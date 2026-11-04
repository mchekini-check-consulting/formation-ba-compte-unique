import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ActivationService, EtatActivation, Refus } from './activation';

export const MESSAGE_TROP_COURT = 'Votre mot de passe doit contenir au moins 12 caractères';
const MESSAGE_INDISPONIBLE = 'Activation impossible pour le moment, réessayez plus tard.';

type Etat = 'chargement' | EtatActivation | 'INDISPONIBLE';

// Page « Activer mon espace » (CU-14), ouverte depuis le lien reçu par SMS : la cliente y
// définit son mot de passe. Le lien sert uniquement à cela : il ne connecte pas. Les règles du
// mot de passe sont aussi contrôlées par le serveur, avec les mêmes messages.
@Component({
  selector: 'app-activation-page',
  imports: [RouterLink],
  template: `
    <main class="activation">
      <a routerLink="/">← Retour au site</a>
      @switch (etat()) {
        @case ('chargement') {
          <h1>Activer mon espace</h1>
          <p>Vérification du lien…</p>
        }
        @case ('VALIDE') {
          <h1>Activer mon espace</h1>
          <p class="muted">Choisissez le mot de passe de votre espace. Vous vous connecterez ensuite avec l'identifiant à 8 chiffres reçu par e-mail.</p>
          <form (submit)="activer($event, motDePasse)">
            <label>
              Mot de passe
              <input #motDePasse name="motDePasse" type="password" autocomplete="new-password" required />
            </label>
            <ul class="regles">
              <li>Au moins 12 caractères, dont une majuscule, une minuscule, un chiffre et un caractère spécial</li>
              <li>Pas un mot de passe courant</li>
              <li>Ni votre adresse e-mail, ni votre nom</li>
            </ul>
            @if (erreur()) {
              <p role="alert" class="erreur">{{ erreur() }}</p>
            }
            <button type="submit" [disabled]="enCours()">Activer mon espace</button>
          </form>
        }
        @case ('ACTIVE') {
          <h1>Votre espace est activé</h1>
          <p>Vous pouvez maintenant vous connecter avec votre identifiant et votre mot de passe.</p>
          <a class="bouton" routerLink="/connexion">Me connecter</a>
        }
        @case ('DEJA_ACTIVE') {
          <h1>Votre espace est déjà activé</h1>
          <a class="bouton" routerLink="/connexion">Me connecter</a>
        }
        @case ('EXPIRE') {
          <h1>Lien expiré</h1>
          <p class="muted">Ce lien d'activation n'est plus valable : il a plus de 24 heures, a déjà servi ou a été remplacé par un nouveau lien.</p>
          @if (nouveauLien()) {
            <p role="status" class="info">{{ nouveauLien() }}</p>
          } @else {
            <button type="button" [disabled]="enCours()" (click)="demanderNouveauLien()">Recevoir un nouveau lien</button>
          }
        }
        @default {
          <h1>Activer mon espace</h1>
          <p role="alert" class="erreur">{{ messageIndisponible }}</p>
        }
      }
    </main>
  `,
  styles: `
    .activation { max-width: 460px; margin: 0 auto; padding: 40px 20px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    .muted { color: #5d6887; line-height: 1.6; }
    form { display: flex; flex-direction: column; gap: 14px; }
    label { display: flex; flex-direction: column; gap: 6px; font-weight: 600; }
    input { font: inherit; padding: 12px; border: 1px solid #c9d1de; border-radius: 10px; }
    .regles { margin: 0; padding-left: 18px; color: #5d6887; font-size: 13.5px; line-height: 1.6; }
    button, .bouton { display: inline-block; text-align: center; text-decoration: none; padding: 12px 18px; border-radius: 10px; border: 0; background: #0b2545; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
    button:disabled { opacity: .5; cursor: not-allowed; }
    .erreur { color: #b42318; background: #fdecea; padding: 10px 12px; border-radius: 10px; margin: 0; }
    .info { color: #0b4f8a; background: #e8f1fb; padding: 10px 12px; border-radius: 10px; }
  `,
})
export class ActivationPage {
  private readonly api = inject(ActivationService);
  private readonly jeton: string = inject(ActivatedRoute).snapshot.queryParamMap.get('jeton') ?? '';

  protected readonly messageIndisponible = MESSAGE_INDISPONIBLE;
  protected readonly etat = signal<Etat>('chargement');
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);
  protected readonly nouveauLien = signal('');

  constructor() {
    if (!this.jeton) {
      this.etat.set('EXPIRE');
      return;
    }
    this.api.etat(this.jeton).subscribe({
      next: (reponse) => this.etat.set(reponse.etat),
      error: () => this.etat.set('INDISPONIBLE'),
    });
  }

  protected activer(event: Event, motDePasse: HTMLInputElement): void {
    event.preventDefault();
    this.erreur.set('');
    if (motDePasse.value.length < 12) {
      this.erreur.set(MESSAGE_TROP_COURT);
      return;
    }
    this.enCours.set(true);
    this.api.activer(this.jeton, motDePasse.value).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.etat.set(reponse.etat);
      },
      error: (e: HttpErrorResponse) => {
        this.enCours.set(false);
        motDePasse.value = '';
        if (e.status === 410) {
          this.etat.set('EXPIRE');
        } else if (e.status === 409) {
          this.etat.set('DEJA_ACTIVE');
        } else {
          this.erreur.set(e.status === 400 ? ((e.error as Refus | null)?.message ?? MESSAGE_INDISPONIBLE) : MESSAGE_INDISPONIBLE);
        }
      },
    });
  }

  protected demanderNouveauLien(): void {
    this.enCours.set(true);
    this.api.nouveauLien(this.jeton).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.nouveauLien.set(reponse.message);
      },
      error: () => {
        this.enCours.set(false);
        this.nouveauLien.set(MESSAGE_INDISPONIBLE);
      },
    });
  }
}
