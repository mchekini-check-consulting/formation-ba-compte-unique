import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { pourcentage } from '../livret-a/parametres';

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

/**
 * CU-24 (US-00), D1 et D7 du PO : paramètres publics des produits (GET /catalogue/parametres-publics, sans connexion).
 * Un paramètre non renseigné vaut null et n'est pas affiché.
 */
export interface ParametresPublics {
  /** Frais de gestion annuels du contrat sur les unités de compte, en % ({ PER: 0.5, AV: 0.65 }). */
  fraisUcPct: Record<Produit, number>;
  fondsEurosAv: { tauxBrutVisePct: number | null; dateMiseAJour: string | null; fraisGestionPct: number | null };
}

/** Taux du fonds en euros affiché sur une carte : « Taux brut visé en 2026 : » <b>2,90 %</b> « (mis à jour le 01/01/2026)… ». */
export interface TauxAffiche {
  avant: string;
  taux: string;
  detail: string;
}

/**
 * CU-24 : taux brut visé du fonds en euros de l'assurance vie et sa date de mise à jour (PO, 27/11/2026 : mis à jour
 * chaque 1er janvier, l'année affichée est celle de cette date). Le net n'est affiché que si les frais du fonds en
 * euros sont fixés (D1). null sans taux.
 */
export function tauxFondsEurosAv(p: ParametresPublics | null): TauxAffiche | null {
  const f = p?.fondsEurosAv;
  if (f?.tauxBrutVisePct == null) {
    return null;
  }
  const [annee, mois, jour] = f.dateMiseAJour?.split('-') ?? [];
  const net = f.fraisGestionPct == null ? '' : `, soit ${pourcentage(f.tauxBrutVisePct - f.fraisGestionPct)} net après frais`;
  return {
    avant: annee ? `Taux brut visé en ${annee} :` : 'Taux brut visé :',
    taux: pourcentage(f.tauxBrutVisePct),
    detail: `${annee ? `(mis à jour le ${jour}/${mois}/${annee}) ` : ''}sur le fonds en euros${net}, non garanti`,
  };
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

  /** Paramètres publics des produits, une seule source (backend), lisible sans session (page publique). */
  parametresPublics(): Observable<ParametresPublics> {
    return this.http.get<ParametresPublics>(`${apiBase('backend')}/catalogue/parametres-publics`);
  }

  lister(produit: Produit): Observable<SupportsDisponibles> {
    // CU-417 : withCredentials pour envoyer le cookie de session au gateway (autre adresse que l'écran) ; sans lui,
    // le backend répond 401 et l'application renvoie vers la connexion, puis vers l'espace.
    return this.http.get<SupportsDisponibles>(this.url, { params: { produit }, withCredentials: true });
  }
}
