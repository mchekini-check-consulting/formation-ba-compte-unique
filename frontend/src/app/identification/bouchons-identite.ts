import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { apiBase } from '../api';

// Appels au backend bouchons-identite (bouchons des partenaires d'identification :
// contrôle sanctions/PEP, Stripe Identity pièce et selfie). Voir bouchons-identite/README.md.

/** Ce que le parcours doit faire après la réponse d'un bouchon. */
export type Suite = 'POURSUIVRE' | 'REPRENDRE' | 'REESSAYER' | 'REESSAYER_PLUS_TARD' | 'ANNULER';
export type StatutDossier = 'EN_COURS_DE_VERIFICATION' | 'ANNULE';

/** Cas de test du bouchon US-B7 (CUB-70), choisis dans le panneau de test de « Mon identité ». */
export type CasDeTest = 'CONFORME' | 'SANCTIONNE' | 'PEP' | 'INDISPONIBLE';

/** Dates au format AAAA-MM-JJ, comme les attend le backend. */
export interface ControleSanctionsRequete {
  nomNaissance: string;
  prenoms: string;
  dateNaissance: string;
  nationalite: string;
  casDeTest?: CasDeTest;
}

export interface ControleSanctionsReponse {
  resultat: 'AUCUNE_CORRESPONDANCE' | 'CORRESPONDANCE_SANCTIONS' | 'CORRESPONDANCE_PEP';
  suite: Suite;
  statutDossier: StatutDossier;
  message: string | null;
}

export interface SaisieIdentite {
  nomNaissance: string;
  prenoms: string;
  dateNaissance: string;
  dateExpiration: string;
}

export interface VerificationDocumentReponse {
  statut: 'VERIFIE' | 'REJETE' | 'A_REPRENDRE' | 'ERREUR_TECHNIQUE' | 'INDISPONIBLE';
  motif: string | null;
  suite: Suite;
  statutDossier: StatutDossier;
  message: string | null;
}

export interface VerificationSelfieReponse {
  resultat:
    | 'CORRESPONDANCE'
    | 'NON_CORRESPONDANCE'
    | 'ECHEC_VERIFICATION_PRESENCE'
    | 'INDISPONIBILITE_TECHNIQUE';
  score: number | null;
  suite: Suite;
  statutDossier: StatutDossier;
  message: string | null;
}

/** CUB-8 : sans réponse des listes sanctions/PEP sous 5 secondes, la vérification est impossible. */
export const DELAI_SANCTIONS_MS = 5000;

@Injectable({ providedIn: 'root' })
export class BouchonsIdentiteService {
  private readonly http = inject(HttpClient);
  private readonly base = apiBase('bouchons-identite');

  controlerSanctions(identite: ControleSanctionsRequete): Observable<ControleSanctionsReponse> {
    return this.http
      .post<ControleSanctionsReponse>(`${this.base}/sanctions-pep/controles`, identite)
      .pipe(timeout(DELAI_SANCTIONS_MS));
  }

  /** piece : code d'une pièce de test du bouchon (aucune image n'est envoyée). */
  verifierDocument(
    dossierId: string,
    piece: string,
    saisie: SaisieIdentite,
  ): Observable<VerificationDocumentReponse> {
    return this.http.post<VerificationDocumentReponse>(
      `${this.base}/stripe-identity/verifications-document`,
      { dossierId, recto: piece, verso: piece, saisie },
    );
  }

  /** selfie : code d'un selfie de test du bouchon (aucune image n'est envoyée). */
  verifierSelfie(dossierId: string, selfie: string): Observable<VerificationSelfieReponse> {
    return this.http.post<VerificationSelfieReponse>(
      `${this.base}/stripe-identity/verifications-selfie`,
      { dossierId, selfie },
    );
  }
}
