import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { BoutonFonctionnalite } from '../fonctionnalites/bouton-fonctionnalite';
import { PointVl, SupportEligible, SupportsService } from './supports';

type Colonne = 'nom' | 'categorie' | 'niveauRisque' | 'valeurLiquidative' | 'fraisSupportPct' | 'total';
type Vue = 'tableau' | 'cartes';

/** Explication de l'échelle de risque, au survol et pour les lecteurs d'écran. */
export const EXPLICATION_RISQUE = 'Niveau de risque de 1 (le plus faible) à 7 (le plus élevé).';

const nombre = (valeur: number, decimales: number) =>
  valeur.toLocaleString('fr-FR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }).replace(/ | /g, ' ');

// Lot 6 : composant unique de liste et de fiche des unités de compte (page « Supports disponibles », puis l'étape
// « Mes supports » et la liste « Vers » des arbitrages quand elles seront développées). Il reçoit la liste déjà
// filtrée par le service de CAT-01B (profil, produit, supports actifs) et ne filtre rien lui-même au-delà de ce que la
// cliente choisit à l'écran (recherche, catégorie ou famille, niveau de risque). Tri par colonne, tableau ou cartes,
// fiche dans un panneau latéral avec la courbe de la valeur liquidative (catalogue.historique_vl, lecture seule).
@Component({
  selector: 'app-liste-supports',
  imports: [BoutonFonctionnalite, NgTemplateOutlet],
  template: `
    <div class="liste-supports">
      <aside class="ls-filtres" aria-label="Filtres">
        <label>Rechercher
          <input id="recherche" type="search" placeholder="Nom ou code ISIN" [value]="recherche()" (input)="recherche.set(valeur($event))" />
        </label>
        <label>{{ familles() ? 'Famille' : 'Catégorie' }}
          <select id="categorie" name="categorie" (change)="categorie.set(valeur($event))">
            <option value="">Toutes</option>
            @for (c of categories(); track c) {
              <option [value]="c">{{ c }}</option>
            }
          </select>
        </label>
        <label>Niveau de risque
          <select id="risque" name="risque" (change)="risque.set(valeur($event))">
            <option value="">Tous</option>
            @for (n of niveaux(); track n) {
              <option [value]="n">{{ n }} sur 7</option>
            }
          </select>
        </label>
        <p class="ls-compte" role="status">{{ affiches().length }} support(s)</p>
      </aside>

      <div class="ls-contenu">
        <div class="ls-vues" role="group" aria-label="Affichage">
          <button type="button" data-test="vue-tableau" [attr.aria-pressed]="vue() === 'tableau'" (click)="vue.set('tableau')">Tableau</button>
          <button type="button" data-test="vue-cartes" [attr.aria-pressed]="vue() === 'cartes'" (click)="vue.set('cartes')">Cartes</button>
        </div>

        @if (supports().length === 0) {
          <p class="vide">Aucun support disponible pour votre profil.</p>
        } @else if (affiches().length === 0) {
          <p class="vide">Aucun support ne correspond à ces filtres.</p>
        } @else if (vue() === 'tableau') {
          <div class="ls-tableau">
            <table>
              <thead>
                <tr>
                  @for (c of colonnes; track c.cle) {
                    <th scope="col" [attr.aria-sort]="tri() === c.cle ? (croissant() ? 'ascending' : 'descending') : null">
                      <button type="button" class="ls-tri" (click)="trier(c.cle)">{{ c.libelle }}</button>
                    </th>
                  }
                  <th scope="col">Frais du contrat</th>
                  <th scope="col">Label</th>
                </tr>
              </thead>
              <tbody>
                @for (s of affiches(); track s.codeIsin) {
                  <tr data-test="support">
                    <td>
                      <button type="button" class="ls-nom" data-test="nom" (click)="ouvrir(s)">{{ s.nom }}</button>
                      <span class="ls-isin">{{ s.codeIsin }}</span>
                      @if (s.nonCote) {
                        <span class="tag non-cote">Non coté</span>
                      }
                    </td>
                    <td>{{ famille(s) }}</td>
                    <td><ng-container *ngTemplateOutlet="echelle; context: { $implicit: s.niveauRisque }" /></td>
                    <td>{{ euros(s.valeurLiquidative) }}<br /><span class="ls-isin">au {{ jour(s.dateVl) }}</span></td>
                    <td data-test="frais-support">{{ pourcent(s.fraisSupportPct) }} par an</td>
                    <td><strong>{{ pourcent(total(s)) }}</strong></td>
                    <td>{{ pourcent(fraisContratPct()) }}</td>
                    <td>{{ s.label ?? '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <ul class="ls-cartes">
            @for (s of affiches(); track s.codeIsin) {
              <li class="carte" data-test="support">
                <div class="ls-tags">
                  <span class="tag">{{ famille(s) }}</span>
                  @if (s.nonCote) {
                    <span class="tag non-cote">Non coté</span>
                  }
                  @if (s.label) {
                    <span class="tag label">{{ s.label }}</span>
                  }
                </div>
                <button type="button" class="ls-nom" data-test="nom" (click)="ouvrir(s)">{{ s.nom }}</button>
                <ng-container *ngTemplateOutlet="echelle; context: { $implicit: s.niveauRisque }" />
                <dl>
                  <div><dt>Valeur liquidative</dt><dd>{{ euros(s.valeurLiquidative) }} au {{ jour(s.dateVl) }}</dd></div>
                  <div data-test="frais-support"><dt>Frais du support</dt><dd>{{ pourcent(s.fraisSupportPct) }} par an</dd></div>
                  <div><dt>Frais du contrat</dt><dd>{{ pourcent(fraisContratPct()) }} par an</dd></div>
                  <div><dt>Total annuel</dt><dd>{{ pourcent(total(s)) }}</dd></div>
                </dl>
              </li>
            }
          </ul>
        }
      </div>
    </div>

    <ng-template #echelle let-niveau>
      <span class="ls-risque" [attr.title]="explication" [attr.aria-label]="'Risque ' + niveau + '/7. ' + explication">
        <span class="ls-barres" aria-hidden="true">
          @for (n of sept; track n) {
            <i [class]="n <= niveau ? 'r' + niveau : ''"></i>
          }
        </span>
        <span aria-hidden="true">Risque {{ niveau }}/7</span>
      </span>
    </ng-template>

    @if (fiche(); as s) {
      <div class="ls-voile" (click)="fermer()"></div>
      <aside class="ls-fiche" role="dialog" aria-labelledby="ls-fiche-titre" data-test="fiche" (keydown.escape)="fermer()">
        <button type="button" class="ls-fermer" data-test="fermer" (click)="fermer()" aria-label="Fermer la fiche">×</button>
        <h2 id="ls-fiche-titre" tabindex="-1">{{ s.nom }}</h2>
        @if (s.nonCote) {
          <p class="avis" role="note" data-test="avertissement-non-cote"><strong>Non coté</strong></p>
        }
        <dl>
          <div><dt>Code ISIN</dt><dd>{{ s.codeIsin }}</dd></div>
          <div><dt>{{ familles() ? 'Famille' : 'Catégorie' }}</dt><dd>{{ famille(s) }}</dd></div>
          <div><dt>Niveau de risque</dt><dd><ng-container *ngTemplateOutlet="echelle; context: { $implicit: s.niveauRisque }" /></dd></div>
          <div><dt>Valeur liquidative</dt><dd>{{ euros(s.valeurLiquidative) }} au {{ jour(s.dateVl) }}</dd></div>
          @if (s.periodiciteVl) {
            <div data-test="periodicite"><dt>Fréquence de calcul du prix</dt><dd>{{ s.periodiciteVl }}</dd></div>
          }
          <div><dt>Frais du support</dt><dd>{{ pourcent(s.fraisSupportPct) }} par an</dd></div>
          <div><dt>Frais du contrat</dt><dd>{{ pourcent(fraisContratPct()) }} par an</dd></div>
          <div><dt>Total annuel</dt><dd>{{ pourcent(total(s)) }}</dd></div>
          @if (s.label) {
            <div><dt>Label</dt><dd>{{ s.label }}</dd></div>
          }
        </dl>

        <h3>Évolution de la valeur liquidative</h3>
        @switch (courbe()) {
          @case (null) {
            <p>Chargement…</p>
          }
          @case ('indisponible') {
            <p class="erreur" role="alert">L’historique est momentanément indisponible.</p>
          }
          @default {
            @if (points().length < 2) {
              <p class="vide" data-test="courbe-vide">Historique insuffisant pour tracer une courbe.</p>
            } @else {
              <figure class="ls-courbe" data-test="courbe">
                <svg viewBox="0 0 320 140" role="img" [attr.aria-label]="'Valeur liquidative du ' + jour(points()[0].dateVl) + ' au ' + jour(points()[points().length - 1].dateVl)"
                     (mousemove)="survoler($event)" (mouseleave)="survol.set(null)">
                  <line x1="0" x2="320" [attr.y1]="trace().yMin" [attr.y2]="trace().yMin" class="ls-axe" />
                  <polyline [attr.points]="trace().ligne" fill="none" class="ls-ligne" />
                  @if (survol(); as i) {
                    <line [attr.x1]="trace().x[i]" [attr.x2]="trace().x[i]" y1="6" y2="126" class="ls-repere" />
                    <circle [attr.cx]="trace().x[i]" [attr.cy]="trace().y[i]" r="4" class="ls-point" />
                  }
                </svg>
                <figcaption>
                  @if (survol(); as i) {
                    {{ jour(points()[i].dateVl) }} : <strong>{{ euros(points()[i].valeur) }}</strong>
                  } @else {
                    Du {{ jour(points()[0].dateVl) }} ({{ euros(points()[0].valeur) }}) au
                    {{ jour(points()[points().length - 1].dateVl) }} ({{ euros(points()[points().length - 1].valeur) }})
                  }
                </figcaption>
                <details>
                  <summary>Voir les valeurs</summary>
                  <table>
                    <thead><tr><th scope="col">Date</th><th scope="col">Valeur liquidative</th></tr></thead>
                    <tbody>
                      @for (p of points(); track p.dateVl) {
                        <tr><td>{{ jour(p.dateVl) }}</td><td>{{ euros(p.valeur) }}</td></tr>
                      }
                    </tbody>
                  </table>
                </details>
              </figure>
            }
          }
        }

        <p class="ls-document">
          <app-bouton-fonctionnalite cle="document-support" classe="lien">Document d’informations du support</app-bouton-fonctionnalite>
        </p>
      </aside>
    }
  `,
})
export class ListeSupports {
  private readonly service = inject(SupportsService);

  /** Supports déjà filtrés par le service de CAT-01B. */
  readonly supports = input.required<SupportEligible[]>();
  readonly fraisContratPct = input.required<number>();
  /** D7 : catégorie → famille ; vide tant que le regroupement n'est pas validé (filtre sur la catégorie d'origine). */
  readonly famillesCategories = input<Record<string, string>>({});

  protected readonly colonnes: { cle: Colonne; libelle: string }[] = [
    { cle: 'nom', libelle: 'Nom' },
    { cle: 'categorie', libelle: 'Catégorie' },
    { cle: 'niveauRisque', libelle: 'Risque' },
    { cle: 'valeurLiquidative', libelle: 'Valeur liquidative' },
    { cle: 'fraisSupportPct', libelle: 'Frais du support' },
    { cle: 'total', libelle: 'Total annuel' },
  ];
  protected readonly sept = [1, 2, 3, 4, 5, 6, 7];
  protected readonly explication = EXPLICATION_RISQUE;

  protected readonly vue = signal<Vue>('tableau');
  protected readonly recherche = signal('');
  protected readonly categorie = signal('');
  protected readonly risque = signal('');
  protected readonly tri = signal<Colonne | null>(null);
  protected readonly croissant = signal(true);
  protected readonly fiche = signal<SupportEligible | null>(null);
  protected readonly courbe = signal<PointVl[] | 'indisponible' | null>(null);
  protected readonly survol = signal<number | null>(null);

  protected readonly familles = computed(() => Object.keys(this.famillesCategories()).length > 0);
  protected readonly categories = computed(() =>
    [...new Set(this.supports().map((s) => this.famille(s)))].sort((a, b) => a.localeCompare(b, 'fr')),
  );
  protected readonly niveaux = computed(() => [...new Set(this.supports().map((s) => s.niveauRisque))].sort((a, b) => a - b));
  protected readonly affiches = computed(() => {
    const texte = this.recherche().trim().toLocaleLowerCase('fr');
    const liste = this.supports().filter(
      (s) =>
        (texte === '' || s.nom.toLocaleLowerCase('fr').includes(texte) || s.codeIsin.toLocaleLowerCase('fr').includes(texte)) &&
        (this.categorie() === '' || this.famille(s) === this.categorie()) &&
        (this.risque() === '' || s.niveauRisque === Number(this.risque())),
    );
    const colonne = this.tri();
    if (!colonne) {
      return liste; // ordre du service : niveau de risque puis nom
    }
    const sens = this.croissant() ? 1 : -1;
    return [...liste].sort((a, b) => sens * this.comparer(a, b, colonne));
  });
  protected readonly points = computed(() => {
    const c = this.courbe();
    return Array.isArray(c) ? c : [];
  });
  protected readonly trace = computed(() => {
    const p = this.points();
    const valeurs = p.map((v) => v.valeur);
    const min = Math.min(...valeurs);
    const ecart = Math.max(...valeurs) - min || 1;
    const x = p.map((_, i) => (i * 320) / Math.max(p.length - 1, 1));
    const y = valeurs.map((v) => 126 - ((v - min) / ecart) * 116);
    return { x, y, yMin: 127, ligne: x.map((xi, i) => `${xi.toFixed(1)},${y[i].toFixed(1)}`).join(' ') };
  });

  protected famille(s: SupportEligible): string {
    return this.famillesCategories()[s.categorie] ?? s.categorie;
  }

  protected total(s: SupportEligible): number {
    return s.fraisSupportPct + this.fraisContratPct();
  }

  protected trier(colonne: Colonne): void {
    this.croissant.set(this.tri() === colonne ? !this.croissant() : true);
    this.tri.set(colonne);
  }

  protected ouvrir(s: SupportEligible): void {
    this.fiche.set(s);
    this.courbe.set(null);
    this.survol.set(null);
    this.service.historique(s.codeIsin).subscribe({
      next: (points) => this.courbe.set(points),
      error: () => this.courbe.set('indisponible'),
    });
    // Le focus va sur le titre de la fiche : le clavier et le lecteur d'écran suivent l'ouverture.
    setTimeout(() => document.getElementById('ls-fiche-titre')?.focus());
  }

  protected fermer(): void {
    this.fiche.set(null);
  }

  protected survoler(event: MouseEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const largeur = svg.getBoundingClientRect().width || 320;
    const n = this.points().length;
    this.survol.set(Math.min(n - 1, Math.max(0, Math.round((event.offsetX / largeur) * (n - 1)))));
  }

  protected valeur(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected euros(montant: number): string {
    return `${nombre(montant, 2)} €`;
  }

  protected pourcent(taux: number): string {
    return `${nombre(taux, 2)} %`;
  }

  protected jour(date: string): string {
    return new Date(`${date}T00:00:00`).toLocaleDateString('fr-FR');
  }

  private comparer(a: SupportEligible, b: SupportEligible, colonne: Colonne): number {
    switch (colonne) {
      case 'nom':
        return a.nom.localeCompare(b.nom, 'fr');
      case 'categorie':
        return this.famille(a).localeCompare(this.famille(b), 'fr');
      case 'total':
        return this.total(a) - this.total(b);
      default:
        return a[colonne] - b[colonne];
    }
  }
}
