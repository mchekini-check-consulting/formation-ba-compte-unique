import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CarteCompte } from '../comptes/carte-compte';
import { Compte, ComptesService, formaterDate, libelleStatut, soldeAffichable } from '../comptes/comptes';
import { SuiviSession } from '../session/suivi-session';
import { LIBELLES_STATUT, ProfilService } from '../profil/profil';
import { SupportsService, TauxAffiche, tauxFondsEurosAv } from '../supports/supports';
import { BoutonFonctionnalite } from '../fonctionnalites/bouton-fonctionnalite';
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
  private readonly suivi = inject(SuiviSession);
  private readonly etat = signal<EtatComptes>({ lu: false });

  /** CU-24 : taux du fonds en euros de l'assurance vie (paramètres publics) ; absent tant qu'il n'est pas lu. */
  private readonly fondsEurosAv = signal<TauxAffiche | null>(null);
  protected readonly epargne = computed<Produit[]>(() => {
    const f = this.fondsEurosAv();
    return EPARGNE.map((p) => (p.nom === 'Assurance-vie' && f ? { ...p, tauxAvant: f.avant, taux: f.taux, tauxDetail: f.detail } : p));
  });
  protected readonly credits: Produit[] = CREDITS;
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
        this.profils.lire().subscribe({ next: (etat) => this.statutProfil.set(LIBELLES_STATUT[etat.statut]), error: () => undefined });
        this.supports.parametresPublics().subscribe({ next: (p) => this.fondsEurosAv.set(tauxFondsEurosAv(p)), error: () => undefined });
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
