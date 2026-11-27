import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type Produit = 'PER' | 'AV';
export type OrigineFonds = 'REVENUS' | 'EPARGNE' | 'HERITAGE_DONATION' | 'VENTE_BIEN' | 'AUTRE';

export const ORIGINES: { code: OrigineFonds; libelle: string }[] = [
  { code: 'REVENUS', libelle: 'Salaire ou revenus' },
  { code: 'EPARGNE', libelle: 'Épargne existante' },
  { code: 'HERITAGE_DONATION', libelle: 'Héritage ou donation' },
  { code: 'VENTE_BIEN', libelle: 'Vente d’un bien' },
  { code: 'AUTRE', libelle: 'Autre (à préciser)' },
];

/** Bénéficiaire de la clause d'assurance vie ; dateNaissance au format AAAA-MM-JJ. */
export interface Beneficiaire {
  nom: string;
  prenom: string;
  dateNaissance: string;
  quotePartPct: number;
}

export interface EtatSouscription {
  produit: Produit;
  profil: 'VALIDE' | 'A_COMPLETER' | 'A_REVALIDER';
  etape: 'VERSEMENT' | 'SIGNATURE' | 'SIGNEE';
  minimum: number;
  montant: number | null;
  origineFonds: OrigineFonds | null;
  originePrecision: string | null;
  beneficiaires: Beneficiaire[];
  codeEnvoyeLe: string | null;
  bloqueeJusquAu: string | null;
  numeroContrat: string | null;
  /** PER-02 / AV-02 : les deux documents du produit ont été ouverts et cochés. */
  documentsLus: boolean;
}

export interface DemandeVersement {
  montant: number;
  origineFonds: OrigineFonds;
  originePrecision: string;
  beneficiaires: Beneficiaire[];
}

// CU-312 (PER-04) et CU-330 (AV-01) : souscription du PER ou de l'assurance vie (premier versement, clause
// bénéficiaire pour l'assurance vie, code SMS, signature). Les règles sont contrôlées par le backend, qui renvoie le
// message à afficher.
@Injectable({ providedIn: 'root' })
export class SouscriptionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/souscriptions`;

  // withCredentials : le cookie de session part vers le gateway (autre adresse que l'écran, CU-417).
  etat(produit: Produit): Observable<EtatSouscription> {
    return this.http.get<EtatSouscription>(this.adresse(produit), { withCredentials: true });
  }

  versement(produit: Produit, demande: DemandeVersement): Observable<EtatSouscription> {
    return this.http.post<EtatSouscription>(`${this.adresse(produit)}/versement`, demande, { withCredentials: true });
  }

  envoyerCode(produit: Produit): Observable<EtatSouscription> {
    return this.http.post<EtatSouscription>(`${this.adresse(produit)}/code`, {}, { withCredentials: true });
  }

  signer(produit: Produit, code: string): Observable<EtatSouscription> {
    return this.http.post<EtatSouscription>(`${this.adresse(produit)}/signature`, { code }, { withCredentials: true });
  }

  private adresse(produit: Produit): string {
    return `${this.url}/${produit.toLowerCase()}`;
  }
}
