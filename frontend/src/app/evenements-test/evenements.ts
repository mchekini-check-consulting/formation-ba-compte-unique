import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** Un événement de la boîte d'envoi (CU-382). */
export interface EvenementADistribuer {
  id: string;
  type: string;
  topic: string;
  cle: string;
  dateMetier: string;
  creeLe: string;
  publieLe: string | null;
  essais: number;
  derniereErreur: string | null;
}

/** Un événement reçu par le journal back-office. */
export interface EvenementRecu {
  id: string;
  type: string;
  topic: string;
  cle: string;
  dateMetier: string;
  recuLe: string;
}

/** Un message en erreur après 3 essais. */
export interface MessageEnErreur {
  topic: string;
  cle: string | null;
  groupe: string;
  motif: string;
  essais: number;
  enErreurLe: string;
}

export interface EtatEvenements {
  aDistribuer: EvenementADistribuer[];
  recus: EvenementRecu[];
  enErreur: MessageEnErreur[];
}

// CU-382 (TECH-14) : routes /test/evenements du backend (absentes en preprod et prod : 404).
@Injectable({ providedIn: 'root' })
export class EvenementsTestService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/test/evenements`;

  lire(): Observable<EtatEvenements> {
    return this.http.get<EtatEvenements>(this.url);
  }

  envoyerMessageInvalide(): Observable<void> {
    return this.http.post<void>(`${this.url}/message-invalide`, {});
  }
}
