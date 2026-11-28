import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { BoutonFonctionnalite } from '../fonctionnalites/bouton-fonctionnalite';
import { eurosAuCentime } from '../livret-a/parametres';
import { PROFILS } from '../gestion/gestion';
import { EtatSouscription, ORIGINES, Produit, SouscriptionService } from '../souscription/souscription';

/**
 * Lot 5 (PO, 27/11/2026) : étapes du tunnel de souscription, dans l'ordre des stories (D5). PO 28/11 : l'ordre se
 * change ici seulement (SOU-02, CU-528, placera plus tard « Ma gestion » avant la signature) ; les écrans désignent
 * leur étape par sa clé, le numéro et la page suivante se déduisent de cet ordre.
 */
export const ETAPES = [
  { cle: 'profil', libelle: 'Mon profil', lien: '/profil' },
  { cle: 'documents', libelle: 'Mes documents', lien: '/decouvrir' },
  { cle: 'versement', libelle: 'Mon versement', lien: '/souscrire' },
  { cle: 'signature', libelle: 'Récapitulatif et signature', lien: '/souscrire' },
  { cle: 'confirmation', libelle: 'Confirmation', lien: '/souscrire' },
  { cle: 'gestion', libelle: 'Ma gestion et mes supports (facultatif)', lien: '/gestion' },
] as const;

export type CleEtape = (typeof ETAPES)[number]['cle'];

/** Page de l'étape qui suit, dans l'ordre du tunnel (null après la dernière). */
export function pageSuivante(cle: CleEtape): string | null {
  const i = ETAPES.findIndex((e) => e.cle === cle);
  return ETAPES[i + 1]?.lien ?? null;
}

const PROFIL: Record<EtatSouscription['profil'], string> = { VALIDE: 'Validé', A_COMPLETER: 'À compléter', A_REVALIDER: 'À revalider' };

// Lot 5 : cadre commun des étapes du tunnel (PER et assurance vie) : indicateur « Étape X sur N » toujours visible,
// colonne « Ma souscription » qui résume les choix déjà faits, lien « Contacter mon conseiller » (« Bientôt » tant
// qu'il n'est pas livré). Le contenu de l'étape est projeté ; inactif (actif = false), seul le contenu s'affiche.
@Component({
  selector: 'app-tunnel',
  imports: [BoutonFonctionnalite, NgTemplateOutlet],
  template: `
    <ng-template #contenu><ng-content /></ng-template>
    @if (actif()) {
      <div class="tunnel">
        <div class="tunnel-principal">
          <nav class="tunnel-etapes" aria-label="Étapes de la souscription">
            <p class="etape-courante" data-test="etape">Étape {{ numero() }} sur {{ etapes.length }} : {{ etapes[numero() - 1].libelle }}</p>
            <ol>
              @for (e of etapes; track e.cle; let i = $index) {
                <li [class.faite]="i + 1 < numero()" [attr.aria-current]="i + 1 === numero() ? 'step' : null">{{ e.libelle }}</li>
              }
            </ol>
          </nav>
          <ng-container *ngTemplateOutlet="contenu" />
          <p class="tunnel-conseiller">
            <app-bouton-fonctionnalite cle="contact-conseiller" classe="lien">Contacter mon conseiller</app-bouton-fonctionnalite>
          </p>
        </div>
        <aside class="ma-souscription" aria-labelledby="titre-ma-souscription" data-test="ma-souscription">
          <h2 id="titre-ma-souscription">Ma souscription</h2>
          <dl>
            <div><dt>Produit</dt><dd>{{ produit() === 'PER' ? 'Plan d’épargne retraite' : 'Assurance vie' }}</dd></div>
            @if (resume(); as r) {
              <div><dt>Mon profil</dt><dd>{{ r.profil }}</dd></div>
              <div><dt>Mes documents</dt><dd>{{ r.documents }}</dd></div>
              @if (r.versement) {
                <div><dt>Mon versement</dt><dd>{{ r.versement }}</dd></div>
              }
              @if (r.beneficiaires) {
                <div><dt>Bénéficiaires</dt><dd>{{ r.beneficiaires }}</dd></div>
              }
              @if (r.gestion) {
                <div><dt>Gestion au démarrage</dt><dd>{{ r.gestion }}</dd></div>
              }
              @if (r.contrat) {
                <div><dt>Contrat</dt><dd>{{ r.contrat }}</dd></div>
              }
            }
          </dl>
        </aside>
      </div>
    } @else {
      <ng-container *ngTemplateOutlet="contenu" />
    }
  `,
})
export class Tunnel {
  private readonly service = inject(SouscriptionService);

  readonly produit = input.required<Produit>();
  readonly etape = input.required<CleEtape>();
  /** Hors tunnel (page ouverte depuis le menu), le contenu s'affiche seul. */
  readonly actif = input(true);
  /** État déjà lu par la page (souscription) ; sinon le tunnel le lit lui-même. */
  readonly etat = input<EtatSouscription | null>(null);
  /** false : la page fournit l'état (souscription) ; le tunnel ne le lit pas une seconde fois. */
  readonly autonome = input(true);

  protected readonly etapes = ETAPES;
  protected readonly numero = computed(() => ETAPES.findIndex((e) => e.cle === this.etape()) + 1);
  private readonly lu = signal<EtatSouscription | null>(null);

  constructor() {
    effect(() => {
      if (this.actif() && this.autonome() && !this.etat()) {
        const produit = this.produit();
        untracked(() => this.service.etat(produit).subscribe({ next: (e) => this.lu.set(e), error: () => undefined }));
      }
    });
  }

  protected readonly resume = computed(() => {
    const e = this.etat() ?? this.lu();
    if (!e) {
      return null;
    }
    const origine = ORIGINES.find((o) => o.code === e.origineFonds)?.libelle.replace(' (à préciser)', '');
    return {
      profil: PROFIL[e.profil],
      documents: e.documentsLus ? 'Lus' : 'À lire',
      versement: e.montant == null ? null : `${eurosAuCentime(e.montant)}${origine ? ` · ${origine}` : ''}`,
      beneficiaires:
        e.produit !== 'AV' || e.montant == null ? null : e.beneficiaires.length === 0 ? 'Aucun (succession)' : `${e.beneficiaires.length} bénéficiaire(s)`,
      gestion: e.profilGestionParDefaut ? `Pilotée, profil ${PROFILS[e.profilGestionParDefaut]}` : null,
      contrat: e.numeroContrat,
    };
  });
}
