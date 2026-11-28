import { Component, computed, input, signal } from '@angular/core';

/**
 * Lot 7 (PO, 28/11/2026) : une phrase par terme technique, au même endroit pour toute l'application. Textes
 * pédagogiques proposés pour validation du PO ; les mentions réglementaires, elles, sont des paramètres (app-mention).
 */
export const GLOSSAIRE = {
  'unite-de-compte': {
    terme: 'Unité de compte',
    phrase: 'Une unité de compte est un support d’investissement (actions, obligations, immobilier…) dont la valeur peut monter comme baisser.',
  },
  'valeur-liquidative': {
    terme: 'Valeur liquidative',
    phrase: 'La valeur liquidative est le prix d’une part d’un support, calculé à une date donnée.',
  },
  'niveau-risque': {
    terme: 'Niveau de risque',
    phrase: 'Le niveau de risque va de 1 (le plus faible) à 7 (le plus élevé) : plus il est haut, plus la valeur du support peut varier.',
  },
  'non-cote': {
    terme: 'Non coté',
    phrase: 'Un support non coté investit dans des actifs qui ne s’échangent pas en Bourse, comme l’immobilier ou des entreprises non cotées.',
  },
  'fonds-euros': {
    terme: 'Fonds en euros',
    phrase: 'Le fonds en euros est la partie du contrat dont le capital est garanti, avec des intérêts crédités une fois par an.',
  },
  'gestion-pilotee': {
    terme: 'Gestion pilotée',
    phrase: 'En gestion pilotée, la banque répartit votre épargne entre les supports selon votre profil de gestion.',
  },
  'gestion-libre': {
    terme: 'Gestion libre',
    phrase: 'En gestion libre, vous choisissez vous-même les supports sur lesquels votre épargne est investie.',
  },
  'plafond-retraite': {
    terme: 'Plafond épargne retraite',
    phrase: 'Le plafond épargne retraite est le montant de versements sur un PER que vous pouvez déduire de vos revenus, indiqué sur votre avis d’imposition.',
  },
} as const;

export type Terme = keyof typeof GLOSSAIRE;

let numero = 0;

// Lot 7 : info-bulle d'un terme technique : bouton « ? » à côté du terme, phrase affichée au survol, au focus clavier
// ou au clic, refermée par Échap ; la phrase est reliée au bouton (aria-describedby) pour les lecteurs d'écran.
@Component({
  selector: 'app-info',
  template: `
    <span class="info-bulle" (mouseenter)="ouverte.set(true)" (mouseleave)="ouverte.set(false)">
      <button type="button" class="info-bouton" [attr.aria-label]="'Qu’est-ce que : ' + entree().terme + ' ?'"
        [attr.aria-describedby]="id" [attr.aria-expanded]="ouverte()" data-test="info"
        (click)="basculer($event)" (focus)="ouverte.set(true)" (blur)="ouverte.set(false)"
        (keydown.escape)="ouverte.set(false)">?</button>
      <span class="info-texte" role="tooltip" [id]="id" [class.visible]="ouverte()">{{ entree().phrase }}</span>
    </span>
  `,
  styles: `
    .info-bulle { position: relative; display: inline-block; vertical-align: middle; margin-left: 6px; font-weight: 400; text-transform: none; letter-spacing: 0; }
    .info-bouton { width: 20px; height: 20px; border-radius: 50%; border: 1px solid #8a97ad; background: #fff; color: #0b2545; font: 700 12px/1 system-ui, sans-serif; cursor: help; padding: 0; }
    .info-bouton:focus-visible { outline: 3px solid #00b894; outline-offset: 2px; }
    .info-texte { position: absolute; z-index: 30; left: 50%; top: calc(100% + 8px); transform: translateX(-50%); width: max-content; max-width: 280px; padding: 10px 12px; border-radius: 10px; background: #0b2545; color: #fff; font-size: 13px; line-height: 1.45; text-align: left; box-shadow: 0 8px 24px rgba(11, 37, 69, .25); visibility: hidden; opacity: 0; transition: opacity .12s; }
    .info-texte.visible { visibility: visible; opacity: 1; }
  `,
})
export class InfoBulle {
  readonly terme = input.required<Terme>();
  protected readonly entree = computed(() => GLOSSAIRE[this.terme()]);
  protected readonly ouverte = signal(false);
  protected readonly id = `info-${++numero}`;

  /** Le clic affiche la phrase (Échap ou la sortie du bouton la referme) ; dans une étiquette, il ne coche pas l’option. */
  protected basculer(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.ouverte.set(true);
  }
}
