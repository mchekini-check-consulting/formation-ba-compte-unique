import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export interface SessionOuverte {
  identifiant: string;
}

export interface RefusConnexion {
  message: string;
  invitation: string | null;
}

// Connexion à l'espace (POST /connexion, GET /connexion/session). La session est portée par
// un cookie que le navigateur envoie seul au gateway (withCredentials).
@Injectable({ providedIn: 'root' })
export class ConnexionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/connexion`;

  connecter(identifiant: string, motDePasse: string): Observable<SessionOuverte> {
    return this.http.post<SessionOuverte>(this.url, { identifiant, motDePasse }, { withCredentials: true });
  }

  session(): Observable<SessionOuverte> {
    return this.http.get<SessionOuverte>(`${this.url}/session`, { withCredentials: true });
  }
}
