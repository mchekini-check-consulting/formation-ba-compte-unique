import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { motifDeFermeture } from './motifs';

/** Demandes de connexion et de session : leur 401 est traité par l'écran qui les envoie. */
const CONNEXION = /\/connexion(\/|$|\?)/;

// CU-169 et CU-170 : toute réponse 401 du backend (session absente, expirée ou révoquée) renvoie
// vers la connexion, avec le motif qui fait afficher « Session expirée » quand il y a lieu. Les
// pages n'ont donc rien à faire pour un 401 ; seules la connexion et la lecture de session
// (gardes et suivi de session, CU-17 et CU-18) gèrent leur 401 elles-mêmes.
export const sessionFermee: HttpInterceptorFn = (requete, suivant) => {
  const router = inject(Router);
  return suivant(requete).pipe(
    tap({
      error: (erreur: unknown) => {
        if (erreur instanceof HttpErrorResponse && erreur.status === 401 && !CONNEXION.test(requete.url)) {
          const motif = motifDeFermeture(erreur);
          void router.navigate(['/connexion'], motif ? { queryParams: { motif } } : {});
        }
      },
    }),
  );
};
