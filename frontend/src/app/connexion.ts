import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export interface SessionOuverte {
  identifiant: string;
}

/** CU-17 : session en cours et secondes restantes avant sa fermeture faute d'activité. */
export interface SessionEnCours extends SessionOuverte {
  expireDansSecondes: number;
}

export interface RefusConnexion {
  message: string;
  invitation: string | null;
}

// Connexion à l'espace (POST /connexion), session en cours (GET /connexion/session, ne prolonge
// pas la session), action de la cliente (POST /connexion/session/activite) et déconnexion
// (POST /connexion/deconnexion, CU-17). La session est portée par un cookie que le navigateur
// envoie seul au gateway (withCredentials).
@Injectable({ providedIn: 'root' })
export class ConnexionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/connexion`;

  connecter(identifiant: string, motDePasse: string): Observable<SessionOuverte> {
    return this.http.post<SessionOuverte>(this.url, { identifiant, motDePasse }, { withCredentials: true });
  }

  session(): Observable<SessionEnCours> {
    return this.http.get<SessionEnCours>(`${this.url}/session`, { withCredentials: true });
  }

  activite(): Observable<SessionEnCours> {
    return this.http.post<SessionEnCours>(`${this.url}/session/activite`, null, { withCredentials: true });
  }

  deconnecter(): Observable<void> {
    return this.http.post<void>(`${this.url}/deconnexion`, null, { withCredentials: true });
  }
}
