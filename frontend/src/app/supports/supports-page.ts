import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { BoutonFonctionnalite } from '../fonctionnalites/bouton-fonctionnalite';
import { Tunnel } from '../tunnel/tunnel';
import { ListeSupports } from './liste-supports';
import { ParametresPublics, Produit, SupportsDisponibles, SupportsService, tauxFondsEuros } from './supports';

type Etat = 'chargement' | 'affiche' | 'indisponible';

const PRODUITS: Record<Produit, string> = { PER: 'PER', AV: 'assurance vie' };

const pourcent = (taux: number) =>
  `${taux.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} %`;

// CU-412 (CAT-01B) : écran « Supports disponibles » du PER et de l'assurance vie. La liste vient déjà filtrée par le
// backend (profil, produit, supports actifs). Lot 6 : le fonds en euros en tête, comme socle du contrat (données
// existantes et paramètres D1 / D7, un paramètre vide n'est pas affiché), puis la liste commune des unités de compte
// (tableau ou cartes, tri, recherche, filtres à gauche, fiche). Profil non validé : aucune unité de compte, message et
// lien vers le questionnaire ; le fonds en euros reste affiché.
@Component({
  selector: 'app-supports-page',
  imports: [RouterLink, ListeSupports, Tunnel, BoutonFonctionnalite],
  template: `
    <div class="page supports">
      <!-- PO 28/11 : ouverte depuis « Ma gestion » en gestion libre, la page est l'étape « Mes supports » du tunnel, en
           consultation ; le panneau « Ma répartition » reste en Bientôt (CU-525 GL-01). -->
      <app-tunnel [produit]="produit()" etape="gestion" [actif]="dansLeTunnel">
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
            <h2>Fonds en euros, le socle de votre contrat</h2>
            <dl>
              <div><dt>Garantie</dt><dd>Capital garanti</dd></div>
              <div><dt>Intérêts</dt><dd>Crédités une fois par an</dd></div>
              <div><dt>Disponibilité</dt><dd>Toujours disponible, quel que soit votre profil</dd></div>
              @if (fondsEuros()?.fraisGestionPct != null) {
                <div data-test="frais-fonds-euros"><dt>Frais de gestion</dt><dd>{{ pourcent(fondsEuros()!.fraisGestionPct!) }} par an</dd></div>
              }
              @if (tauxVise(); as t) {
                <div data-test="taux-vise"><dt>Taux</dt><dd>{{ t.avant }} <strong>{{ t.taux }}</strong> {{ t.detail }}</dd></div>
              }
              @if (fondsEuros()?.tauxServiPct != null) {
                <div data-test="taux-servi"><dt>Taux servi</dt><dd>{{ pourcent(fondsEuros()!.tauxServiPct!) }}</dd></div>
              }
            </dl>
          </section>

          @if (!donnees()!.profilValide) {
            <p class="avis" data-test="profil-a-valider" role="status">
              {{ donnees()!.message }}.
              <a routerLink="/profil">Compléter mon questionnaire</a>
            </p>
          } @else {
            <h2>Unités de compte</h2>
            <p class="frais" data-test="frais-contrat">
              Frais de gestion du contrat sur les unités de compte : <strong>{{ pourcent(donnees()!.fraisContratPct) }} par an</strong>,
              en plus des frais propres à chaque support.
            </p>
            <app-liste-supports [supports]="donnees()!.supports" [fraisContratPct]="donnees()!.fraisContratPct" [famillesCategories]="familles()" />
          }
        }
      }
      @if (dansLeTunnel) {
        <section class="carte ma-repartition" data-test="ma-repartition" aria-labelledby="titre-ma-repartition">
          <h2 id="titre-ma-repartition">Ma répartition</h2>
          <app-bouton-fonctionnalite cle="repartition-gestion-libre" classe="lien">Répartir mes versements entre mes supports</app-bouton-fonctionnalite>
        </section>
      }
      </app-tunnel>
    </div>
  `,
})
export class SupportsPage {
  private readonly service = inject(SupportsService);
  protected readonly produits: Produit[] = ['PER', 'AV'];
  private readonly route = inject(ActivatedRoute);
  protected readonly produit = signal<Produit>(this.route.snapshot.queryParamMap.get('produit') === 'PER' ? 'PER' : 'AV');
  protected readonly dansLeTunnel = this.route.snapshot.queryParamMap.get('tunnel') === '1';
  protected readonly etat = signal<Etat>('chargement');
  protected readonly donnees = signal<SupportsDisponibles | null>(null);
  /** Sans réponse, aucun paramètre n'est affiché (jamais de valeur écrite en dur). */
  private readonly parametres = toSignal(
    this.service.parametresPublics().pipe(catchError(() => of(null as ParametresPublics | null))),
    { initialValue: null },
  );
  protected readonly fondsEuros = computed(() => {
    const p = this.parametres();
    return p ? (this.produit() === 'PER' ? p.fondsEurosPer : p.fondsEurosAv) : null;
  });
  protected readonly tauxVise = computed(() => tauxFondsEuros(this.fondsEuros()));
  protected readonly familles = computed(() => this.parametres()?.famillesCategories ?? {});
  protected readonly pourcent = pourcent;

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

  private charger(): void {
    this.etat.set('chargement');
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
