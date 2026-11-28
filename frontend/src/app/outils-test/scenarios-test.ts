import { Component, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CAS_FISCAUX, CAS_SANCTIONS } from '../identification/bouchons-identite';
import { CAS_DE_TEST } from '../identification/etape-identite';
import { SCENARIOS_PIECE } from '../identification/etape-piece';
import { SCENARIOS_SELFIE } from '../identification/etape-selfie';
import { SCENARIOS_PIECE_LIVRET_A, SCENARIOS_SELFIE_LIVRET_A } from '../livret-a/etape-piece-livret-a';
import { CODES_DE_TEST } from '../ouverture/etape-signature';
import { CleScenario, choisirScenario, scenarioChoisi, versionScenarios } from './scenarios';

interface Groupe {
  cle: CleScenario;
  titre: string;
  liste: readonly { code: string; libelle: string }[];
}

/** Bouchons pilotés depuis cette page, dans l'ordre des parcours. */
export const GROUPES: { parcours: string; ancre: string; groupes: Groupe[] }[] = [
  {
    parcours: 'Ouverture de compte',
    ancre: 'ouverture-compte',
    groupes: [
      { cle: 'ouverture.identite', titre: 'Mon identité : contrôle sanctions et PEP (bouchon US-B7, CUB-70)', liste: CAS_DE_TEST },
      { cle: 'ouverture.piece', titre: 'Ma pièce d’identité : analyse (bouchon Stripe Identity)', liste: SCENARIOS_PIECE },
      { cle: 'ouverture.selfie', titre: 'Mon selfie : vérification de présence (bouchon Stripe Identity)', liste: SCENARIOS_SELFIE },
    ],
  },
  {
    parcours: 'Ouverture d’un Livret A',
    ancre: 'livret-a',
    groupes: [
      { cle: 'livret-a.sanctions', titre: 'Contrôle sanctions et PPE (CU-28)', liste: CAS_SANCTIONS },
      { cle: 'livret-a.piece', titre: 'Nouvelle pièce d’identité : analyse (CU-29)', liste: SCENARIOS_PIECE_LIVRET_A },
      { cle: 'livret-a.selfie', titre: 'Nouvelle pièce : vérification de présence (CU-29)', liste: SCENARIOS_SELFIE_LIVRET_A },
      { cle: 'livret-a.fiscal', titre: 'Administration fiscale (CU-295)', liste: CAS_FISCAUX },
    ],
  },
];

// D4 (PO, 27/11/2026) : page /test/scenarios (local, dev et qualif). Les choix de résultat simulé des bouchons, qui
// étaient affichés dans les parcours de la cliente, se font ici ; ils sont mémorisés dans ce navigateur et
// s'appliquent au prochain envoi de l'étape concernée. Sans choix : le cas nominal (premier de chaque liste).
@Component({
  selector: 'app-scenarios-test',
  imports: [RouterLink],
  template: `
    <main class="scenarios-test">
      <a routerLink="/">← Retour au site</a>
      <h1>Scénarios des bouchons (test)</h1>
      <p>
        Choisissez le résultat simulé avant l’étape du parcours : il s’applique au prochain envoi, dans ce navigateur.
        Actions sur une démarche en cours : <a routerLink="/test/livret-a">Livret A</a> ·
        <a routerLink="/test/virements">Virements</a>.
      </p>
      @for (p of parcours; track p.parcours) {
        <h2 [id]="p.ancre">{{ p.parcours }}</h2>
        @for (g of p.groupes; track g.cle) {
          <fieldset [attr.data-test]="g.cle">
            <legend>{{ g.titre }}</legend>
            @for (s of g.liste; track s.code) {
              <label>
                <input type="radio" [name]="g.cle" [value]="s.code" [checked]="choix()[g.cle] === s.code" (change)="choisir(g.cle, s.code)" />
                {{ s.libelle }}
              </label>
            }
          </fieldset>
        }
        @if ($first) {
          <fieldset data-test="docusign">
            <legend>Signature de la convention : codes du bouchon Docusign (CU-89)</legend>
            <ul>
              @for (c of codesDocusign; track c.code) {
                <li><span class="mono">{{ c.code }}</span> : {{ c.libelle }}</li>
              }
            </ul>
          </fieldset>
        }
      }
    </main>
  `,
  styles: `
    .scenarios-test { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h2 { color: #0b2545; margin-top: 28px; }
    fieldset { border: 1px dashed #b7c3d9; border-radius: 12px; padding: 12px 16px; margin: 12px 0; display: grid; gap: 6px; }
    legend { font-weight: 700; padding: 0 6px; }
    .mono { font-family: ui-monospace, monospace; font-weight: 700; }
  `,
})
export class ScenariosTest {
  protected readonly parcours = GROUPES;
  protected readonly codesDocusign = CODES_DE_TEST;
  protected readonly choix = computed<Record<string, string>>(() => {
    versionScenarios();
    return Object.fromEntries(GROUPES.flatMap((p) => p.groupes).map((g) => [g.cle, scenarioChoisi(g.cle, g.liste)]));
  });

  protected choisir(cle: CleScenario, code: string): void {
    choisirScenario(cle, code);
  }
}
