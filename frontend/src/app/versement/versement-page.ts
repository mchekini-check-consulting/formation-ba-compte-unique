import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CarteCompte } from '../comptes/carte-compte';
import { Compte, ComptesService } from '../comptes/comptes';
import { CadreEspace } from '../espace/cadre-espace';
import { SuiviSession } from '../session/suivi-session';
import { FormulaireVirement } from './formulaire-virement';
import { RubriqueVersements } from './rubrique-versements';

type Etat = { lu: false } | { lu: true; erreur: boolean; compte: Compte | null };

// CU-11 (US-06) : page « Faire un virement », ouverte par les boutons « Faire un virement » de l'espace
// (haut de page et actions du compte courant). Même menu et même style que l'espace (CadreEspace) :
// solde du compte courant, IBAN à communiquer à l'émetteur, mouvements et, en test, simulation d'un
// virement entrant (CU-143) ou sortant (CU-144). Sans session, retour à la connexion.
// CU-147 : virement vers un de mes bénéficiaires (FormulaireVirement) ; une fois exécuté, le solde et les
// mouvements sont relus.
@Component({
  selector: 'app-versement-page',
  imports: [CadreEspace, CarteCompte, FormulaireVirement, RouterLink, RubriqueVersements],
  template: `
    <app-cadre-espace page="virement" [identifiant]="identifiant()">
      <section class="block">
        <div class="wrap">
          <div class="eyebrow">Mes comptes · Compte courant</div>
          <h1 class="titre">Faire un virement</h1>
          <p class="lead">
            Envoyez de l’argent à un de vos <a routerLink="/beneficiaires">bénéficiaires</a>, confirmé par un code reçu
            par SMS. Pour recevoir un virement, communiquez votre IBAN à l’émetteur. Les virements reçus et émis
            apparaissent dans vos mouvements et votre solde est mis à jour.
          </p>
          @if (etat(); as e) {
            @if (!e.lu) {
              <p>Chargement…</p>
            } @else if (e.erreur) {
              <p role="alert">Votre compte est momentanément indisponible. Veuillez réessayer plus tard.</p>
            } @else if (compte(); as compte) {
              <div class="feature versement">
                <app-carte-compte [compte]="compte" />
                <div class="colonne">
                  <app-formulaire-virement (execute)="apresVirement()" />
                  <app-rubrique-versements
                    [compteId]="'' + compte.id"
                    [actualisation]="actualisation()"
                    (credite)="lireCompte()"
                  />
                </div>
              </div>
            } @else {
              <div class="noacct">
                <span class="tag t-blue">Pas encore de compte</span>
                <div>
                  <h3>Ouvrez votre compte courant</h3>
                  <p>Le virement sera possible dès l’ouverture de votre compte, sans versement initial.</p>
                  <a class="btn m" routerLink="/">Ouvrir mon compte</a>
                </div>
              </div>
            }
          }
        </div>
      </section>
    </app-cadre-espace>
  `,
  styles: `
    .feature.versement { align-items: start; }
    .colonne { min-width: 0; }
  `,
})
export class VersementPage {
  private readonly suivi = inject(SuiviSession);
  private readonly comptes = inject(ComptesService);

  protected readonly identifiant = signal('');
  protected readonly etat = signal<Etat>({ lu: false });
  /** Augmenté après un virement exécuté : la rubrique relit les mouvements. */
  protected readonly actualisation = signal(0);
  protected readonly compte = computed(() => {
    const etat = this.etat();
    return etat.lu ? etat.compte : null;
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

  /** CU-147 : virement exécuté -> nouveau solde et nouveaux mouvements. */
  protected apresVirement(): void {
    this.lireCompte();
    this.actualisation.update((n) => n + 1);
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
