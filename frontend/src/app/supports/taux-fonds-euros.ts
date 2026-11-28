import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ParametresPublics, Produit, SupportsService, tauxFondsEurosAv, tauxFondsEurosPer } from './supports';

// PO 28/11/2026 : taux du fonds en euros d'un produit, identique pour le PER et l'assurance vie, tout lu dans les
// paramètres : taux brut visé, date, net (si les frais du fonds en euros sont renseignés) et « Taux non garanti. » sur une
// ligne ; la garantie du capital sur la ligne suivante. Sans paramètre (ou sans réponse), rien ne s'affiche.
@Component({
  selector: 'app-taux-fonds-euros',
  template: `
    @if (taux(); as t) {
      <p class="tfe" [attr.data-test]="'taux-fonds-euros-' + produit()">
        <span class="tfe-ligne">Fonds en euros : {{ t.avant }} <strong>{{ t.taux }}</strong> {{ t.detail }}</span>
        @if (t.garantie) {
          <span class="tfe-garantie" data-test="garantie">{{ t.garantie }}</span>
        }
      </p>
    }
  `,
  styles: `
    .tfe { margin: 4px 0; }
    .tfe-ligne, .tfe-garantie { display: block; }
    .tfe-garantie { font-weight: 700; margin-top: 2px; }
  `,
})
export class TauxFondsEuros {
  readonly produit = input.required<Produit>();
  private readonly parametres = toSignal(
    inject(SupportsService).parametresPublics().pipe(catchError(() => of(null as ParametresPublics | null))),
    { initialValue: null },
  );
  protected readonly taux = computed(() =>
    this.produit() === 'PER' ? tauxFondsEurosPer(this.parametres()) : tauxFondsEurosAv(this.parametres()),
  );
}
