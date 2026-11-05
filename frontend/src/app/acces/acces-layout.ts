import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

// Mise en page commune aux écrans d'accès à l'espace (maquette « Connexion Hi-Tech Bank ») :
// en-tête, présentation à gauche, carte du formulaire à droite (contenu projeté). Styles dans
// src/acces.scss.
@Component({
  selector: 'app-acces-layout',
  imports: [RouterLink],
  host: { class: 'acces' },
  template: `
    <header>
      <a class="logo" routerLink="/">Hi-Tech<span>Bank</span></a>
      <a class="help" [routerLink]="retour()">{{ libelleRetour() }}</a>
    </header>
    <main>
      <section class="intro">
        <div class="eyebrow">Mon espace</div>
        <h1>{{ titre() }}</h1>
        <p>{{ texte() }}</p>
        <ul class="points">
          @for (point of points(); track point[0]) {
            <li><span class="check" aria-hidden="true">✓</span><span><b>{{ point[0] }}</b> : {{ point[1] }}</span></li>
          }
        </ul>
      </section>
      <section class="card">
        <ng-content />
      </section>
    </main>
  `,
})
export class AccesLayout {
  readonly titre = input.required<string>();
  readonly texte = input.required<string>();
  /** Arguments de la colonne de gauche : [titre en gras, explication]. */
  readonly points = input<[string, string][]>([]);
  readonly retour = input('/');
  readonly libelleRetour = input('← Retour au site');
}
