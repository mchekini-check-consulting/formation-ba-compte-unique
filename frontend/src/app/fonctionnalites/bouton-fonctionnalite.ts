import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CleFonctionnalite, EtatsFonctionnalites, FONCTIONNALITES, Fonctionnalite, FonctionnalitesService } from './fonctionnalites';

let suivant = 0;

// Lot 3 : bouton unique de fonctionnalité, deux états seulement.
// - Disponible (interrupteur à true et page développée) : lien vers la page.
// - Bientôt : le bouton reste à sa place avec son libellé, désactivé (aria-disabled), badge « Bientôt » et phrase
//   courte ; un clic ne fait rien et un lecteur d'écran annonce « Bientôt disponible ».
@Component({
  selector: 'app-bouton-fonctionnalite',
  imports: [NgTemplateOutlet, RouterLink],
  template: `
    <ng-template #contenu><ng-content /></ng-template>
    @if (lien(); as page) {
      <a [class]="classe()" [routerLink]="page" [attr.data-fonctionnalite]="cle()" data-etat="disponible"><ng-container *ngTemplateOutlet="contenu" /></a>
    } @else {
      <button type="button" [class]="classe() + ' bientot'" aria-disabled="true" [attr.aria-describedby]="idPhrase"
              [title]="fonctionnalite().phrase" [attr.data-fonctionnalite]="cle()" data-etat="bientot">
        <ng-container *ngTemplateOutlet="contenu" />
        <span class="badge-bientot"><span class="visuellement-cache">, </span>Bientôt<span class="visuellement-cache"> disponible</span></span>
      </button>
      <span [id]="idPhrase" [class]="phraseVisible() ? 'phrase-bientot' : 'visuellement-cache'">{{ fonctionnalite().phrase }}</span>
    }
  `,
  styles: `
    :host { display: inline-flex; flex-direction: column; align-items: flex-start; gap: 4px; }
    .tile { width: 100%; height: 100%; }
  `,
})
export class BoutonFonctionnalite {
  readonly cle = input.required<CleFonctionnalite>();
  /** Classes du bouton dans son écran (btn p sm, tile, go...), identiques dans les deux états. */
  readonly classe = input('');
  /** Phrase « Bientôt » visible sous le bouton ; sinon infobulle et lecteur d'écran seulement (en-tête). */
  readonly phraseVisible = input(true);

  private readonly etats = toSignal(inject(FonctionnalitesService).etats(), { initialValue: {} as EtatsFonctionnalites });
  protected readonly idPhrase = `bientot-${++suivant}`;
  protected readonly fonctionnalite = computed<Fonctionnalite>(() => FONCTIONNALITES[this.cle()]);
  /** Page à ouvrir si la fonctionnalité est livrée dans cet environnement ; sinon « Bientôt ». */
  protected readonly lien = computed(() => (this.etats()[this.cle()] ? this.fonctionnalite().lien : undefined));
}
