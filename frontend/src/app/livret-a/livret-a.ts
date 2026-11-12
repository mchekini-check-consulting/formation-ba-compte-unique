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

  simulerExpirationDelai(reference: string): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/test/livret-a/demandes/${encodeURIComponent(reference)}/expiration-delai`,
      {},
      { withCredentials: true },
    );
  }
}
