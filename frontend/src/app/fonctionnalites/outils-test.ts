import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { FonctionnalitesService } from './fonctionnalites';

/**
 * D4 (PO, 27/11/2026) : les pages /test/... ne s'ouvrent que si l'interrupteur « Outils de test » est activé (dev et
 * qualif) ; sinon (preprod, prod, ou sans réponse du backend), retour à l'accueil du site.
 */
export const outilsDeTest: CanActivateFn = () => {
  const router = inject(Router);
  return inject(FonctionnalitesService)
    .etats()
    .pipe(map((etats) => (etats['outils-test'] ? true : router.parseUrl('/'))));
};
