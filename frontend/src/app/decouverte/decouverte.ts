import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type Produit = 'PER' | 'AV';
export type CodeDocument = 'DIC' | 'FICHE_PRODUIT';

export interface EtatDocument {
  code: CodeDocument;
  titre: string;
  version: string;
  contenu: string;
  ouvert: boolean;
  lu: boolean;
}

export interface EtatDecouverte {
  produit: Produit;
  documents: EtatDocument[];
  peutContinuer: boolean;
}

// CU-311 (PER-02) et CU-331 (AV-02) : documents à lire avant de souscrire. Le backend enregistre chaque ouverture
// (date, heure, version) et refuse de cocher une case avant l'ouverture du document.
@Injectable({ providedIn: 'root' })
export class DecouverteService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/decouverte`;

  // withCredentials : le cookie de session part vers le gateway (autre adresse que l'écran, CU-417).
  etat(produit: Produit): Observable<EtatDecouverte> {
    return this.http.get<EtatDecouverte>(this.url, { params: { produit }, withCredentials: true });
  }

  ouvrir(produit: Produit, document: CodeDocument): Observable<EtatDecouverte> {
    return this.http.post<EtatDecouverte>(`${this.url}/ouvertures`, { produit, document }, { withCredentials: true });
  }

  cocher(produit: Produit, document: CodeDocument): Observable<EtatDecouverte> {
    return this.http.post<EtatDecouverte>(`${this.url}/lectures`, { produit, document }, { withCredentials: true });
  }
}
