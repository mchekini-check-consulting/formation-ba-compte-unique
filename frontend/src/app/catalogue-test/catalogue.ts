import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** Un fichier catalogue déposé (CU-381). */
export interface FichierDepose {
  nomFichier: string;
  dateFichier: string;
  deposeLe: string;
}

export interface LigneRejetee {
  numeroLigne: number;
  codeIsin: string | null;
  motif: string;
}

/** Une exécution du batch du catalogue. */
export interface JournalBatch {
  dateMetier: string;
  declenchement: 'AUTOMATIQUE' | 'MANUEL';
  nomFichier: string;
  debut: string;
  fin: string;
  ajoutes: number;
  modifies: number;
  retires: number;
  rejetees: number;
  statut: 'TERMINE' | 'ECHEC';
  message: string | null;
  lignesRejetees: LigneRejetee[];
}

/** Une valeur liquidative de l'historique d'un support (CU-383). */
export interface ValeurLiquidative {
  dateVl: string;
  valeur: number;
  source: string;
}

/** CU-417 : simulation de la liste des supports indisponible (test seulement). */
export interface SimulationSupports {
  indisponible: boolean;
}

// CU-381 (TECH-13) : routes /test/catalogue du backend (absentes en preprod et prod : 404).
@Injectable({ providedIn: 'root' })
export class CatalogueTestService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/test/catalogue`;

  deposer(nomFichier: string, contenu: string): Observable<FichierDepose> {
    return this.http.post<FichierDepose>(`${this.url}/fichiers`, { nomFichier, contenu });
  }

  fichiers(): Observable<FichierDepose[]> {
    return this.http.get<FichierDepose[]>(`${this.url}/fichiers`);
  }

  lancer(): Observable<JournalBatch> {
    return this.http.post<JournalBatch>(`${this.url}/batch`, {});
  }

  journal(): Observable<JournalBatch[]> {
    return this.http.get<JournalBatch[]>(`${this.url}/journal`);
  }

  historique(codeIsin: string): Observable<ValeurLiquidative[]> {
    return this.http.get<ValeurLiquidative[]>(
      `${this.url}/supports/${encodeURIComponent(codeIsin)}/valeurs-liquidatives`,
    );
  }

  /** CU-417 : la liste des supports (/supports) est-elle simulée indisponible ? */
  simulationSupports(): Observable<SimulationSupports> {
    return this.http.get<SimulationSupports>(`${this.url}/simulation-supports`);
  }

  simulerSupports(indisponible: boolean): Observable<SimulationSupports> {
    return this.http.put<SimulationSupports>(`${this.url}/simulation-supports`, { indisponible });
  }

  vider(): Observable<void> {
    return this.http.delete<void>(`${this.url}/supports`);
  }
}
