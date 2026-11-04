import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { apiBase } from '../api';

export interface MessageCapture {
  canal: 'EMAIL' | 'SMS';
  destinataire: string;
  objet: string | null;
  texte: string;
  /** Premier lien du texte (lien d'activation du SMS). */
  lien: string | null;
  envoyeLe: string;
}

type Etat = 'chargement' | 'affiche' | 'aucun';

// CU-87 : pages /test/emails et /test/sms. En environnement de test, rien ne quitte
// l'environnement : le dernier e-mail ou SMS « envoyé » est affiché ici (GET /test/emails ou
// /test/sms du backend). Le lien d'activation du SMS est cliquable.
@Component({
  selector: 'app-messages-test',
  imports: [RouterLink],
  template: `
    <main class="messages-test">
      <a routerLink="/">← Retour au site</a>
      <h1>{{ titre }}</h1>
      <p class="muted">Environnement de test : aucun message ne quitte l'environnement. Le dernier message envoyé remplace le précédent.</p>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('aucun') {
          <p class="vide">Aucun message pour le moment.</p>
        }
        @case ('affiche') {
          @if (message(); as m) {
            <article class="message">
              <dl>
                <dt>Destinataire</dt><dd>{{ m.destinataire }}</dd>
                <dt>Envoyé le</dt><dd>{{ m.envoyeLe }}</dd>
                @if (m.objet) {
                  <dt>Objet</dt><dd>{{ m.objet }}</dd>
                }
              </dl>
              <pre>{{ m.texte }}</pre>
              @if (m.lien) {
                <a class="lien" [href]="m.lien">Ouvrir le lien du message</a>
              }
            </article>
          }
        }
      }
      <button type="button" (click)="charger()">Actualiser</button>
    </main>
  `,
  styles: `
    .messages-test { max-width: 760px; margin: 0 auto; padding: 40px 20px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    .muted, .vide { color: #5d6887; line-height: 1.6; }
    .message { border: 1px solid #d9dee6; border-radius: 12px; padding: 18px; margin: 18px 0; background: #fff; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; margin: 0 0 12px; }
    dt { font-weight: 700; }
    dd { margin: 0; overflow-wrap: anywhere; }
    pre { white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; background: #f4f6fa; padding: 12px; border-radius: 8px; }
    .lien { display: inline-block; margin-top: 8px; }
    button { margin-top: 12px; padding: 10px 18px; border-radius: 10px; border: 1px solid #0b2545; background: #fff; color: #0b2545; font-weight: 700; cursor: pointer; }
  `,
})
export class MessagesTest {
  private readonly http = inject(HttpClient);
  private readonly canal: 'emails' | 'sms' = inject(ActivatedRoute).snapshot.data['canal'];
  protected readonly titre = this.canal === 'sms' ? 'SMS de test' : 'E-mails de test';
  protected readonly etat = signal<Etat>('chargement');
  protected readonly message = signal<MessageCapture | null>(null);

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.http.get<MessageCapture>(`${apiBase('backend')}/test/${this.canal}`).subscribe({
      next: (message) => {
        this.message.set(message);
        this.etat.set('affiche');
      },
      error: () => {
        this.message.set(null);
        this.etat.set('aucun');
      },
    });
  }
}
