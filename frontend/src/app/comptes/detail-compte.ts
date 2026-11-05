import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CarteCompte } from './carte-compte';
import { Compte, ComptesService } from './comptes';
import { SuiviSession } from '../session/suivi-session';

// CU-20 : un compte de la cliente. Tout refus (autre cliente, compte inexistant, panne) affiche
// le même message, sans aucune information sur le compte. Sans session, retour à la connexion.
@Component({
  selector: 'app-detail-compte',
  imports: [CarteCompte, RouterLink],
  template: `
    <main class="compte">
      <a routerLink="/comptes">← Mes comptes</a>
      @if (inaccessible()) {
        <p role="alert">Ce compte n’est pas accessible</p>
      } @else if (compte(); as detail) {
        <h1>Mon compte</h1>
        <app-carte-compte [compte]="detail" />
        <p>IBAN : {{ detail.ibanMasque }}</p>
      }
    </main>
  `,
  styles: `
    .compte { max-width: 760px; margin: 0 auto; padding: 40px 20px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    p { color: #5d6887; line-height: 1.6; }
  `,
})
export class DetailCompte {
  private readonly router = inject(Router);
  protected readonly compte = signal<Compte | null>(null);
  protected readonly inaccessible = signal(false);

  constructor() {
    // CU-17 : ouvrir la page est une action de la cliente ; le suivi s'arrête quand elle la quitte.
    const suivi = inject(SuiviSession);
    suivi.ouvrir().subscribe({ error: () => undefined });
    inject(DestroyRef).onDestroy(() => suivi.arreter());
    const id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
    inject(ComptesService)
      .consulter(id)
      .subscribe({
        next: (compte) => this.compte.set(compte),
        error: (erreur: HttpErrorResponse) => {
          if (erreur.status === 401) {
            void this.router.navigateByUrl('/connexion');
          } else {
            this.inaccessible.set(true);
          }
        },
      });
  }
}
