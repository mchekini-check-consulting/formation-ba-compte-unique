import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

// CU-146 (US-22) : un bénéficiaire tel qu'affiché (critère 11) : jamais l'IBAN complet.
export interface Beneficiaire {
  id: number;
  libelle: string;
  nomVerifie: string;
  ibanMasque: string;
  ajouteLe: string;
  verification: string;
}

export interface DemandeAjout {
  iban: string;
  nom: string;
  libelle: string;
}

// Ajout vérifié, en attente du code reçu par SMS.
export interface AjoutEnAttente {
  ajoutId: number;
  nomVerifie: string;
  message: string;
}

// Refus du backend : message exact du ticket ; termine -> le parcours en cours est arrêté (retour au début).
export interface Refus {
  texte: string;
  termine: boolean;
}

/** Message d'un refus du backend, ou défaut si l'appel n'a pas abouti (réseau, erreur imprévue). */
export function refusDe(erreur: HttpErrorResponse, defaut: string): Refus {
  const texte: unknown = erreur.error?.message;
  return typeof texte === 'string'
    ? { texte, termine: erreur.error?.termine === true }
    : { texte: defaut, termine: false };
}

// CU-146 : rubrique « Mes bénéficiaires » (GET/POST /beneficiaires, POST /beneficiaires/ajouts/{id}/confirmation,
// PATCH et DELETE /beneficiaires/{id}), avec le cookie de session.
@Injectable({ providedIn: 'root' })
export class BeneficiairesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/beneficiaires`;

  lister(): Observable<Beneficiaire[]> {
    return this.http.get<Beneficiaire[]>(this.url, { withCredentials: true });
  }

  ajouter(demande: DemandeAjout): Observable<AjoutEnAttente> {
    return this.http.post<AjoutEnAttente>(this.url, demande, { withCredentials: true });
  }

  confirmer(ajoutId: number, code: string): Observable<Beneficiaire> {
    return this.http.post<Beneficiaire>(`${this.url}/ajouts/${ajoutId}/confirmation`, { code }, { withCredentials: true });
  }

  renommer(id: number, libelle: string): Observable<Beneficiaire> {
    return this.http.patch<Beneficiaire>(`${this.url}/${id}`, { libelle }, { withCredentials: true });
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`, { withCredentials: true });
  }
}
