import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { ResultatFiscal } from '../identification/bouchons-identite';

export type StatutDemande = 'EN_COURS_DE_VERIFICATION' | 'OUVERTURE_VALIDEE' | 'LIVRET_A_EXISTANT';

/** Identité à transmettre à l'administration fiscale (date au format AAAA-MM-JJ). */
export interface Transmission {
  nom: string;
  prenoms: string;
  sexe: string;
  dateNaissance: string;
  lieuNaissance: string;
}

// CU-31 : demande d'ouverture de Livret A telle qu'affichée. message : texte à montrer à la cliente.
export interface DemandeLivretA {
  reference: string;
  statut: StatutDemande;
  message: string;
  attestationLe: string;
  partageDetailAutorise: boolean;
  choixPartageLe: string;
  interrogationLe: string | null;
  reponseFiscale: ResultatFiscal | null;
  retard: boolean;
  delai: string;
  transmission: Transmission | null;
}

/** CU-28 : coordonnées modifiables par la cliente (complément d'adresse facultatif). */
export interface Coordonnees {
  voie: string;
  complement: string | null;
  codePostal: string;
  ville: string;
  paysAdresse: string;
  email: string;
  mobile: string;
}

/** CU-28 : identité pré-remplie depuis le dossier client (date au format AAAA-MM-JJ). */
export interface IdentiteLivretA {
  civilite: string;
  nom: string;
  prenoms: string;
  dateNaissance: string;
  coordonnees: Coordonnees;
  residenceFiscale: string;
}

export type StatutControleIdentite = 'EN_COURS' | 'VALIDE' | 'BLOQUE' | 'ATTENTE_RESPONSABLE' | 'EN_ATTENTE';
export type ResultatSanctions = 'AUCUNE_CORRESPONDANCE' | 'CORRESPONDANCE_SANCTIONS' | 'CORRESPONDANCE_PEP' | 'SANS_REPONSE';

/** CU-28 : contrôle d'identité. aControler : identité à envoyer au contrôle sanctions/PPE tant qu'il est en cours. */
export interface ControleIdentite {
  reference: string;
  statut: StatutControleIdentite;
  message: string;
  utilisable: boolean;
  aControler: { nomNaissance: string; prenoms: string; dateNaissance: string; nationalite: string } | null;
}

// CU-31 : routes /livret-a du backend (GET /livret-a/demande, POST /livret-a/demandes,
// POST /livret-a/demandes/{reference}/reponse-fiscale), avec le cookie de session. CU-295 critère 5 :
// POST /test/livret-a/demandes/{reference}/expiration-delai (environnement de test seulement).
@Injectable({ providedIn: 'root' })
export class LivretAService {
  private readonly http = inject(HttpClient);
  private readonly base = apiBase('backend');

  etat(): Observable<{ demande: DemandeLivretA | null }> {
    return this.http.get<{ demande: DemandeLivretA | null }>(`${this.base}/livret-a/demande`, { withCredentials: true });
  }

  demander(attestation: boolean, partageDetailAutorise: boolean): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/livret-a/demandes`,
      { attestation, partageDetailAutorise },
      { withCredentials: true },
    );
  }

  transmettreReponse(reference: string, interrogationId: string | null, resultat: ResultatFiscal): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/livret-a/demandes/${encodeURIComponent(reference)}/reponse-fiscale`,
      { interrogationId, resultat },
      { withCredentials: true },
    );
  }

  /** CU-28 : identité pré-remplie et dernier contrôle d'identité. */
  identite(): Observable<{ identite: IdentiteLivretA; controle: ControleIdentite | null }> {
    return this.http.get<{ identite: IdentiteLivretA; controle: ControleIdentite | null }>(`${this.base}/livret-a/identite`, {
      withCredentials: true,
    });
  }

  confirmerIdentite(coordonnees: Coordonnees, residenceFiscaleConfirmee: boolean): Observable<ControleIdentite> {
    return this.http.post<ControleIdentite>(
      `${this.base}/livret-a/identite`,
      { coordonnees, residenceFiscaleConfirmee },
      { withCredentials: true },
    );
  }

  transmettreControle(reference: string, resultat: ResultatSanctions, tentatives: number): Observable<ControleIdentite> {
    return this.http.post<ControleIdentite>(
      `${this.base}/livret-a/identite/${encodeURIComponent(reference)}/resultat`,
      { resultat, tentatives },
      { withCredentials: true },
    );
  }

  /** CU-28 critère 9 (environnement de test) : un responsable valide une personne politiquement exposée. */
  simulerValidationResponsable(reference: string): Observable<ControleIdentite> {
    return this.http.post<ControleIdentite>(
      `${this.base}/test/livret-a/identite/${encodeURIComponent(reference)}/validation-responsable`,
      {},
      { withCredentials: true },
    );
  }

  simulerExpirationDelai(reference: string): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/test/livret-a/demandes/${encodeURIComponent(reference)}/expiration-delai`,
      {},
      { withCredentials: true },
    );
  }
}
