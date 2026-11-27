import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EtatsFonctionnalites, FonctionnalitesService } from './fonctionnalites';

/** Pages de test (D4), regroupées sous l'entrée « Outils de test » du menu. */
export const PAGES_DE_TEST = [
  { lien: '/test/scenarios', libelle: 'Scénarios des bouchons (ouverture de compte, Livret A)' },
  { lien: '/test/livret-a', libelle: 'Livret A : simulations sur la démarche en cours' },
  { lien: '/test/virements', libelle: 'Virements : virement entrant, refus de la banque' },
  { lien: '/test/verification-beneficiaire', libelle: 'Vérification du bénéficiaire (VoP)' },
  { lien: '/test/contrats', libelle: 'Contrats de test PER et assurance vie' },
  { lien: '/test/ordres', libelle: 'Ordres et positions de test' },
  { lien: '/test/catalogue', libelle: 'Catalogue des unités de compte' },
  { lien: '/test/evenements', libelle: 'Événements Kafka' },
  { lien: '/test/date-metier', libelle: 'Date métier' },
  { lien: '/test/emails', libelle: 'E-mails envoyés' },
  { lien: '/test/sms', libelle: 'SMS envoyés' },
  { lien: '/test/journal-securite', libelle: 'Journal de sécurité' },
];

// Lot 3 : page /test/fonctionnalites (dev et qualif) : état de chaque interrupteur de l'environnement, et accès aux
// autres outils de test.
@Component({
  selector: 'app-fonctionnalites-test',
  imports: [RouterLink],
  template: `
    <main class="fonctionnalites-test">
      <a routerLink="/espace">← Mon espace</a>
      <h1>Outils de test</h1>
      <h2>Interrupteurs des fonctionnalités</h2>
      <p>« Disponible » : le bouton mène à la fonctionnalité ; « Bientôt » : bouton désactivé. Réglés dans helm.yaml (FONCTIONNALITE_*).</p>
      <table data-test="interrupteurs">
        <thead><tr><th scope="col">Fonctionnalité</th><th scope="col">État</th></tr></thead>
        <tbody>
          @for (e of etats(); track e.cle) {
            <tr><td>{{ e.cle }}</td><td [class.actif]="e.active">{{ e.active ? 'Disponible' : 'Bientôt' }}</td></tr>
          } @empty {
            <tr><td colspan="2">Interrupteurs indisponibles.</td></tr>
          }
        </tbody>
      </table>
      <h2>Pages de test</h2>
      <ul data-test="pages">
        @for (p of pages; track p.lien) {
          <li><a [routerLink]="p.lien">{{ p.libelle }}</a></li>
        }
      </ul>
    </main>
  `,
  styles: `
    .fonctionnalites-test { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    table { border-collapse: collapse; width: 100%; }
    th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #e5eaf3; }
    .actif { color: #067647; font-weight: 700; }
    li { margin: 6px 0; }
  `,
})
export class FonctionnalitesTest {
  protected readonly pages = PAGES_DE_TEST;
  private readonly brut = toSignal(inject(FonctionnalitesService).etats(), { initialValue: {} as EtatsFonctionnalites });
  protected etats() {
    return Object.entries(this.brut()).map(([cle, active]) => ({ cle, active }));
  }
}
