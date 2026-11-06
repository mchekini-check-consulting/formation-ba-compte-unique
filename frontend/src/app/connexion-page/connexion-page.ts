import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccesLayout } from '../acces/acces-layout';
import { ConnexionService, RefusConnexion } from '../connexion';
import { MOTIF_EXPIREE, MOTIF_SESSION_EXPIREE } from '../session/suivi-session';

const MESSAGE_INDISPONIBLE = 'Connexion impossible pour le moment, réessayez plus tard.';
const STATUTS_REFUS = [401, 429, 503];

/** CU-171 : « Réessayez dans 12 minutes. » à partir de la durée restante du blocage, en secondes. */
export function reessayerDans(secondes: number): string {
  const minutes = Math.max(1, Math.ceil(secondes / 60));
  return `Réessayez dans ${minutes} ${minutes > 1 ? 'minutes' : 'minute'}.`;
}

/**
 * Message affiché pour un refus du backend. CU-171 : pendant un blocage (429), la consigne
 * « Réessayez ... » du message est calculée à partir de la durée restante renvoyée par le backend.
 */
export function messageDeRefus(refus: RefusConnexion): string {
  const secondes = refus.reessayerDansSecondes;
  if (typeof secondes !== 'number' || !Number.isFinite(secondes) || secondes <= 0) {
    return refus.message;
  }
  const debut = refus.message.replace(/\s*Réessayez\b.*$/, '');
  return `${debut} ${reessayerDans(secondes)}`.trim();
}

// Écran « Me connecter » (CU-15), d'après la maquette « Connexion Hi-Tech Bank ».
@Component({
  selector: 'app-connexion-page',
  imports: [AccesLayout, RouterLink],
  templateUrl: './connexion-page.html',
})
export class ConnexionPage {
  private readonly api = inject(ConnexionService);
  private readonly router = inject(Router);

  protected readonly erreur = signal('');
  protected readonly invitation = signal('');
  protected readonly enCours = signal(false);
  protected readonly visible = signal(false);
  private readonly motif = inject(ActivatedRoute).snapshot.queryParamMap.get('motif');
  /** CU-17 : arrivée après 10 minutes sans activité dans l'espace. */
  protected readonly sessionExpiree = this.motif === MOTIF_EXPIREE;
  /** CU-169 : session refusée par le serveur (expirée ou révoquée) pendant la navigation. */
  protected readonly sessionFermee = this.motif === MOTIF_SESSION_EXPIREE;
  protected readonly points: [string, string][] = [
    ['Connexion simple', 'identifiant et mot de passe, sans code à recevoir.'],
    ['Session sécurisée', "fermeture automatique après 10 minutes d'inactivité."],
    ['Vos données protégées', 'vous seule accédez à vos comptes.'],
  ];

  protected connecter(event: Event, identifiant: HTMLInputElement, motDePasse: HTMLInputElement): void {
    event.preventDefault();
    this.erreur.set('');
    this.invitation.set('');
    this.enCours.set(true);
    this.api.connecter(identifiant.value.trim(), motDePasse.value).subscribe({
      next: () => void this.router.navigateByUrl('/espace'),
      error: (e: HttpErrorResponse) => {
        this.enCours.set(false);
        motDePasse.value = '';
        // 401 : identifiants refusés ; 429 : trop de tentatives (CU-16, CU-48) ; 503 : journal de
        // sécurité indisponible (CU-48). Le message vient du backend, texte exact des tickets.
        const refus: RefusConnexion | null = STATUTS_REFUS.includes(e.status) && e.error?.message ? e.error : null;
        this.erreur.set(refus ? messageDeRefus(refus) : MESSAGE_INDISPONIBLE);
        this.invitation.set(refus?.invitation ?? '');
      },
    });
  }
}
