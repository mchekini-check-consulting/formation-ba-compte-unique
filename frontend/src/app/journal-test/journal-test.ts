import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { apiBase } from '../api';

export interface LigneJournal {
  id: number;
  survenuLe: string;
  evenement: string;
  resultat: string | null;
  motif: string | null;
  identifiantSaisi: string | null;
  identifiantConnexionId: number | null;
  adresseIp: string | null;
  compteDemande: string | null;
}

type Etat = 'chargement' | 'affiche' | 'indisponible';

// CU-167 et CU-48 : page /test/journal-securite. En environnement de test (dev, qualif, local),
// les 100 derniers événements du journal de sécurité (connexions, blocages, déconnexions,
// expirations...) sont affichés pour la recette, sans accès à la base. Le backend répond 404 en
// preprod et prod : la page l'indique.
@Component({
  selector: 'app-journal-test',
  imports: [RouterLink],
  template: `
    <main class="journal-test">
      <a routerLink="/">← Retour au site</a>
      <h1>Journal de sécurité (test)</h1>
      <p class="muted">Environnement de test : les 100 derniers événements, du plus récent au plus ancien.</p>
      <form (submit)="filtrer($event, identifiant.value)">
        <label for="identifiant">Identifiant saisi</label>
        <input #identifiant id="identifiant" name="identifiant" placeholder="Tous les identifiants" />
        <button type="submit">Actualiser</button>
      </form>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="vide" role="status">Journal non consultable dans cet environnement.</p>
        }
        @case ('affiche') {
          @if (lignes().length === 0) {
            <p class="vide">Aucun événement.</p>
          } @else {
            <div class="tableau">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col">Événement</th>
                    <th scope="col">Résultat</th>
                    <th scope="col">Motif</th>
                    <th scope="col">Identifiant</th>
                    <th scope="col">Adresse IP</th>
                    <th scope="col">Compte demandé</th>
                  </tr>
                </thead>
                <tbody>
                  @for (ligne of lignes(); track ligne.id) {
                    <tr>
                      <td>{{ ligne.survenuLe }}</td>
                      <td>{{ ligne.evenement }}</td>
                      <td>{{ ligne.resultat ?? '' }}</td>
                      <td>{{ ligne.motif ?? '' }}</td>
                      <td>{{ ligne.identifiantSaisi ?? '' }}</td>
                      <td>{{ ligne.adresseIp ?? '' }}</td>
                      <td>{{ ligne.compteDemande ?? '' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }
      }
    </main>
  `,
  styles: `
    .journal-test { max-width: 1100px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    .muted, .vide { color: #5d6887; line-height: 1.6; }
    form { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; margin: 16px 0; }
    label { font-weight: 700; }
    input { padding: 9px 12px; border: 1px solid #d9dee6; border-radius: 10px; min-width: 0; }
    button { padding: 10px 18px; border-radius: 10px; border: 1px solid #0b2545; background: #fff; color: #0b2545; font-weight: 700; cursor: pointer; }
    .tableau { overflow-x: auto; border: 1px solid #d9dee6; border-radius: 12px; background: #fff; }
    table { border-collapse: collapse; width: 100%; font-size: 14px; }
    th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eef1f5; white-space: nowrap; }
    th { background: #f4f6fa; }
  `,
})
export class JournalTest {
  private readonly http = inject(HttpClient);
  protected readonly etat = signal<Etat>('chargement');
  protected readonly lignes = signal<LigneJournal[]>([]);

  constructor() {
    this.charger('');
  }

  protected filtrer(event: Event, identifiant: string): void {
    event.preventDefault();
    this.charger(identifiant.trim());
  }

  private charger(identifiant: string): void {
    this.etat.set('chargement');
    const params: Record<string, string> = identifiant ? { identifiant } : {};
    this.http.get<LigneJournal[]>(`${apiBase('backend')}/test/journal-securite`, { params }).subscribe({
      next: (lignes) => {
        this.lignes.set(lignes);
        this.etat.set('affiche');
      },
      error: () => {
        this.lignes.set([]);
        this.etat.set('indisponible');
      },
    });
  }
}
