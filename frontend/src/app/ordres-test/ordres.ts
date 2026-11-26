import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type TypeOrdre = 'VERSEMENT' | 'RACHAT' | 'ARBITRAGE';

/**
 * Demande d'ordre (CU-376) : l'identifiant est créé par l'écran au moment de la validation. En euros (montant) ou,
 * pour un rachat ou un arbitrage, en nombre de parts du support vendu.
 */
export interface DemandeOrdre {
  id: string;
  numeroContrat: string;
  type: TypeOrdre;
  supportDepart: string | null;
  supportArrivee: string | null;
  montant: number | null;
  nombreParts: number | null;
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
  /** Montant en euros ; vide pour un ordre en parts. */
  montant: number | null;
  /** Nombre de parts vendues ; vide pour un ordre en euros. */
  nombreParts: number | null;
  demandeLe: string;
  dateExecutionPrevue: string;
  statut: 'DEMANDE' | 'EN_COURS' | 'EXECUTE' | 'REJETE' | 'ANNULE';
  /** Date et heure métier de l'annulation (Paris) ; vide si l'ordre n'est pas annulé. */
  annuleLe: string | null;
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

  /** Annule un ordre « demandé » avant l'heure limite (409 sinon, avec le motif). */
  annuler(id: string): Observable<OrdreTest> {
    return this.http.post<OrdreTest>(`${this.url}/${encodeURIComponent(id)}/annulation`, null);
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
