import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type Produit = 'PER' | 'AV';

/** Un support en unités de compte éligible au profil (CU-412). Frais du support seulement. */
export interface SupportEligible {
  codeIsin: string;
  nom: string;
  categorie: string;
  niveauRisque: number;
  fraisSupportPct: number;
  valeurLiquidative: number;
  dateVl: string;
  nonCote: boolean;
  label: string | null;
}

export interface SupportsDisponibles {
  produit: Produit;
  profilValide: boolean;
  profilInvestisseur: string | null;
  fraisContratPct: number;
  supports: SupportEligible[];
  message: string | null;
}

// CU-412 (CAT-01B) : supports disponibles pour la cliente connectée, déjà filtrés par le backend (seul endroit du
// filtre par profil et par produit, trié par niveau de risque puis nom).
@Injectable({ providedIn: 'root' })
export class SupportsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/catalogue/supports`;

  lister(produit: Produit): Observable<SupportsDisponibles> {
    // CU-417 : withCredentials pour envoyer le cookie de session au gateway (autre adresse que l'écran) ; sans lui,
    // le backend répond 401 et l'application renvoie vers la connexion, puis vers l'espace.
    return this.http.get<SupportsDisponibles>(this.url, { params: { produit }, withCredentials: true });
  }
}
