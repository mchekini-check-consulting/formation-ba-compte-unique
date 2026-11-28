import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { PRESENTATIONS } from '../decouverte/decouverte-page';
import { pourcentage } from '../livret-a/parametres';
import { ProfilService } from '../profil/profil';
import { SupportsService } from '../supports/supports';
import { TauxFondsEuros } from '../supports/taux-fonds-euros';
import { Produit } from '../souscription/souscription';
import { Mention } from '../pedagogie/mention';

// Lot 5 (PO, 27/11/2026) : page d'entrée du tunnel de souscription (?produit=PER|AV) : présentation (règles de
// CU-311 / CU-331), frais des unités de compte et fonds en euros lus dans les paramètres publics, puis « Ouvrir mon
// PER » / « Ouvrir mon assurance vie ». L'étape 1 (profil, CU-10) est vérifiée au clic : profil validé, la cliente
// passe aux documents ; sinon le questionnaire s'ouvre dans le tunnel, puis elle revient aux documents.
@Component({
  selector: 'app-produit-page',
  imports: [Mention, TauxFondsEuros],
  template: `
    <div class="page produit-page">
      <div class="eyebrow">Épargne et placements</div>
      <h1>{{ produit === 'PER' ? 'Le plan d’épargne retraite' : 'L’assurance vie' }}</h1>
      <p class="lead">{{ presentation.accroche }}</p>

      <div class="regles">
        @for (r of presentation.regles; track r.titre) {
          <section class="carte">
            <h2>{{ r.titre }}</h2>
            <p>{{ r.texte }}</p>
          </section>
        }
      </div>

      <section class="carte frais" data-test="frais">
        <h2>Frais</h2>
        @if (parametres(); as p) {
          @if (p.fraisUc) {
            <p>Frais de gestion des unités de compte : <strong>{{ p.fraisUc }} par an</strong>.</p>
          }
          @if (p.fraisFondsEuros) {
            <p data-test="frais-fonds-euros">Frais de gestion du fonds en euros : <strong>{{ p.fraisFondsEuros }} par an</strong>.</p>
          }
        }
        <app-taux-fonds-euros [produit]="produit" />
        <p>Frais de gestion détaillés avant signature.</p>
        <app-mention cle="perte-capital-uc" />
      </section>

      @if (erreur()) {
        <p class="erreur" role="alert">{{ erreur() }}</p>
      }
      <button class="btn p" type="button" data-test="ouvrir" [disabled]="envoi()" (click)="demarrer()">
        {{ produit === 'PER' ? 'Ouvrir mon PER' : 'Ouvrir mon assurance vie' }}
      </button>
    </div>
  `,
  styles: `
    .regles { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; margin-bottom: 16px; }
    .regles h2, .frais h2 { margin: 0 0 4px; font-size: 18px; }
    .frais { margin-bottom: 20px; }
    .frais p { margin: 4px 0; }
  `,
})
export class ProduitPage {
  private readonly router = inject(Router);
  private readonly profils = inject(ProfilService);
  protected readonly produit: Produit =
    inject(ActivatedRoute).snapshot.queryParamMap.get('produit') === 'AV' ? 'AV' : 'PER';
  protected readonly presentation = PRESENTATIONS[this.produit];
  protected readonly envoi = signal(false);
  protected readonly erreur = signal('');
  protected readonly parametres = toSignal(
    inject(SupportsService)
      .parametresPublics()
      .pipe(
        map((p) => ({
          fraisUc: p.fraisUcPct?.[this.produit] == null ? null : pourcentage(p.fraisUcPct[this.produit]),
          // D1 : frais du fonds en euros du PER (PER-09) ; ceux de l'assurance vie restent vides (CU-530).
          fraisFondsEuros: (() => {
            const frais = (this.produit === 'AV' ? p.fondsEurosAv : p.fondsEurosPer)?.fraisGestionPct;
            return frais == null ? null : pourcentage(frais);
          })(),
        })),
      ),
    { initialValue: null },
  );

  /** Étape 1 du tunnel (CU-10) : profil validé → documents ; à compléter ou à revalider → questionnaire. */
  protected demarrer(): void {
    this.envoi.set(true);
    this.erreur.set('');
    this.profils.lire().subscribe({
      next: (etat) => {
        const suite = etat.statut === 'VALIDE' ? '/decouvrir' : '/profil';
        void this.router.navigate([suite], { queryParams: { produit: this.produit } });
      },
      error: () => {
        this.envoi.set(false);
        this.erreur.set('La souscription est momentanément indisponible. Réessayez plus tard.');
      },
    });
  }
}
