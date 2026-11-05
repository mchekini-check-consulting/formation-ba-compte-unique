import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccesLayout } from '../acces/acces-layout';
import { ReinitialisationService } from './reinitialisation';

const MESSAGE_INDISPONIBLE = 'Demande impossible pour le moment, réessayez plus tard.';

// CU-46 (critères 1 à 3 et 6) : la cliente saisit son identifiant et reçoit un lien par SMS. Le
// message est le même, qu'un compte existe ou non.
@Component({
  selector: 'app-mot-de-passe-oublie',
  imports: [AccesLayout, RouterLink],
  template: `
    <app-acces-layout
      titre="Mot de passe oublié ? Retrouvez l'accès à votre espace."
      texte="Indiquez votre identifiant de connexion : nous vous envoyons par SMS un lien pour choisir un nouveau mot de passe."
      [points]="points"
      retour="/connexion"
      libelleRetour="← Retour à la connexion"
    >
      <h2>Mot de passe oublié</h2>
      @if (message()) {
        <div role="status" class="msg info"><p>{{ message() }}</p></div>
        <a class="btn" routerLink="/connexion">Retour à la connexion</a>
      } @else {
        <p class="sub">Saisissez l'identifiant de votre espace client.</p>
        @if (erreur()) {
          <div role="alert" class="msg err"><p>{{ erreur() }}</p></div>
        }
        <form (submit)="demander($event, identifiant)">
          <div class="field">
            <label for="identifiant">Identifiant de connexion</label>
            <div class="input">
              <input #identifiant id="identifiant" name="identifiant" autocomplete="username" placeholder="8 chiffres"
                aria-describedby="identifiant-aide" required />
            </div>
            <span class="hint" id="identifiant-aide">8 chiffres, envoyés par e-mail à l'ouverture de votre compte.</span>
          </div>
          <button class="btn" type="submit" [disabled]="enCours()">Recevoir un lien par SMS</button>
        </form>
        <div class="row">
          <a routerLink="/connexion">Je me souviens de mon mot de passe</a>
        </div>
      }
    </app-acces-layout>
  `,
})
export class MotDePasseOublie {
  private readonly api = inject(ReinitialisationService);

  protected readonly points: [string, string][] = [
    ['Lien par SMS', 'envoyé au numéro de mobile de votre espace.'],
    ['Valable 24 heures', "un nouveau lien remplace le précédent."],
    ['Tout en ligne', 'sans appeler votre conseiller.'],
  ];
  protected readonly message = signal('');
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);

  protected demander(event: Event, identifiant: HTMLInputElement): void {
    event.preventDefault();
    this.erreur.set('');
    this.enCours.set(true);
    this.api.demander(identifiant.value.trim()).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.message.set(reponse.message);
      },
      error: () => {
        this.enCours.set(false);
        this.erreur.set(MESSAGE_INDISPONIBLE);
      },
    });
  }
}
