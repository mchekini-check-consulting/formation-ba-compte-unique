import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CarteCompte } from './carte-compte';
import { Compte, ComptesService } from './comptes';
import { CadreEspace } from '../espace/cadre-espace';
import { SuiviSession } from '../session/suivi-session';

// CU-20 : un compte de la cliente, dans le cadre et le style de l'espace (CadreEspace). Tout refus
// (autre cliente, compte inexistant, panne) affiche le même message, sans aucune information sur le
// compte. Sans session, retour à la connexion.
@Component({
  selector: 'app-detail-compte',
  imports: [CadreEspace, CarteCompte, RouterLink],
  template: `
    <app-cadre-espace page="comptes" [identifiant]="identifiant()">
      <section class="block">
        <div class="wrap">
          <a class="go" routerLink="/comptes">← Mes comptes</a>
          @if (inaccessible()) {
            <p class="lead refus" role="alert">Ce compte n’est pas accessible</p>
          } @else if (compte(); as detail) {
            <div class="eyebrow">Mes comptes · Compte courant</div>
            <h1 class="titre">Mon compte</h1>
            <div class="feature">
              <app-carte-compte [compte]="detail" />
              <div>
                <h3>IBAN</h3>
                <p class="lead">{{ detail.ibanMasque }}</p>
              </div>
            </div>
          }
        </div>
      </section>
    </app-cadre-espace>
  `,
  styles: `
    .refus { margin-top: 24px; }
  `,
})
export class DetailCompte {
  protected readonly identifiant = signal('');
  protected readonly compte = signal<Compte | null>(null);
  protected readonly inaccessible = signal(false);

  constructor() {
    // CU-17 : ouvrir la page est une action de la cliente ; le suivi s'arrête quand elle la quitte.
    const suivi = inject(SuiviSession);
    suivi.ouvrir().subscribe({ next: (session) => this.identifiant.set(session.identifiant), error: () => undefined });
    inject(DestroyRef).onDestroy(() => suivi.arreter());
    const id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
    inject(ComptesService)
      .consulter(id)
      .subscribe({
        next: (compte) => this.compte.set(compte),
        error: (erreur: HttpErrorResponse) => {
          // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
          if (erreur.status !== 401) {
            this.inaccessible.set(true);
          }
        },
      });
  }
}
