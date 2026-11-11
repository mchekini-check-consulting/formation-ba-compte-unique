import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CarteCompte } from '../comptes/carte-compte';
import { Compte, ComptesService, formaterDate, libelleStatut, soldeAffichable } from '../comptes/comptes';
import { SuiviSession, formaterRestant } from '../session/suivi-session';
import { CREDITS, EPARGNE, Produit } from './produits';

type EtatComptes = { lu: false } | { lu: true; erreur: boolean; comptes: Compte[] };

const DUREE_ANNONCE_MS = 3200;

// Espace de la cliente connectée, d'après la maquette « Espace client Hi-Tech Bank » : compte
// courant (CU-19), tous les produits ouverts à la souscription, déconnexion et fermeture de la
// session après 10 minutes sans activité (CU-17). Sans session active, retour à la connexion.
// Les parcours pas encore développés (souscription...) l'annoncent simplement. « Faire un virement »
// ouvre la page /versement (CU-11).
@Component({
  selector: 'app-espace',
  imports: [CarteCompte, RouterLink],
  templateUrl: './espace.html',
})
export class Espace {
  private readonly document = inject(DOCUMENT);
  private readonly comptes = inject(ComptesService);
  private readonly suivi = inject(SuiviSession);
  private readonly etat = signal<EtatComptes>({ lu: false });
  private minuteurAnnonce: ReturnType<typeof setTimeout> | null = null;

  protected readonly epargne: Produit[] = EPARGNE;
  protected readonly credits: Produit[] = CREDITS;
  protected readonly identifiant = signal('');
  protected readonly annonce = signal('');
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
  protected readonly restant = computed(() => {
    const secondes = this.suivi.restant();
    return secondes === null ? null : formaterRestant(secondes);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.suivi.arreter();
      if (this.minuteurAnnonce) clearTimeout(this.minuteurAnnonce);
    });
    this.suivi.ouvrir().subscribe({
      next: (session) => {
        this.identifiant.set(session.identifiant);
        this.lireComptes();
      },
      error: () => undefined,
    });
  }

  protected deconnecter(): void {
    this.suivi.deconnecter();
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

  /** Parcours pas encore développé : on l'annonce, sans rien faire d'autre. */
  protected annoncer(texte: string): void {
    this.annonce.set(texte);
    if (this.minuteurAnnonce) clearTimeout(this.minuteurAnnonce);
    this.minuteurAnnonce = setTimeout(() => this.annonce.set(''), DUREE_ANNONCE_MS);
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
