import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
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
  /** Fréquence de calcul de la valeur liquidative (catalogue), null si absente. */
  periodiciteVl: string | null;
}

/** Lot 6 : une valeur liquidative de la courbe d'un support (catalogue.historique_vl, lecture seule). */
export interface PointVl {
  dateVl: string;
  valeur: number;
}

/**
 * CU-24 (US-00), D1 et D7 du PO : paramètres publics des produits (GET /catalogue/parametres-publics, sans connexion).
 * Un paramètre non renseigné vaut null et n'est pas affiché.
 */
export interface ParametresPublics {
  /** Frais de gestion annuels du contrat sur les unités de compte, en %, par produit. */
  fraisUcPct: Record<Produit, number>;
  fondsEurosAv: FondsEuros;
  /** Lot 6, D1 et PO 28/11 : fonds en euros du PER (taux, date, frais de PER-09). */
  fondsEurosPer: FondsEuros;
  /** Lot 6, D7 : catégorie du catalogue → famille ; vide tant que le regroupement n'est pas validé. */
  famillesCategories: Record<string, string>;
  /** Lot 7 : frais d'entrée en % par produit (null : non affiché). */
  fraisEntreePct?: Record<Produit, number | null>;
  /** Lot 7 : mentions réglementaires renseignées (taux-non-garanti, perte-capital-uc, performances-passees, non-cote). */
  mentions?: Record<string, string>;
}

/** Fonds en euros d'un produit ; un champ null n'est pas affiché (D7 : taux servi non fixé). */
export interface FondsEuros {
  tauxBrutVisePct: number | null;
  dateMiseAJour: string | null;
  fraisGestionPct: number | null;
  tauxServiPct: number | null;
}

/**
 * Taux du fonds en euros : une ligne pour le taux et sa mention (« Taux brut visé en <année> : » <b>taux</b> « (mis à
 * jour le …), soit … net de frais de gestion. Taux non garanti. »), la garantie du capital sur la ligne suivante, pour
 * qu'on ne puisse pas lire « non garanti » comme portant sur le fonds ou le capital (PO, 28/11/2026).
 */
export interface TauxAffiche {
  avant: string;
  taux: string;
  detail: string;
  /** Mention mentions.capital-garanti-fonds-euros ; vide si le paramètre est vide. */
  garantie: string;
}

type ParametresTaux = { fondsEurosAv?: Partial<FondsEuros> | null; fondsEurosPer?: Partial<FondsEuros> | null; mentions?: Record<string, string> };

/** CU-24 : fonds en euros de l'assurance vie (net absent tant que ses frais ne sont pas fixés, CU-530). */
export function tauxFondsEurosAv(p: ParametresTaux | null): TauxAffiche | null {
  return tauxFondsEuros(p?.fondsEurosAv, p?.mentions);
}

/** PO 28/11/2026 : fonds en euros du PER (taux, net de ses frais de gestion de PER-09, garantie). */
export function tauxFondsEurosPer(p: ParametresTaux | null): TauxAffiche | null {
  return tauxFondsEuros(p?.fondsEurosPer, p?.mentions);
}

/**
 * Même affichage pour le PER et l'assurance vie, tout lu dans les paramètres : taux brut visé et date (mis à jour chaque
 * 1er janvier, l'année affichée est celle de la date), net = taux brut visé − frais de gestion du fonds en euros
 * seulement si ces frais sont renseignés, mentions taux-non-garanti et capital-garanti-fonds-euros. null sans taux.
 */
export function tauxFondsEuros(f: Partial<FondsEuros> | null | undefined, mentions: Record<string, string> = {}): TauxAffiche | null {
  if (f?.tauxBrutVisePct == null) {
    return null;
  }
  const [annee, mois, jour] = f.dateMiseAJour?.split('-') ?? [];
  const net = f.fraisGestionPct == null ? '' : `, soit ${pourcentage(Math.round((f.tauxBrutVisePct - f.fraisGestionPct) * 100) / 100)} net de frais de gestion`;
  const mentionTaux = mentions['taux-non-garanti'] ?? '';
  return {
    avant: annee ? `Taux brut visé en ${annee} :` : 'Taux brut visé :',
    taux: pourcentage(f.tauxBrutVisePct),
    detail: `${annee ? `(mis à jour le ${jour}/${mois}/${annee})` : ''}${net}.${mentionTaux ? ` ${mentionTaux}` : ''}`,
    garantie: mentions['capital-garanti-fonds-euros'] ?? '',
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
    return this.parametres$;
  }

  // Lot 7 : lus une fois par chargement (plusieurs mentions et cartes sur une même page) ; un échec n'est pas gardé.
  private readonly parametres$ = this.http
    .get<ParametresPublics>(`${apiBase('backend')}/catalogue/parametres-publics`)
    .pipe(shareReplay({ bufferSize: 1, refCount: false }));

  lister(produit: Produit): Observable<SupportsDisponibles> {
    // CU-417 : withCredentials pour envoyer le cookie de session au gateway (autre adresse que l'écran) ; sans lui,
    // le backend répond 401 et l'application renvoie vers la connexion, puis vers l'espace.
    return this.http.get<SupportsDisponibles>(this.url, { params: { produit }, withCredentials: true });
  }

  /** Lot 6 : courbe de la valeur liquidative d'un support, de la plus ancienne à la plus récente. */
  historique(codeIsin: string): Observable<PointVl[]> {
    return this.http.get<PointVl[]>(`${this.url}/${encodeURIComponent(codeIsin)}/historique`, { withCredentials: true });
  }
}
