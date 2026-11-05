import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type EtatReinitialisation = 'VALIDE' | 'EXPIRE' | 'REINITIALISE';

export interface Reponse {
  message: string;
  regles: string[];
}

// CU-46 : mot de passe oublié (POST /reinitialisation...). Le jeton du lien est envoyé dans le
// corps des requêtes, jamais dans l'adresse appelée.
@Injectable({ providedIn: 'root' })
export class ReinitialisationService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/reinitialisation`;

  demander(identifiant: string): Observable<Reponse> {
    return this.http.post<Reponse>(`${this.url}/demande`, { identifiant });
  }

  etat(jeton: string): Observable<{ etat: EtatReinitialisation }> {
    return this.http.post<{ etat: EtatReinitialisation }>(`${this.url}/etat`, { jeton });
  }

  reinitialiser(jeton: string, motDePasse: string, confirmation: string): Observable<{ etat: EtatReinitialisation }> {
    return this.http.post<{ etat: EtatReinitialisation }>(this.url, { jeton, motDePasse, confirmation });
  }
}
