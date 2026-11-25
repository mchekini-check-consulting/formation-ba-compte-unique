import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type TypeOrdre = 'VERSEMENT' | 'RACHAT' | 'ARBITRAGE';

/** Demande d'ordre (CU-376) : l'identifiant est créé par l'écran au moment de la validation. */
export interface DemandeOrdre {
  id: string;
  numeroContrat: string;
  type: TypeOrdre;
  supportDepart: string | null;
  supportArrivee: string | null;
  montant: number;
}

export interface EvenementOrdre {
  id: string;
  statut: 'EN_ATTENTE' | 'PUBLIE';
  publieLe: string | null;
  derniereErreur: string | null;
}

export interface OrdreTest {
  id: string;
  numeroContrat: string;
  type: TypeOrdre;
  supportDepart: string | null;
  supportArrivee: string | null;
  montant: number;
  demandeLe: string;
  dateExecutionPrevue: string;
  statut: 'DEMANDE' | 'EN_COURS' | 'EXECUTE' | 'REJETE' | 'ANNULE';
  montantDisponible: number;
  evenement: EvenementOrdre | null;
}

export interface PositionTest {
  numeroContrat: string;
  codeIsin: string;
  montant: number | null;
  nombreParts: number | null;
  enregistreeLe: string;
}

export interface Disponible {
  numeroContrat: string;
  codeIsin: string;
  disponible: number;
}

// CU-376 (TECH-08) : routes /test/ordres du backend (absentes en preprod et prod : 404).
@Injectable({ providedIn: 'root' })
export class OrdresTestService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/test/ordres`;

  /** Réponse complète : 201 pour un ordre créé, 200 pour un doublon. */
  passer(demande: DemandeOrdre): Observable<HttpResponse<OrdreTest>> {
    return this.http.post<OrdreTest>(this.url, demande, { observe: 'response' });
  }

  ordres(): Observable<OrdreTest[]> {
    return this.http.get<OrdreTest[]>(this.url);
  }

  enregistrerPosition(
    numeroContrat: string,
    codeIsin: string,
    montant: number | null,
    nombreParts: number | null,
  ): Observable<Disponible> {
    return this.http.post<Disponible>(`${this.url}/positions`, { numeroContrat, codeIsin, montant, nombreParts });
  }

  positions(): Observable<PositionTest[]> {
    return this.http.get<PositionTest[]>(`${this.url}/positions`);
  }

  vider(): Observable<void> {
    return this.http.delete<void>(this.url);
  }
}
