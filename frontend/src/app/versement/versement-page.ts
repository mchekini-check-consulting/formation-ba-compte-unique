import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CarteCompte } from '../comptes/carte-compte';
import { Compte, ComptesService } from '../comptes/comptes';
import { SuiviSession, formaterRestant } from '../session/suivi-session';
import { RubriqueVersements } from './rubrique-versements';

type Etat = { lu: false } | { lu: true; erreur: boolean; compte: Compte | null };

// CU-11 (US-06) : page « Faire un versement », ouverte par les boutons « Faire un versement » de l'espace
// (haut de page et actions du compte courant). Même menu et même style que l'espace (src/espace.scss) :
// solde du compte courant, IBAN à communiquer à l'émetteur, mouvements et, en test, simulation d'un
// virement entrant (CU-143). Sans session, retour à la connexion.
@Component({
  selector: 'app-versement-page',
  imports: [CarteCompte, RouterLink, RubriqueVersements],
  template: `
    <div class="espace">
      <aside>
        <div class="logo">Hi-Tech<span>Bank</span></div>
        <nav aria-label="Mon espace">
          <a routerLink="/espace"><i></i>Accueil</a>
          <div class="sep">Mes comptes</div>
          <a routerLink="/espace" fragment="compte"><i></i>Compte courant</a>
          <a routerLink="/versement" aria-current="page"><i></i>Faire un versement</a>
          <a routerLink="/comptes"><i></i>Mes comptes</a>
          <a routerLink="/espace" fragment="epargne"><i></i>Épargne et placements</a>
        </nav>
        <a class="cta" routerLink="/espace" fragment="epargne">Souscrire un produit</a>
      </aside>

      <div class="mnav">
        <div class="logo">Hi-Tech<span>Bank</span></div>
        <nav class="row" aria-label="Mon espace (mobile)">
          <a routerLink="/espace">Accueil</a>
          <a routerLink="/versement" aria-current="page">Versement</a>
          <a routerLink="/comptes">Mes comptes</a>
        </nav>
      </div>

      <main>
        <div class="top">
          <div class="hello">
            @if (identifiant()) {
              Bonjour, identifiant <b>{{ identifiant() }}</b>
            }
          </div>
          <div class="right">
            @if (restant(); as temps) {
              <span class="minuteur" title="Repère indicatif : la session se ferme après 10 minutes sans activité">
                Fermeture automatique dans <b>{{ temps }}</b>
              </span>
            }
            <a class="btn o sm" routerLink="/espace">← Mon espace</a>
            <button class="btn p sm deconnexion" type="button" (click)="suivi.deconnecter()">Me déconnecter</button>
          </div>
        </div>

        <section class="block">
          <div class="wrap">
            <div class="eyebrow">Mes comptes · Compte courant</div>
            <h1 class="titre">Faire un versement</h1>
            <p class="lead">
              Alimentez votre compte courant par virement instantané depuis un compte à votre nom : communiquez votre
              IBAN à l’émetteur. Le virement apparaît dans vos mouvements et votre solde est mis à jour.
            </p>
            @if (etat(); as e) {
              @if (!e.lu) {
                <p>Chargement…</p>
              } @else if (e.erreur) {
                <p role="alert">Votre compte est momentanément indisponible. Veuillez réessayer plus tard.</p>
              } @else if (compte(); as compte) {
                <div class="feature versement">
                  <app-carte-compte [compte]="compte" />
                  <app-rubrique-versements [compteId]="'' + compte.id" (credite)="lireCompte()" />
                </div>
              } @else {
                <div class="noacct">
                  <span class="tag t-blue">Pas encore de compte</span>
                  <div>
                    <h3>Ouvrez votre compte courant</h3>
                    <p>Le versement sera possible dès l’ouverture de votre compte, sans versement initial.</p>
                    <a class="btn m" routerLink="/">Ouvrir mon compte</a>
                  </div>
                </div>
              }
            }
          </div>
        </section>
      </main>
    </div>
  `,
  styles: `
    .titre { font-size: 40px; line-height: 1.1; letter-spacing: -.02em; color: #0b2545; margin: 10px 0 12px; }
    .feature.versement { align-items: start; }
    @media (max-width: 980px) {
      .titre { font-size: 32px; }
    }
  `,
})
export class VersementPage {
  protected readonly suivi = inject(SuiviSession);
  private readonly comptes = inject(ComptesService);

  protected readonly identifiant = signal('');
  protected readonly etat = signal<Etat>({ lu: false });
  protected readonly compte = computed(() => {
    const etat = this.etat();
    return etat.lu ? etat.compte : null;
  });
  protected readonly restant = computed(() => {
    const secondes = this.suivi.restant();
    return secondes === null ? null : formaterRestant(secondes);
  });

  constructor() {
    // CU-17 : ouvrir la page est une action de la cliente ; le suivi s'arrête quand elle la quitte.
    inject(DestroyRef).onDestroy(() => this.suivi.arreter());
    this.suivi.ouvrir().subscribe({
      next: (session) => {
        this.identifiant.set(session.identifiant);
        this.lireCompte();
      },
      error: () => undefined,
    });
  }

  /** Compte courant de la cliente (premier compte) ; relu après un virement crédité (nouveau solde). */
  protected lireCompte(): void {
    this.comptes.mesComptes().subscribe({
      next: (comptes) => this.etat.set({ lu: true, erreur: false, compte: comptes[0] ?? null }),
      error: (erreur: HttpErrorResponse) => {
        // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
        if (erreur.status !== 401) {
          this.etat.set({ lu: true, erreur: true, compte: null });
        }
      },
    });
  }
}
