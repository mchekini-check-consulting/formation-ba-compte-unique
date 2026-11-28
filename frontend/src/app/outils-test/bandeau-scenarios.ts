import { Component, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GROUPES } from './scenarios-test';
import { revenirAuCasNominal, scenarioChoisi, versionScenarios } from './scenarios';

// PO 28/11/2026 : sur les pages /test/... (local, dev et qualif), un bandeau rappelle les scénarios de bouchons actifs
// (autres que le cas nominal), avec « Revenir au cas nominal ». Jamais affiché à une cliente (pages de test seulement).
@Component({
  selector: 'app-bandeau-scenarios',
  imports: [RouterLink],
  template: `
    <div class="bandeau-scenarios" role="status" data-test="bandeau-scenarios">
      @if (actifs().length === 0) {
        <span>Scénario de test actif : <strong>cas nominal</strong> pour tous les bouchons.</span>
      } @else {
        <span>Scénario de test actif :</span>
        <ul>
          @for (a of actifs(); track a.cle) {
            <li><strong>{{ a.parcours }}</strong> · {{ a.titre }} : {{ a.libelle }}</li>
          }
        </ul>
        <button type="button" data-test="cas-nominal" (click)="revenir()">Revenir au cas nominal</button>
      }
      <a routerLink="/test/scenarios">Choisir les scénarios</a>
    </div>
  `,
  styles: `
    .bandeau-scenarios { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; padding: 10px 16px; background: #fff4e5; border-bottom: 2px solid #f5a524; color: #5c3200; font: 14px system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
    ul { margin: 0; padding-left: 18px; }
    button { padding: 6px 12px; border-radius: 8px; border: 1px solid #8a4b00; background: #fff; color: #5c3200; font-weight: 700; cursor: pointer; }
    a { color: #5c3200; font-weight: 700; }
  `,
})
export class BandeauScenarios {
  protected readonly actifs = computed(() => {
    versionScenarios();
    return GROUPES.flatMap((p) =>
      p.groupes
        .map((g) => ({ g, code: scenarioChoisi(g.cle, g.liste) }))
        .filter(({ g, code }) => code !== g.liste[0].code)
        .map(({ g, code }) => ({ cle: g.cle, parcours: p.parcours, titre: g.titre, libelle: g.liste.find((s) => s.code === code)!.libelle })),
    );
  });

  protected revenir(): void {
    revenirAuCasNominal();
  }
}
