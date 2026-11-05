import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AccesLayout } from '../acces/acces-layout';
import { SaisieMotDePasse, SaisieMotDePasseComponent } from '../mot-de-passe/saisie-mot-de-passe';
import { ActivationService, EtatActivation, Refus } from './activation';

const MESSAGE_INDISPONIBLE = 'Activation impossible pour le moment, réessayez plus tard.';

type Etat = 'chargement' | EtatActivation | 'INDISPONIBLE';

// Page « Activer mon espace » (CU-14), ouverte depuis le lien reçu par SMS : la cliente y
// définit son mot de passe, saisi deux fois (complément de l'US-09). Le lien sert uniquement à
// cela : il ne connecte pas. Les règles et la confirmation sont aussi contrôlées par le serveur,
// avec les mêmes messages.
@Component({
  selector: 'app-activation-page',
  imports: [AccesLayout, RouterLink, SaisieMotDePasseComponent],
  template: `
    <app-acces-layout
      titre="Activez votre espace en choisissant votre mot de passe."
      texte="Une seule étape avant votre première connexion. Vous vous connecterez ensuite avec l'identifiant à 8 chiffres reçu par e-mail."
      [points]="points"
    >
      @switch (etat()) {
        @case ('chargement') {
          <h2>Activer mon espace</h2>
          <p class="sub">Vérification du lien…</p>
        }
        @case ('VALIDE') {
          <h2>Activer mon espace</h2>
          <p class="sub">Choisissez le mot de passe de votre espace.</p>
          <app-saisie-mot-de-passe
            libelle="Activer mon espace"
            [enCours]="enCours()"
            [erreurServeur]="erreur()"
            (valider)="activer($event)"
          />
        }
        @case ('ACTIVE') {
          <h2>Votre espace est activé</h2>
          <p class="sub">Vous pouvez maintenant vous connecter avec votre identifiant et votre mot de passe.</p>
          <a class="btn bouton" routerLink="/connexion">Me connecter</a>
        }
        @case ('DEJA_ACTIVE') {
          <h2>Votre espace est déjà activé</h2>
          <p class="sub">Connectez-vous avec votre identifiant et votre mot de passe.</p>
          <a class="btn bouton" routerLink="/connexion">Me connecter</a>
        }
        @case ('EXPIRE') {
          <h2>Lien expiré</h2>
          <p class="sub">Ce lien d'activation n'est plus valable : il a plus de 24 heures, a déjà servi ou a été remplacé par un nouveau lien.</p>
          @if (nouveauLien()) {
            <div role="status" class="msg info"><p>{{ nouveauLien() }}</p></div>
          } @else {
            <button class="btn" type="button" [disabled]="enCours()" (click)="demanderNouveauLien()">Recevoir un nouveau lien</button>
          }
        }
        @default {
          <h2>Activer mon espace</h2>
          <div role="alert" class="msg err"><p>{{ messageIndisponible }}</p></div>
        }
      }
    </app-acces-layout>
  `,
})
export class ActivationPage {
  private readonly api = inject(ActivationService);
  private readonly jeton: string = inject(ActivatedRoute).snapshot.queryParamMap.get('jeton') ?? '';

  protected readonly messageIndisponible = MESSAGE_INDISPONIBLE;
  protected readonly points: [string, string][] = [
    ['Lien personnel', 'reçu par SMS, valable 24 heures.'],
    ['Mot de passe robuste', 'au moins 12 caractères, dont une majuscule, une minuscule, un chiffre et un caractère spécial.'],
    ['Vos données protégées', 'vous seule accédez à vos comptes.'],
  ];
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

  protected activer({ motDePasse, confirmation }: SaisieMotDePasse): void {
    this.erreur.set('');
    this.enCours.set(true);
    this.api.activer(this.jeton, motDePasse, confirmation).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.etat.set(reponse.etat);
      },
      error: (e: HttpErrorResponse) => {
        this.enCours.set(false);
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
