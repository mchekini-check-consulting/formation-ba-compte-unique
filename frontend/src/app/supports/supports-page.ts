import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Produit, SupportsDisponibles, SupportsService } from './supports';

type Etat = 'chargement' | 'affiche' | 'indisponible';

const PRODUITS: Record<Produit, string> = { PER: 'PER', AV: 'assurance vie' };

// CU-412 (CAT-01B) : écran « Supports disponibles » du PER et de l'assurance vie. La liste vient déjà filtrée par le
// backend (profil, produit, supports actifs) ; l'écran propose seulement les filtres catégorie et niveau de risque.
// Profil non validé : aucune unité de compte, message et lien vers le questionnaire ; le fonds en euros reste affiché.
@Component({
  selector: 'app-supports-page',
  imports: [RouterLink],
  template: `
    <main class="supports">
      <a routerLink="/espace">← Mon espace</a>
      <h1>Supports disponibles — {{ libelle(produit()) }}</h1>
      <div class="onglets" role="group" aria-label="Produit">
        @for (p of produits; track p) {
          <button type="button" [attr.data-test]="'produit-' + p" [class.actif]="produit() === p" [attr.aria-pressed]="produit() === p" (click)="choisirProduit(p)">
            {{ p === 'PER' ? 'PER' : 'Assurance vie' }}
          </button>
        }
      </div>

      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="erreur" role="alert">La liste des supports est momentanément indisponible. Réessayez plus tard.</p>
        }
        @case ('affiche') {
          <section class="carte euros" data-test="fonds-euros">
            <span class="tag">Fonds en euros</span>
            <h2>Fonds en euros</h2>
            <p>Capital garanti, intérêts crédités une fois par an. Toujours disponible, quel que soit votre profil.</p>
          </section>

          @if (!donnees()!.profilValide) {
            <p class="avis" data-test="profil-a-valider" role="status">
              {{ donnees()!.message }}.
              <a routerLink="/profil">Compléter mon questionnaire</a>
            </p>
          } @else {
            <p class="frais" data-test="frais-contrat">
              Frais de gestion du contrat sur les unités de compte : <strong>{{ pourcent(donnees()!.fraisContratPct) }} par an</strong>,
              en plus des frais propres à chaque support.
            </p>

            <div class="filtres">
              <label>Catégorie
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
            </div>

            @if (donnees()!.supports.length === 0) {
              <p class="vide">Aucun support disponible pour votre profil.</p>
            } @else if (affiches().length === 0) {
              <p class="vide">Aucun support ne correspond à ces filtres.</p>
            } @else {
              <ul class="liste">
                @for (s of affiches(); track s.codeIsin) {
                  <li class="carte" data-test="support">
                    <div class="tags">
                      <span class="tag">{{ s.categorie }}</span>
                      <span class="tag risque">Risque {{ s.niveauRisque }}/7</span>
                      @if (s.nonCote) {
                        <span class="tag non-cote">Non coté</span>
                      }
                      @if (s.label) {
                        <span class="tag label">{{ s.label }}</span>
                      }
                    </div>
                    <h3>{{ s.nom }}</h3>
                    <dl>
                      <div><dt>Valeur liquidative</dt><dd>{{ euros(s.valeurLiquidative) }} au {{ jour(s.dateVl) }}</dd></div>
                      <div data-test="frais-support"><dt>Frais du support</dt><dd>{{ pourcent(s.fraisSupportPct) }} par an</dd></div>
                      <div><dt>Frais du contrat</dt><dd>{{ pourcent(donnees()!.fraisContratPct) }} par an</dd></div>
                    </dl>
                  </li>
                }
              </ul>
            }
          }
        }
      }
    </main>
  `,
  styles: `
    .supports { max-width: 1100px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 16px; }
    h2, h3 { margin: 8px 0; color: #0b2545; }
    h3 { font-size: 17px; }
    .onglets { display: flex; gap: 8px; margin-bottom: 24px; }
    .onglets button { padding: 10px 18px; border-radius: 999px; border: 1px solid #0b2545; background: #fff; color: #0b2545; font-weight: 700; cursor: pointer; }
    .onglets button.actif { background: #0b2545; color: #fff; }
    .carte { border: 1px solid #d9dee6; border-radius: 14px; padding: 16px; background: #fff; }
    .euros { margin-bottom: 20px; background: #f4f6fa; }
    .liste { list-style: none; padding: 0; margin: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
    .tags { display: flex; flex-wrap: wrap; gap: 6px; }
    .tag { font-size: 12px; padding: 4px 10px; border-radius: 999px; background: #eef1f5; color: #5d6887; font-weight: 700; }
    .tag.risque { background: #fef0c7; color: #93370d; }
    .tag.non-cote { background: #fee4e2; color: #b42318; }
    .tag.label { background: #d1fadf; color: #067647; }
    dl { margin: 8px 0 0; display: grid; gap: 4px; font-size: 14px; }
    dl div { display: flex; justify-content: space-between; gap: 12px; }
    dt { color: #5d6887; }
    dd { margin: 0; font-weight: 700; text-align: right; }
    .filtres { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
    label { display: flex; flex-direction: column; gap: 6px; font-weight: 700; font-size: 14px; }
    select { padding: 9px 12px; border: 1px solid #d9dee6; border-radius: 10px; font: inherit; font-weight: 400; }
    .frais { line-height: 1.6; }
    .avis { padding: 14px 16px; border-radius: 12px; background: #fffaeb; border: 1px solid #fedf89; line-height: 1.6; }
    .vide { color: #5d6887; }
    .erreur { color: #b42318; font-weight: 700; }
  `,
})
export class SupportsPage {
  private readonly service = inject(SupportsService);
  protected readonly produits: Produit[] = ['PER', 'AV'];
  protected readonly produit = signal<Produit>(
    inject(ActivatedRoute).snapshot.queryParamMap.get('produit') === 'PER' ? 'PER' : 'AV',
  );
  protected readonly etat = signal<Etat>('chargement');
  protected readonly donnees = signal<SupportsDisponibles | null>(null);
  protected readonly categorie = signal('');
  protected readonly risque = signal('');
  protected readonly categories = computed(() =>
    [...new Set((this.donnees()?.supports ?? []).map((s) => s.categorie))].sort((a, b) => a.localeCompare(b, 'fr')),
  );
  protected readonly niveaux = computed(() =>
    [...new Set((this.donnees()?.supports ?? []).map((s) => s.niveauRisque))].sort((a, b) => a - b),
  );
  protected readonly affiches = computed(() =>
    (this.donnees()?.supports ?? []).filter(
      (s) =>
        (this.categorie() === '' || s.categorie === this.categorie()) &&
        (this.risque() === '' || s.niveauRisque === Number(this.risque())),
    ),
  );

  constructor() {
    this.charger();
  }

  protected libelle(produit: Produit): string {
    return PRODUITS[produit];
  }

  protected choisirProduit(produit: Produit): void {
    if (produit !== this.produit()) {
      this.produit.set(produit);
      this.charger();
    }
  }

  protected valeur(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }

  protected euros(montant: number): string {
    return `${SupportsPage.nombre(montant, 2)} €`;
  }

  protected pourcent(taux: number): string {
    return `${SupportsPage.nombre(taux, 2)} %`;
  }

  protected jour(date: string): string {
    return new Date(`${date}T00:00:00`).toLocaleDateString('fr-FR');
  }

  private static nombre(valeur: number, decimales: number): string {
    return valeur
      .toLocaleString('fr-FR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales })
      .replace(/ | /g, ' ');
  }

  private charger(): void {
    this.etat.set('chargement');
    this.categorie.set('');
    this.risque.set('');
    this.service.lister(this.produit()).subscribe({
      next: (donnees) => {
        this.donnees.set(donnees);
        this.etat.set('affiche');
      },
      // Critère 11 : jamais de liste incomplète si le service ne répond pas.
      error: () => {
        this.donnees.set(null);
        this.etat.set('indisponible');
      },
    });
  }
}
