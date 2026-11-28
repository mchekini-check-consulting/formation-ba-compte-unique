import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ParametresPublics, SupportsService } from '../supports/supports';

export type CleMention = 'taux-non-garanti' | 'perte-capital-uc' | 'performances-passees' | 'non-cote';

// Lot 7 (PO, 28/11/2026) : mention réglementaire affichée là où elle s'applique. Le texte est un paramètre
// (MENTION_* de helm.yaml, lu par GET /catalogue/parametres-publics) : vide ou illisible, rien ne s'affiche.
@Component({
  selector: 'app-mention',
  template: `
    @if (texte(); as t) {
      <p class="mention-reglementaire" [attr.data-mention]="cle()">{{ t }}</p>
    }
  `,
  styles: `
    .mention-reglementaire { margin: 8px 0; padding: 8px 12px; border-left: 3px solid #8a97ad; background: #f5f8fd; color: #3b4760; font-size: 13px; line-height: 1.5; }
  `,
})
export class Mention {
  readonly cle = input.required<CleMention>();
  private readonly parametres = toSignal(
    inject(SupportsService).parametresPublics().pipe(catchError(() => of(null as ParametresPublics | null))),
    { initialValue: null },
  );
  protected readonly texte = computed(() => this.parametres()?.mentions?.[this.cle()] ?? '');
}
