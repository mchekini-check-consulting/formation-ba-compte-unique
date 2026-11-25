import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { apiBase } from '../api';
import { afficherDate } from '../date-metier-test/date-metier';

/** Une alerte envoyée à l'équipe (CU-382). */
export interface AlerteEquipe {
  dateMetier: string;
  origine: string;
  message: string;
  emailEnvoye: boolean;
}

// CU-382 (TECH-14) : journal des alertes à l'équipe (traitements en échec : topic d'erreurs, batch du catalogue…),
// affiché sur les pages de test /test/catalogue et /test/evenements. Route GET /test/alertes, absente en preprod et
// prod.
@Component({
  selector: 'app-journal-alertes',
  template: `
    <section class="alertes">
      <h2>Journal des alertes à l’équipe</h2>
      <button type="button" class="secondaire" data-test="actualiser-alertes" (click)="charger()">Actualiser</button>
      @if (indisponible()) {
        <p class="vide">Journal des alertes indisponible pour le moment.</p>
      } @else if (alertes().length === 0) {
        <p class="vide">Aucune alerte.</p>
      } @else {
        <div class="tableau" data-test="alertes">
          <table>
            <thead>
              <tr>
                <th scope="col">Date métier</th>
                <th scope="col">Origine</th>
                <th scope="col">Message</th>
                <th scope="col">E-mail à l’équipe</th>
              </tr>
            </thead>
            <tbody>
              @for (a of alertes(); track $index) {
                <tr>
                  <td>{{ afficher(a.dateMetier) }}</td>
                  <td>{{ a.origine }}</td>
                  <td>{{ a.message }}</td>
                  <td>{{ a.emailEnvoye ? 'Envoyé' : 'Non envoyé' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    h2 { color: #0b2545; margin: 32px 0 12px; font-size: 20px; }
    .vide { color: #5d6887; }
    button { padding: 8px 14px; border-radius: 10px; border: 1px solid #0b2545; background: #fff; color: #0b2545; font-weight: 700; cursor: pointer; margin-bottom: 12px; }
    .tableau { overflow-x: auto; border: 1px solid #eef1f5; border-radius: 8px; }
    table { border-collapse: collapse; width: 100%; font-size: 14px; }
    th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eef1f5; }
    th { background: #f4f6fa; white-space: nowrap; }
  `,
})
export class JournalAlertes {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/test/alertes`;
  protected readonly alertes = signal<AlerteEquipe[]>([]);
  protected readonly indisponible = signal(false);
  protected readonly afficher = afficherDate;

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.http.get<AlerteEquipe[]>(this.url).subscribe({
      next: (alertes) => {
        this.alertes.set(alertes);
        this.indisponible.set(false);
      },
      error: () => this.indisponible.set(true),
    });
  }
}
