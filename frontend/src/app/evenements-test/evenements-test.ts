import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { afficherDate } from '../date-metier-test/date-metier';
import { EtatEvenements, EvenementsTestService } from './evenements';

type Etat = 'chargement' | 'affiche' | 'indisponible';

// CU-382 (TECH-14) : page /test/evenements. Montre la boîte d'envoi (événements publiés dans Kafka ou en attente),
// les événements reçus par le journal back-office et les messages en erreur ; un bouton envoie un message invalide
// pour vérifier qu'il part dans le topic d'erreurs après 3 essais. Absente en preprod et prod.
@Component({
  selector: 'app-evenements-test',
  imports: [RouterLink],
  template: `
    <main class="evenements-test">
      <a routerLink="/">← Retour au site</a>
      <h1>Événements Kafka (test)</h1>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="vide" role="status">Page de test indisponible dans cet environnement ou pour le moment.</p>
        }
        @case ('affiche') {
          <div class="actions">
            <button type="button" (click)="charger()">Actualiser</button>
            <button type="button" class="secondaire" (click)="envoyerMessageInvalide()">Envoyer un message invalide</button>
          </div>
          @if (message()) {
            <p class="succes" role="status">{{ message() }}</p>
          }
          @if (erreur()) {
            <p class="erreur" role="alert">{{ erreur() }}</p>
          }

          <h2>Boîte d’envoi</h2>
          @if (donnees()!.aDistribuer.length === 0) {
            <p class="vide">Aucun événement enregistré.</p>
          } @else {
            <div class="tableau">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Type</th>
                    <th scope="col">Topic</th>
                    <th scope="col">Clé</th>
                    <th scope="col">Date métier</th>
                    <th scope="col">Publication</th>
                  </tr>
                </thead>
                <tbody data-test="a-distribuer">
                  @for (e of donnees()!.aDistribuer; track e.id) {
                    <tr>
                      <td>{{ e.type }}</td>
                      <td>{{ e.topic }}</td>
                      <td>{{ e.cle }}</td>
                      <td>{{ e.dateMetier }}</td>
                      <td>
                        @if (e.publieLe) {
                          Publié le {{ afficher(e.publieLe) }}
                        } @else {
                          <span class="attente">En attente ({{ e.essais }} essai(s){{ e.derniereErreur ? ' : ' + e.derniereErreur : '' }})</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          <h2>Reçus par le journal back-office</h2>
          @if (donnees()!.recus.length === 0) {
            <p class="vide">Aucun événement reçu.</p>
          } @else {
            <div class="tableau">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Type</th>
                    <th scope="col">Topic</th>
                    <th scope="col">Clé</th>
                    <th scope="col">Date métier</th>
                    <th scope="col">Reçu le</th>
                  </tr>
                </thead>
                <tbody data-test="recus">
                  @for (e of donnees()!.recus; track e.id) {
                    <tr>
                      <td>{{ e.type }}</td>
                      <td>{{ e.topic }}</td>
                      <td>{{ e.cle }}</td>
                      <td>{{ e.dateMetier }}</td>
                      <td>{{ afficher(e.recuLe) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          <h2>Messages en erreur</h2>
          @if (donnees()!.enErreur.length === 0) {
            <p class="vide">Aucun message en erreur.</p>
          } @else {
            <div class="tableau">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Topic</th>
                    <th scope="col">Clé</th>
                    <th scope="col">Groupe</th>
                    <th scope="col">Motif</th>
                    <th scope="col">Essais</th>
                    <th scope="col">Le</th>
                  </tr>
                </thead>
                <tbody data-test="en-erreur">
                  @for (e of donnees()!.enErreur; track $index) {
                    <tr>
                      <td>{{ e.topic }}</td>
                      <td>{{ e.cle ?? '—' }}</td>
                      <td>{{ e.groupe }}</td>
                      <td>{{ e.motif }}</td>
                      <td>{{ e.essais }}</td>
                      <td>{{ afficher(e.enErreurLe) }}</td>
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
    .evenements-test { max-width: 1100px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    h2 { color: #0b2545; margin: 32px 0 12px; font-size: 20px; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    button { padding: 10px 18px; border-radius: 10px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font-weight: 700; cursor: pointer; }
    button.secondaire { background: #fff; color: #0b2545; }
    .vide { color: #5d6887; line-height: 1.6; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
    .attente { color: #b54708; }
    .tableau { overflow-x: auto; border: 1px solid #d9dee6; border-radius: 12px; background: #fff; }
    table { border-collapse: collapse; width: 100%; font-size: 14px; }
    th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eef1f5; }
    th { background: #f4f6fa; white-space: nowrap; }
  `,
})
export class EvenementsTest {
  private readonly service = inject(EvenementsTestService);
  protected readonly etat = signal<Etat>('chargement');
  protected readonly donnees = signal<EtatEvenements | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly afficher = afficherDate;

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.service.lire().subscribe({
      next: (donnees) => {
        this.donnees.set(donnees);
        this.etat.set('affiche');
      },
      error: () => this.etat.set('indisponible'),
    });
  }

  protected envoyerMessageInvalide(): void {
    this.message.set(null);
    this.erreur.set(null);
    this.service.envoyerMessageInvalide().subscribe({
      next: () =>
        this.message.set('Message invalide envoyé : il apparaîtra en erreur après 3 essais (quelques secondes). Actualisez.'),
      error: () => this.erreur.set('Le message n’a pas pu être envoyé. Réessayez.'),
    });
  }
}
