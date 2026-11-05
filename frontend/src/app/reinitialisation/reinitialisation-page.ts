import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AccesLayout } from '../acces/acces-layout';
import { SaisieMotDePasse, SaisieMotDePasseComponent } from '../mot-de-passe/saisie-mot-de-passe';
import { EtatReinitialisation, Reponse, ReinitialisationService } from './reinitialisation';

const MESSAGE_INDISPONIBLE = 'Modification impossible pour le moment, réessayez plus tard.';

type Etat = 'chargement' | EtatReinitialisation | 'INDISPONIBLE';

// CU-46 (critères 4 et 5) : page « Définir mon mot de passe », ouverte depuis le lien reçu par
// SMS. Le nouveau mot de passe est saisi deux fois (complément de l'US-09) ; tant que la saisie
// est refusée, le lien reste valable.
@Component({
  selector: 'app-reinitialisation-page',
  imports: [AccesLayout, RouterLink, SaisieMotDePasseComponent],
  template: `
    <app-acces-layout
      titre="Choisissez votre nouveau mot de passe."
      texte="Il remplacera l'ancien. Par sécurité, vous serez ensuite déconnectée de tous vos appareils."
      [points]="points"
      retour="/connexion"
      libelleRetour="← Retour à la connexion"
    >
      @switch (etat()) {
        @case ('chargement') {
          <h2>Définir mon mot de passe</h2>
          <p class="sub">Vérification du lien…</p>
        }
        @case ('VALIDE') {
          <h2>Définir mon mot de passe</h2>
          <p class="sub">Saisissez deux fois votre nouveau mot de passe.</p>
          <app-saisie-mot-de-passe
            libelle="Enregistrer mon mot de passe"
            [enCours]="enCours()"
            [erreurServeur]="erreur()"
            (valider)="enregistrer($event)"
          />
        }
        @case ('REINITIALISE') {
          <h2>Votre mot de passe est modifié</h2>
          <p class="sub">Vous pouvez maintenant vous connecter avec votre identifiant et votre nouveau mot de passe.</p>
          <a class="btn bouton" routerLink="/connexion">Me connecter</a>
        }
        @case ('EXPIRE') {
          <h2>Lien expiré</h2>
          <p class="sub">Ce lien n'est plus valable : il a plus de 24 heures, a déjà servi ou a été remplacé par un nouveau lien.</p>
          <a class="btn bouton" routerLink="/mot-de-passe-oublie">Recevoir un nouveau lien</a>
        }
        @default {
          <h2>Définir mon mot de passe</h2>
          <div role="alert" class="msg err"><p>{{ messageIndisponible }}</p></div>
        }
      }
    </app-acces-layout>
  `,
})
export class ReinitialisationPage {
  private readonly api = inject(ReinitialisationService);
  private readonly jeton: string = inject(ActivatedRoute).snapshot.queryParamMap.get('jeton') ?? '';

  protected readonly messageIndisponible = MESSAGE_INDISPONIBLE;
  protected readonly points: [string, string][] = [
    ['Lien personnel', 'reçu par SMS, valable 24 heures.'],
    ['Mot de passe robuste', 'au moins 12 caractères, dont une majuscule, une minuscule, un chiffre et un caractère spécial.'],
    ['Tous vos appareils déconnectés', "personne d'autre ne garde l'accès avec l'ancien mot de passe."],
  ];
  protected readonly etat = signal<Etat>('chargement');
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);

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

  protected enregistrer({ motDePasse, confirmation }: SaisieMotDePasse): void {
    this.erreur.set('');
    this.enCours.set(true);
    this.api.reinitialiser(this.jeton, motDePasse, confirmation).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.etat.set(reponse.etat);
      },
      error: (e: HttpErrorResponse) => {
        this.enCours.set(false);
        if (e.status === 410) {
          this.etat.set('EXPIRE');
        } else {
          this.erreur.set(e.status === 400 ? ((e.error as Reponse | null)?.message ?? MESSAGE_INDISPONIBLE) : MESSAGE_INDISPONIBLE);
        }
      },
    });
  }
}
