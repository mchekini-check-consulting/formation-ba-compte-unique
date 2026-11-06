import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Compte, ComptesService, libelleStatut } from './comptes';
import { SuiviSession } from '../session/suivi-session';

// CU-20 : liste des comptes dont la cliente est titulaire. Sans session, retour à la connexion.
@Component({
  selector: 'app-mes-comptes',
  imports: [RouterLink],
  template: `
    <main class="comptes">
      <a routerLink="/espace">← Mon espace</a>
      <h1>Mes comptes</h1>
      @if (erreur()) {
        <p role="alert">Vos comptes sont momentanément indisponibles.</p>
      } @else if (comptes(); as liste) {
        @for (compte of liste; track compte.id) {
          <a class="compte" [routerLink]="['/comptes', compte.id]">
            <strong>Compte courant</strong>
            <span>{{ compte.ibanMasque }}</span>
            <span>{{ libelleStatut(compte.statut) }}</span>
          </a>
        } @empty {
          <p>Vous n'avez pas encore de compte.</p>
          <a routerLink="/">Ouvrir mon compte</a>
        }
      }
    </main>
  `,
  styles: `
    .comptes { max-width: 760px; margin: 0 auto; padding: 40px 20px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    p { color: #5d6887; line-height: 1.6; }
    .compte { display: flex; flex-wrap: wrap; gap: 8px 24px; justify-content: space-between; padding: 16px; border: 1px solid #d8deea; border-radius: 8px; color: inherit; text-decoration: none; }
  `,
})
export class MesComptes {
  protected readonly comptes = signal<Compte[] | null>(null);
  protected readonly erreur = signal(false);
  protected readonly libelleStatut = libelleStatut;

  constructor() {
    // CU-17 : ouvrir la page est une action de la cliente ; le suivi s'arrête quand elle la quitte.
    const suivi = inject(SuiviSession);
    suivi.ouvrir().subscribe({ error: () => undefined });
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
