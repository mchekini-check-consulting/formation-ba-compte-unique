import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CarteCompte } from '../comptes/carte-compte';
import { Compte, ComptesService, formaterDate, libelleStatut, soldeAffichable } from '../comptes/comptes';
import { SuiviSession } from '../session/suivi-session';
import { LIBELLES_STATUT, ProfilService } from '../profil/profil';
import { SupportsService, TauxAffiche, tauxFondsEurosAv, tauxFondsEurosPer } from '../supports/supports';
import { BoutonFonctionnalite } from '../fonctionnalites/bouton-fonctionnalite';
import { GestionService, LIBELLE_MODE, LigneContrat, PROFILS } from '../gestion/gestion';
import { pourcentage } from '../livret-a/parametres';
import { DemandeLivretA, LIBELLES_DEMANDE, LivretAService } from '../livret-a/livret-a';
import { CREDITS, EPARGNE, Produit } from './produits';

type EtatComptes = { lu: false } | { lu: true; erreur: boolean; comptes: Compte[] };

// Espace de la cliente connectée, d'après la maquette « Espace client Hi-Tech Bank » : compte
// courant (CU-19), tous les produits ouverts à la souscription, déconnexion et fermeture de la
// session après 10 minutes sans activité (CU-17). Sans session active, retour à la connexion.
// Les parcours pas encore développés sont des boutons « Bientôt » (lot 3, BoutonFonctionnalite). « Faire un virement »
// ouvre la page /versement (CU-11).
@Component({
  selector: 'app-espace',
  imports: [BoutonFonctionnalite, CarteCompte, RouterLink],
  templateUrl: './espace.html',
})
export class Espace {
  private readonly document = inject(DOCUMENT);
  private readonly comptes = inject(ComptesService);
  private readonly profils = inject(ProfilService);
  private readonly supports = inject(SupportsService);
  private readonly gestions = inject(GestionService);
  private readonly livrets = inject(LivretAService);
  private readonly suivi = inject(SuiviSession);
  private readonly etat = signal<EtatComptes>({ lu: false });

  /** CU-24 et PO 28/11 : taux du fonds en euros du PER et de l'assurance vie (paramètres publics), absents tant qu'ils ne sont pas lus. */
  private readonly fondsEuros = signal<{ PER: TauxAffiche | null; AV: TauxAffiche | null }>({ PER: null, AV: null });
  protected readonly epargne = computed<Produit[]>(() => {
    const taux = this.fondsEuros();
    return EPARGNE.map((p) => {
      const f = p.nom === 'PER' ? taux.PER : p.nom === 'Assurance-vie' ? taux.AV : null;
      return f ? { ...p, tauxAvant: f.avant, taux: f.taux, tauxDetail: f.detail, tauxGarantie: f.garantie } : p;
    });
  });
  protected readonly credits: Produit[] = CREDITS;
  /** Lot 4 : contrats PER et assurance vie de la cliente (null tant qu'ils ne sont pas lus). */
  protected readonly contrats = signal<LigneContrat[] | null>(null);
  protected readonly erreurContrats = signal(false);
  /** Lot 4 : demande de Livret A en cours ou aboutie (null sans demande). */
  protected readonly demandeLivretA = signal<DemandeLivretA | null>(null);
  /** CU-10 : profil validé (true), à compléter ou à revalider (false), pas encore lu (null). */
  protected readonly profilValide = signal<boolean | null>(null);
  /** PO, lot 4 : frais des unités de compte des nouvelles souscriptions, lus dans le paramètre. */
  private readonly fraisUcPct = signal<Record<string, number>>({});
  protected readonly identifiant = signal('');
  /** CU-10 : statut du profil client (« À compléter »...), vide tant qu'il n'est pas lu. */
  protected readonly statutProfil = signal('');
  protected readonly lu = computed(() => this.etat().lu);
  protected readonly compte = computed(() => {
    const etat = this.etat();
    return etat.lu && !etat.erreur ? (etat.comptes[0] ?? null) : null;
  });
  protected readonly sansCompte = computed(() => {
    const etat = this.etat();
    return etat.lu && !etat.erreur && etat.comptes.length === 0;
  });
  /** Solde affichable, null s'il est indisponible (erreur de lecture ou solde refusé). */
  protected readonly solde = computed(() => soldeAffichable(this.compte()));
  protected readonly miseAJour = computed(() => {
    const date = this.compte()?.soldeMisAJourLe;
    return date ? formaterDate(date) : null;
  });
  protected readonly statut = computed(() => {
    const compte = this.compte();
    return compte && this.solde() ? libelleStatut(compte.statut) : null;
  });
  protected readonly cloture = computed(() => this.compte()?.statut === 'CLOTURE');

  constructor() {
    inject(DestroyRef).onDestroy(() => this.suivi.arreter());
    this.suivi.ouvrir().subscribe({
      next: (session) => {
        this.identifiant.set(session.identifiant);
        setTimeout(() => this.defiler());
        this.lireComptes();
        this.profils.lire().subscribe({
          next: (etat) => {
            this.statutProfil.set(LIBELLES_STATUT[etat.statut]);
            this.profilValide.set(etat.statut === 'VALIDE');
          },
          error: () => undefined,
        });
        this.supports.parametresPublics().subscribe({
          next: (p) => {
            this.fondsEuros.set({ PER: tauxFondsEurosPer(p), AV: tauxFondsEurosAv(p) });
            this.fraisUcPct.set(p.fraisUcPct ?? {});
          },
          error: () => undefined,
        });
        this.gestions.contrats().subscribe({ next: (c) => this.contrats.set(c), error: () => this.erreurContrats.set(true) });
        this.livrets.etat().subscribe({ next: (e) => this.demandeLivretA.set(e.demande), error: () => undefined });
      },
      error: () => undefined,
    });
    // Lot 2 : les entrées du menu unique visent une rubrique de l'accueil (/espace#credits...), aussi depuis une autre page.
    inject(ActivatedRoute).fragment.subscribe((fragment) => {
      this.fragment = fragment;
      this.defiler();
    });
  }

  private fragment: string | null = null;

  protected placement(produit: Produit): boolean {
    return produit.nom === 'PER' || produit.nom === 'Assurance-vie';
  }

  /** Frais des unités de compte de la carte PER ou assurance vie (paramètre) ; rien pour les autres produits ou sans paramètre. */
  protected fraisUc(produit: Produit): string | null {
    const cle = produit.nom === 'PER' ? 'PER' : produit.nom === 'Assurance-vie' ? 'AV' : null;
    const frais = cle ? this.fraisUcPct()[cle] : undefined;
    return frais == null ? null : pourcentage(frais);
  }

  protected libelleProduit(produit: string): string {
    return produit === 'PER' ? 'PER' : 'Assurance vie';
  }

  protected libelleDemande(demande: DemandeLivretA): string {
    return LIBELLES_DEMANDE[demande.statut];
  }

  protected gestion(c: LigneContrat): string {
    return `${LIBELLE_MODE[c.mode]} · profil ${PROFILS[c.profil]}`;
  }

  /** Date d'ouverture du contrat, à Paris : « 28/11/2026 ». */
  protected jour(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { timeZone: 'Europe/Paris' });
  }

  /** Rubrique demandée par le menu : affichée dès que la page est prête. */
  private defiler(): void {
    const cible = this.fragment ? this.document.getElementById(this.fragment) : null;
    cible?.scrollIntoView?.({ behavior: 'smooth' });
  }

  /** Liens « #rubrique » du menu : défilement dans la page, sans quitter l'espace. */
  protected suivreAncre(event: MouseEvent): void {
    const lien = (event.target as Element | null)?.closest?.('a[href^="#"]');
    const cible = lien ? this.document.getElementById(lien.getAttribute('href')!.slice(1)) : null;
    if (cible) {
      event.preventDefault();
      cible.scrollIntoView?.({ behavior: 'smooth' });
    }
  }


  private lireComptes(): void {
    this.comptes.mesComptes().subscribe({
      next: (comptes) => this.etat.set({ lu: true, erreur: false, comptes }),
      error: (erreur: HttpErrorResponse) => {
        // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
        if (erreur.status !== 401) {
          this.etat.set({ lu: true, erreur: true, comptes: [] });
        }
      },
    });
  }
}
