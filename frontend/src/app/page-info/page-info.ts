import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

// Pages d'information liées depuis le bas de la landing (mentions légales, protection des
// données, accessibilité). Le titre vient de la route (app.routes.ts) ; le texte définitif
// reste à fournir.
@Component({
  selector: 'app-page-info',
  imports: [RouterLink],
  template: `
    <main class="page-info">
      <a routerLink="/">← Retour au site</a>
      <h1>{{ titre }}</h1>
      <p>Le contenu de cette page est en cours de rédaction.</p>
    </main>
  `,
  styles: `
    .page-info { max-width: 760px; margin: 0 auto; padding: 40px 20px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    p { color: #5d6887; line-height: 1.6; }
  `,
})
export class PageInfo {
  protected readonly titre: string = inject(ActivatedRoute).snapshot.data['titre'];
}
