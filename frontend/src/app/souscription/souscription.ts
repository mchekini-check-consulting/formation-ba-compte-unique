import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type OrigineFonds = 'REVENUS' | 'EPARGNE' | 'HERITAGE_DONATION' | 'VENTE_BIEN' | 'AUTRE';

export const ORIGINES: { code: OrigineFonds; libelle: string }[] = [
  { code: 'REVENUS', libelle: 'Salaire ou revenus' },
  { code: 'EPARGNE', libelle: 'Épargne existante' },
  { code: 'HERITAGE_DONATION', libelle: 'Héritage ou donation' },
  { code: 'VENTE_BIEN', libelle: 'Vente d’un bien' },
  { code: 'AUTRE', libelle: 'Autre (à préciser)' },
];

export interface EtatSouscription {
  profil: 'VALIDE' | 'A_COMPLETER' | 'A_REVALIDER';
  etape: 'VERSEMENT' | 'SIGNATURE' | 'SIGNEE';
  minimum: number;
  montant: number | null;
  origineFonds: OrigineFonds | null;
  originePrecision: string | null;
  codeEnvoyeLe: string | null;
  bloqueeJusquAu: string | null;
  numeroContrat: string | null;
}

// CU-312 (PER-04) : souscription du PER (premier versement, code SMS, signature). Les règles (profil validé, minimum,
// origine des fonds, validité du code, blocage) sont contrôlées par le backend, qui renvoie le message à afficher.
@Injectable({ providedIn: 'root' })
export class SouscriptionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/souscriptions/per`;

  // withCredentials : le cookie de session part vers le gateway (autre adresse que l'écran, CU-417).
  etat(): Observable<EtatSouscription> {
    return this.http.get<EtatSouscription>(this.url, { withCredentials: true });
  }

  versement(montant: number, origineFonds: OrigineFonds, originePrecision: string): Observable<EtatSouscription> {
    return this.http.post<EtatSouscription>(`${this.url}/versement`, { montant, origineFonds, originePrecision },
      { withCredentials: true });
  }

  envoyerCode(): Observable<EtatSouscription> {
    return this.http.post<EtatSouscription>(`${this.url}/code`, {}, { withCredentials: true });
  }

  signer(code: string): Observable<EtatSouscription> {
    return this.http.post<EtatSouscription>(`${this.url}/signature`, { code }, { withCredentials: true });
  }
}
