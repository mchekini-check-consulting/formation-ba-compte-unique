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

/**
 * CU-27 critère 7 : page où revenir après la connexion (paramètre « suite »). Seule une page du
 * site est acceptée (chemin commençant par un seul « / ») : jamais une adresse extérieure.
 */
export function destinationApresConnexion(suite: string | null): string {
  const pageDuSite = !!suite && /^\/(?![/\\])/.test(suite) && !suite.startsWith('/connexion');
  return pageDuSite ? suite : '/espace';
}

/**
 * Pages protégées : sans session, retour à la connexion (« Session expirée » si elle a expiré).
 * CU-27 : la page demandée (autre que l'accueil de l'espace) est transmise à la connexion, qui y
 * ramène la cliente une fois connectée.
 */
export const espaceProtege: CanActivateFn = (_route, etat) => {
  const router = inject(Router);
  const suite = etat.url === '/espace' ? {} : { suite: etat.url };
  return inject(ConnexionService)
    .session()
    .pipe(
      map(() => true),
      catchError((erreur: HttpErrorResponse) => {
        const expiree = erreur.status === 401 && erreur.error?.motif === 'EXPIREE';
        const queryParams = { ...(expiree ? { motif: MOTIF_EXPIREE } : {}), ...suite };
        return of(router.createUrlTree(['/connexion'], { queryParams }));
      }),
    );
};

/** CU-18 critère 6 : déjà connectée, la page de connexion renvoie vers l'espace (CU-27 : ou vers la page demandée). */
export const connexionPublique: CanActivateFn = (route) => {
  const router = inject(Router);
  const destination = destinationApresConnexion(route.queryParamMap.get('suite'));
  return inject(ConnexionService)
    .session()
    .pipe(
      map(() => router.parseUrl(destination)),
      catchError(() => of(true)),
    );
};
