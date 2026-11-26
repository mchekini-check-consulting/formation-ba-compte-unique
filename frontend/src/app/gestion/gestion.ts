import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type Produit = 'PER' | 'AV';
export type ModeGestion = 'LIBRE' | 'PILOTEE' | 'MANDAT';
export type ProfilGestion = 'PRUDENT' | 'EQUILIBRE' | 'DYNAMIQUE';

export interface LigneAllocation {
  codeIsin: string;
  nom: string;
  niveauRisque: number;
  nonCote: boolean;
  partPct: number;
}

export interface Contraintes {
  partMinNonCotePct: number;
  partMinFaibleRisquePct: number;
  horizonAnnees: number | null;
  grilleAValider: boolean;
}

export interface Changement {
  mode: ModeGestion;
  profil: ProfilGestion;
  horizonAnnees: number | null;
  frais: number;
  changeLe: string;
}

export interface Reequilibrage {
  demandeLe: string;
  montantArbitre: number;
  arbitrages: { ordreId: string; supportDepart: string; supportArrivee: string; montant: number }[];
}

export interface ContratGestion {
  numeroContrat: string;
  mode: ModeGestion;
  profil: ProfilGestion;
  changeLe: string;
  encours: number;
  /** AV : encours des supports non cotés, conservés hors gestion pilotée (0 pour le PER). */
  encoursNonCote: number;
  contraintes: Contraintes;
  allocation: LigneAllocation[];
  allocationIndisponible: string | null;
  historique: Changement[];
  reequilibrages: Reequilibrage[];
}

export interface EtatGestion {
  produit: Produit;
  profilInvestisseur: ProfilGestion | null;
  contratsDeTest: boolean;
  mandat: { tauxToutComprisPct: number; montantMinimum: number };
  contrat: ContratGestion | null;
}

export interface Versement {
  id: string;
  montant: number;
  repartition: { codeIsin: string; montant: number }[];
}

// CU-314 (PER-07) et CU-338 (AV-07) : choix du mode de gestion d'un contrat PER ou assurance vie. Les règles (profil
// plafonné, minimum du mandat, parts minimales) sont contrôlées par le backend, qui renvoie le message à afficher.
@Injectable({ providedIn: 'root' })
export class GestionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/gestion`;

  // withCredentials : le cookie de session part vers le gateway (autre adresse que l'écran, CU-417).
  etat(produit: Produit): Observable<EtatGestion> {
    return this.http.get<EtatGestion>(this.url, { params: { produit }, withCredentials: true });
  }

  creerContratDeTest(produit: Produit): Observable<EtatGestion> {
    return this.http.post<EtatGestion>(`${this.url}/contrats`, { produit }, { withCredentials: true });
  }

  choisir(produit: Produit, mode: ModeGestion, profil: ProfilGestion): Observable<EtatGestion> {
    return this.http.post<EtatGestion>(`${this.url}/choix`, { produit, mode, profil }, { withCredentials: true });
  }

  verser(produit: Produit, id: string, montant: number): Observable<Versement> {
    return this.http.post<Versement>(`${this.url}/versements`, { produit, id, montant }, { withCredentials: true });
  }
}
