import { CleFonctionnalite } from '../fonctionnalites/fonctionnalites';
import { DETAIL_TAUX_LIVRET_A, LIVRET_A, TAUX_LIVRET_A, euros } from '../livret-a/parametres';

// Produits proposés à la souscription dans l'espace client (maquette « Espace client Hi-Tech Bank »).
export interface Produit {
  nom: string;
  categorie: string;
  /** Couleur de l'étiquette et de l'illustration. */
  teinte: 'mint' | 'amber' | 'blue' | 'violet' | 'rose';
  description: string;
  taux?: string;
  tauxDetail?: string;
  /** PO 28/11 : garantie du capital du fonds en euros, sur la ligne sous le taux (paramètre). */
  tauxGarantie?: string;
  /** Libellé placé avant le taux (« Taux brut visé en 2026 : »), facultatif. */
  tauxAvant?: string;
  minimum?: string;
  note?: string;
  /** Lot 3 : bouton « Souscrire » d'un produit pas encore ouvert en ligne (LDDS, CEL). */
  souscription?: CleFonctionnalite;
  /** Lot 3 : bouton « Faire une demande » d'un crédit. */
  demande?: CleFonctionnalite;
}

export const EPARGNE: Produit[] = [
  {
    nom: 'Livret A',
    categorie: 'Épargne réglementée',
    teinte: 'mint',
    // CU-27 critère 5 : taux, plafond et minimum repris des paramètres uniques du Livret A.
    description: `Une épargne disponible à tout moment, au taux fixé par l'État. Plafond de versements de ${euros(LIVRET_A.plafond)}.`,
    taux: TAUX_LIVRET_A,
    tauxDetail: DETAIL_TAUX_LIVRET_A,
    minimum: `Ouverture dès ${euros(LIVRET_A.versementMinimum)}`,
    note: "Exonéré d'impôt sur le revenu",
  },
  {
    nom: 'LDDS',
    souscription: 'ldds',
    categorie: 'Épargne réglementée',
    teinte: 'mint',
    description:
      "Livret de développement durable et solidaire : vos fonds financent l'économie sociale et solidaire. Plafond de 12 000 €.",
    taux: '1,70 %',
    tauxDetail: 'net / an',
    minimum: 'Ouverture dès 10 €',
    note: "Exonéré d'impôt sur le revenu",
  },
  {
    nom: 'CEL',
    souscription: 'cel',
    categorie: 'Épargne logement',
    teinte: 'amber',
    description:
      'Vous épargnez pour un projet immobilier et accédez à un crédit à taux prédéfini. Plafond de 15 300 €.',
    taux: '1,25 %',
    tauxDetail: 'brut / an',
    minimum: 'Ouverture dès 300 €, puis 75 € minimum par versement',
    note: 'Condition : un crédit immobilier',
  },
  {
    nom: 'Assurance-vie',
    categorie: 'Placement',
    teinte: 'blue',
    description:
      "Un contrat de placement et de transmission, avec fonds en euros et unités de compte. Frais d'entrée de 0 %.",
    // CU-24 : taux brut visé du fonds en euros lu dans les paramètres publics du backend (espace.ts), jamais en dur.
    minimum: 'Versement minimum : 100 €',
    note: 'Frais de gestion détaillés avant signature',
  },
  {
    nom: 'PER',
    categorie: 'Retraite',
    teinte: 'violet',
    description:
      "Plan d'épargne retraite : vous préparez vos revenus de retraite. Les fonds sont bloqués jusqu'à la retraite, sauf cas prévus par la loi.",
    minimum: 'Versement minimum : 100 €',
    note: 'Déduction fiscale selon votre situation',
  },
];

export const CREDITS: Produit[] = [
  {
    nom: 'Crédit immobilier',
    demande: 'demande-credit-immo',
    categorie: 'Crédit',
    teinte: 'blue',
    description:
      "Un prêt pour acheter ou faire des travaux. La mensualité et l'assurance sont visibles avant de vous engager.",
  },
  {
    nom: 'Crédit à la consommation',
    demande: 'demande-credit-conso',
    categorie: 'Crédit',
    teinte: 'rose',
    description:
      'Un prêt pour un projet ponctuel : véhicule, travaux, équipement. Montant, durée et taux sont affichés avant toute signature.',
  },
  {
    nom: 'Rachat de crédits',
    demande: 'demande-rachat-credits',
    categorie: 'Regroupement',
    teinte: 'mint',
    description:
      'Regroupez vos crédits en une seule mensualité. Vous voyez le coût total avant et après le regroupement.',
  },
];
