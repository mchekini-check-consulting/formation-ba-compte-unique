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

/** CU-89 : réponse du bouchon Docusign (codes de test 111111, 000000, 999999). */
export type ResultatDocusign =
  | 'ENVELOPPE_ENVOYEE'
  | 'SIGNATURE_VALIDE'
  | 'CODE_FAUX'
  | 'CODE_EXPIRE'
  | 'ANNULATION_DEMANDEE';

export interface ReponseDocusign {
  enveloppeId: string;
  resultat: ResultatDocusign;
  message: string;
}

/** CU-295 : cas de test du bouchon de l'administration fiscale, choisis dans le panneau de test du Livret A. */
export type CasFiscal = 'SANS_LIVRET_A' | 'DETENU_AILLEURS' | 'SANS_REPONSE' | 'INDISPONIBLE';
export type ResultatFiscal = 'AUCUN_LIVRET_A' | 'LIVRET_A_EXISTANT' | 'SANS_REPONSE' | 'INDISPONIBLE';

/** CU-31 : informations transmises à l'administration fiscale (date au format AAAA-MM-JJ). */
export interface InterrogationFiscale {
  demandeId: string;
  nom: string;
  prenoms: string;
  sexe: string;
  dateNaissance: string;
  lieuNaissance: string;
  partageDetailAutorise: boolean;
  casDeTest?: CasFiscal;
}

export interface ReponseFiscale {
  interrogationId: string;
  resultat: ResultatFiscal;
  /** Seulement pour LIVRET_A_EXISTANT, si la cliente a autorisé le partage du détail. */
  detail: { etablissement: string; agence: string; dateOuverture: string } | null;
  message: string;
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

  /** CU-89 : enveloppe de signature créée et code SMS « envoyé » (rien n'est réellement envoyé). */
  envoyerEnveloppe(dossierId: string): Observable<ReponseDocusign> {
    return this.http.post<ReponseDocusign>(`${this.base}/docusign/enveloppes`, { dossierId });
  }

  saisirCode(enveloppeId: string, code: string): Observable<ReponseDocusign> {
    return this.http.post<ReponseDocusign>(
      `${this.base}/docusign/enveloppes/${encodeURIComponent(enveloppeId)}/code`,
      { code },
    );
  }

  annulerSignature(enveloppeId: string): Observable<ReponseDocusign> {
    return this.http.post<ReponseDocusign>(
      `${this.base}/docusign/enveloppes/${encodeURIComponent(enveloppeId)}/annulation`,
      {},
    );
  }

  /** CU-295 : interrogation de l'administration fiscale sur la détention d'un Livret A (bouchon). */
  interrogerAdministrationFiscale(interrogation: InterrogationFiscale): Observable<ReponseFiscale> {
    return this.http.post<ReponseFiscale>(`${this.base}/administration-fiscale/interrogations`, interrogation);
  }

  /** selfie : code d'un selfie de test du bouchon (aucune image n'est envoyée). */
  verifierSelfie(dossierId: string, selfie: string): Observable<VerificationSelfieReponse> {
    return this.http.post<VerificationSelfieReponse>(
      `${this.base}/stripe-identity/verifications-selfie`,
      { dossierId, selfie },
    );
  }
}
