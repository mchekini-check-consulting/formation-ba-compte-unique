import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, shareReplay } from 'rxjs';
import { apiBase } from '../api';

/** Une fonctionnalité portée par un bouton : sa page (une fois développée) et sa phrase « Bientôt ». */
export interface Fonctionnalite {
  /** Page de la fonctionnalité ; absente tant qu'elle n'est pas développée (le bouton reste « Bientôt »). */
  lien?: string;
  phrase: string;
}

/**
 * Lot 3 (PO, 27/11/2026) : fonctionnalités portées par un bouton, avec leur interrupteur (helm.yaml,
 * FONCTIONNALITE_*). Livrer une fonctionnalité = ajouter sa page (lien) et passer son interrupteur à true ; l'écran
 * qui porte le bouton ne change pas. Un bouton n'est « Bientôt » que si sa fonctionnalité n'existe pas (rapprochement
 * du lot 0, décisions D2 et D3).
 */
export const FONCTIONNALITES = {
  messagerie: { phrase: 'Échangez avec votre banque depuis votre espace : disponible prochainement.' },
  'ajout-compte-externe': { phrase: 'Ajoutez le compte d’une autre banque, à votre nom : disponible prochainement.' },
  'virement-programme': { phrase: 'Programmez une épargne automatique : disponible prochainement.' },
  'cloture-compte': { phrase: 'Demandez la clôture de votre compte en ligne : disponible prochainement.' },
  ldds: { phrase: 'Ouvrez votre LDDS en ligne : disponible prochainement.' },
  cel: { phrase: 'Ouvrez votre CEL en ligne : disponible prochainement.' },
  'simulateur-credit': { phrase: 'Simulez votre crédit avant de vous engager : disponible prochainement.' },
  'demande-credit-conso': { phrase: 'Faites votre demande de crédit en ligne : disponible prochainement.' },
  'demande-credit-immo': { phrase: 'Faites votre demande de crédit immobilier en ligne : disponible prochainement.' },
  'demande-rachat-credits': { phrase: 'Faites votre demande de rachat de crédits en ligne : disponible prochainement.' },
  'simulateur-per': { phrase: 'Simulez votre PER avant de souscrire : disponible prochainement.' },
  'simulateur-av': { phrase: 'Simulez votre assurance vie avant de souscrire : disponible prochainement.' },
  'document-convention': { phrase: 'Consultez votre convention en ligne : disponible prochainement.' },
  'document-releve': { phrase: 'Consultez vos relevés en ligne : disponible prochainement.' },
  'document-attestation-livret-a': { phrase: 'Consultez votre attestation en ligne : disponible prochainement.' },
  'contact-conseiller': { phrase: 'Écrivez à votre conseiller depuis votre espace : disponible prochainement.' },
  'changement-mot-de-passe': { phrase: 'Changez votre mot de passe depuis votre espace : disponible prochainement.' },
  // Lot 4 : actions d'un contrat PER ou assurance vie et action rapide « Verser ».
  'versement-libre': { phrase: 'Versez sur votre PER ou votre assurance vie : disponible prochainement.' },
  'operations-contrat': { phrase: 'Suivez les opérations de votre contrat : disponible prochainement.' },
  'suivi-contrat': { phrase: 'Suivez la valeur, les gains et les frais de votre contrat : disponible prochainement.' },
  // Lot 5 : versements programmés sur un PER ou une assurance vie (PER-13, AV-04), proposés à l'étape du versement.
  'versements-programmes': { phrase: 'Programmez vos versements sur votre contrat : disponible prochainement.' },
  // Lot 6 (D7) : document d'informations par support, sur la fiche d'un support.
  'document-support': { phrase: 'Consultez le document d’informations du support : disponible prochainement.' },
  // D4 : outils de test (dev et qualif), portés par l'entrée « Outils de test » du menu.
  'outils-test': { lien: '/test/fonctionnalites', phrase: 'Outils de test : réservés aux environnements de test.' },
} satisfies Record<string, Fonctionnalite>;

export type CleFonctionnalite = keyof typeof FONCTIONNALITES;

/** État des interrupteurs : true = fonctionnalité livrée dans cet environnement. */
export type EtatsFonctionnalites = Record<string, boolean>;

// Lot 3 : GET /fonctionnalites (sans connexion), lu une fois par chargement de l'application. Sans réponse, toutes
// les fonctionnalités restent « Bientôt » : un bouton ne mène jamais à une page qui n'est pas livrée.
@Injectable({ providedIn: 'root' })
export class FonctionnalitesService {
  private readonly http = inject(HttpClient);
  private readonly etats$ = this.http
    .get<EtatsFonctionnalites>(`${apiBase('backend')}/fonctionnalites`)
    .pipe(catchError(() => of({} as EtatsFonctionnalites)), shareReplay(1));

  etats(): Observable<EtatsFonctionnalites> {
    return this.etats$;
  }
}
