import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type EtatActivation = 'VALIDE' | 'EXPIRE' | 'DEJA_ACTIVE' | 'ACTIVE';

export interface Refus {
  message: string;
  regles: string[];
}

// CU-14 : activation de l'espace (POST /activation...). Le jeton du lien est envoyé dans le
// corps des requêtes, jamais dans l'adresse appelée.
@Injectable({ providedIn: 'root' })
export class ActivationService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/activation`;

  etat(jeton: string): Observable<{ etat: EtatActivation }> {
    return this.http.post<{ etat: EtatActivation }>(`${this.url}/etat`, { jeton });
  }

  activer(jeton: string, motDePasse: string, confirmation: string): Observable<{ etat: EtatActivation }> {
    return this.http.post<{ etat: EtatActivation }>(this.url, { jeton, motDePasse, confirmation });
  }

  nouveauLien(jeton: string): Observable<Refus> {
    return this.http.post<Refus>(`${this.url}/nouveau-lien`, { jeton });
  }
}
