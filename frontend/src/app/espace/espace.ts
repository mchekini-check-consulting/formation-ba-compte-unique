import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ConnexionService } from '../connexion';

// Espace de la cliente connectée (CU-15) : sans session valide, retour à la connexion.
@Component({
  selector: 'app-espace',
  template: `
    @if (identifiant()) {
      <main class="espace">
        <h1>Bienvenue dans votre espace</h1>
        <p>Vous êtes connectée avec l'identifiant {{ identifiant() }}.</p>
      </main>
    }
  `,
  styles: `
    .espace { max-width: 760px; margin: 0 auto; padding: 40px 20px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    p { color: #5d6887; line-height: 1.6; }
  `,
})
export class Espace {
  private readonly router = inject(Router);
  protected readonly identifiant = signal('');

  constructor() {
    inject(ConnexionService)
      .session()
      .subscribe({
        next: (session) => this.identifiant.set(session.identifiant),
        error: () => void this.router.navigateByUrl('/connexion'),
      });
  }
}
