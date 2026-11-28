import { Injectable } from '@angular/core';
import { OrigineFonds, Produit } from '../souscription/souscription';

/** Saisie de l'étape « Mon versement », telle que tapée (pas encore envoyée). */
export interface Brouillon {
  montant: string;
  origineFonds: OrigineFonds | null;
  precision: string;
  lignes: { nom: string; prenom: string; dateNaissance: string; quotePart: string }[];
}

// Lot 5 (D6) : le retour arrière conserve les saisies tant que la session est ouverte. Gardées en mémoire de
// l'application seulement (rien en base, rien dans le navigateur) : perdues à la fermeture de l'onglet.
@Injectable({ providedIn: 'root' })
export class BrouillonSouscription {
  private readonly saisies = new Map<Produit, Brouillon>();

  lire(produit: Produit): Brouillon | undefined {
    return this.saisies.get(produit);
  }

  garder(produit: Produit, brouillon: Brouillon): void {
    this.saisies.set(produit, brouillon);
  }

  oublier(produit: Produit): void {
    this.saisies.delete(produit);
  }
}
