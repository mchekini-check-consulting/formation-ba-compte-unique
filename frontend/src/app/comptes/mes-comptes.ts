import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Compte, ComptesService, libelleStatut } from './comptes';
import { CadreEspace } from '../espace/cadre-espace';
import { SuiviSession } from '../session/suivi-session';

// CU-20 : liste des comptes dont la cliente est titulaire, dans le cadre et le style de l'espace
// (CadreEspace). Sans session, retour à la connexion.
@Component({
  selector: 'app-mes-comptes',
  imports: [CadreEspace, RouterLink],
  template: `
    <app-cadre-espace page="comptes" [identifiant]="identifiant()">
      <section class="block">
        <div class="wrap">
          <div class="eyebrow">Mes comptes</div>
          <h1 class="titre">Mes comptes</h1>
          <p class="lead">Les comptes dont vous êtes titulaire. Choisissez un compte pour voir son solde.</p>
          @if (erreur()) {
            <p role="alert">Vos comptes sont momentanément indisponibles.</p>
          } @else if (comptes(); as liste) {
            @if (liste.length === 0) {
              <div class="noacct">
                <span class="tag t-blue">Pas encore de compte</span>
                <div>
                  <h3>Vous n'avez pas encore de compte.</h3>
                  <p>Ouverture en environ 30 minutes, sans versement initial.</p>
                  <a class="btn m" routerLink="/">Ouvrir mon compte</a>
                </div>
              </div>
            } @else {
              <div class="cards">
                @for (compte of liste; track compte.id) {
                  <a class="card compte" [routerLink]="['/comptes', compte.id]">
                    <div class="art blue"><span></span></div>
                    <div class="body">
                      <span class="tag" [class.t-mint]="compte.statut !== 'CLOTURE'" [class.t-blue]="compte.statut === 'CLOTURE'">
                        {{ libelleStatut(compte.statut) }}
                      </span>
                      <h3>Compte courant</h3>
                      <p>{{ compte.ibanMasque }}</p>
                      <div class="foot"><span class="go">Voir le compte →</span></div>
                    </div>
                  </a>
                }
              </div>
            }
          }
        </div>
      </section>
    </app-cadre-espace>
  `,
})
export class MesComptes {
  protected readonly identifiant = signal('');
  protected readonly comptes = signal<Compte[] | null>(null);
  protected readonly erreur = signal(false);
  protected readonly libelleStatut = libelleStatut;

  constructor() {
    // CU-17 : ouvrir la page est une action de la cliente ; le suivi s'arrête quand elle la quitte.
    const suivi = inject(SuiviSession);
    suivi.ouvrir().subscribe({ next: (session) => this.identifiant.set(session.identifiant), error: () => undefined });
    inject(DestroyRef).onDestroy(() => suivi.arreter());
    inject(ComptesService)
      .mesComptes()
      .subscribe({
        next: (comptes) => this.comptes.set(comptes),
        error: (erreur: HttpErrorResponse) => {
          // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
          if (erreur.status !== 401) {
            this.erreur.set(true);
          }
        },
      });
  }
}
