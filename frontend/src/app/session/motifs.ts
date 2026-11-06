import { HttpErrorResponse } from '@angular/common/http';

/** CU-17 (US-12) : session fermée après 10 minutes sans activité, vue par le compte à rebours. */
export const MOTIF_EXPIREE = 'expiree';
/** CU-169 : session refusée par le serveur (expirée ou révoquée) avant la fin du compte à rebours. */
export const MOTIF_SESSION_EXPIREE = 'session_expiree';

/**
 * CU-169 : le backend a refusé une demande parce que la session est fermée. Avec le motif EXPIREE
 * (un cookie a été présenté : session expirée ou révoquée), la page de connexion affiche
 * « Session expirée ». Sans cookie (déconnexion volontaire, cookie supprimé), pas de message.
 */
export function motifDeFermeture(erreur: HttpErrorResponse): string | null {
  return erreur.error?.motif === 'EXPIREE' ? MOTIF_SESSION_EXPIREE : null;
}
