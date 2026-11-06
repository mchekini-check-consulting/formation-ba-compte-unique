import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { ConnexionService } from '../connexion';
import { MOTIF_EXPIREE } from './suivi-session';

// CU-18 : protection des pages de l'espace (accueil, compte courant, épargne, crédits, patrimoine,
// optimisation, simulateurs, documents, informations et sécurité : sections de /espace ; mes comptes).
// La session est vérifiée auprès du backend AVANT d'afficher la page : sans session valide, rien
// n'apparaît, même brièvement. La lecture ne prolonge pas la session (CU-17). Le backend refuse
// de toute façon chaque demande de données sans session : cette garde ne fait que l'affichage.

/** Pages protégées : sans session, retour à la connexion (« Session expirée » si elle a expiré). */
export const espaceProtege: CanActivateFn = () => {
  const router = inject(Router);
  return inject(ConnexionService)
    .session()
    .pipe(
      map(() => true),
      catchError((erreur: HttpErrorResponse) => {
        const expiree = erreur.status === 401 && erreur.error?.motif === 'EXPIREE';
        return of(router.createUrlTree(['/connexion'], expiree ? { queryParams: { motif: MOTIF_EXPIREE } } : {}));
      }),
    );
};

/** CU-18 critère 6 : déjà connectée, la page de connexion renvoie vers l'espace. */
export const connexionPublique: CanActivateFn = () => {
  const router = inject(Router);
  return inject(ConnexionService)
    .session()
    .pipe(
      map(() => router.createUrlTree(['/espace'])),
      catchError(() => of(true)),
    );
};
