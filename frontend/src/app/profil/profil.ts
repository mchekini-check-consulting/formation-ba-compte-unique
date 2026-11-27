import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

// CU-10 (US-05) : profil client avant une souscription de PER ou d'assurance vie.
export type Statut = 'VALIDE' | 'A_COMPLETER' | 'A_REVALIDER';

export interface Reponses {
  statutMatrimonial: string | null;
  situationProfessionnelle: string | null;
  patrimoine: string | null;
  produitsDetenus: string | null;
  objectif: string | null;
  horizon: string | null;
  reactionBaisse: string | null;
  perteAcceptable: string | null;
  financeDurable: string | null;
  plafondRetraiteEuros: number | null;
  plafondRetraiteAnnee: number | null;
  tmi: number | null;
  anneeDepartRetraite: number | null;
}

export interface Profil {
  version: number;
  valideLe: string;
  revaliderLe: string;
  reponses: Reponses;
  profilInvestisseur: string;
  scoreInvestisseur: number;
}

export interface EtatProfil {
  statut: Statut;
  profil: Profil | null;
  /** D8 : premier prénom de la cliente (dossier d'ouverture), null s'il est absent. */
  prenom?: string | null;
}

export const LIBELLES_STATUT: Record<Statut, string> = {
  VALIDE: 'Validé',
  A_COMPLETER: 'À compléter',
  A_REVALIDER: 'À revalider',
};

export const LIBELLES_PROFIL: Record<string, string> = { PRUDENT: 'Prudent', EQUILIBRE: 'Équilibré', DYNAMIQUE: 'Dynamique' };

export interface Question {
  id: keyof Reponses;
  groupe: string;
  libelle: string;
  aide?: string;
  /** Codes et libellés d'une liste ; absent pour un nombre saisi. */
  choix?: [string, string][];
}

// Questions de la maquette CU-10, toutes obligatoires pour tous les produits (codes contrôlés par le backend).
export const QUESTIONS: Question[] = [
  { id: 'statutMatrimonial', groupe: 'Situation', libelle: 'Statut matrimonial', aide: 'Sert à instruire un décès et la réversion.',
    choix: [['MARIE', 'Marié(e)'], ['PACSE', 'Pacsé(e)'], ['CONCUBINAGE', 'Concubinage'], ['CELIBATAIRE', 'Célibataire'], ['VEUF', 'Veuf(ve)'], ['DIVORCE', 'Divorcé(e)']] },
  { id: 'situationProfessionnelle', groupe: 'Situation', libelle: 'Situation professionnelle',
    choix: [['SALARIE', 'Salarié(e)'], ['INDEPENDANT', 'Indépendant(e)'], ['RETRAITE', 'Retraité(e)'], ['SANS_ACTIVITE', 'Sans activité']] },
  { id: 'patrimoine', groupe: 'Situation', libelle: 'Patrimoine et épargne existante', aide: 'Un montant indicatif suffit.',
    choix: [['MOINS_10K', 'Moins de 10 000 €'], ['DE_10K_A_100K', '10 000 € à 100 000 €'], ['PLUS_100K', 'Plus de 100 000 €']] },
  { id: 'produitsDetenus', groupe: 'Connaissances et objectifs', libelle: 'Produits déjà détenus',
    choix: [['AUCUN', 'Aucun'], ['LIVRETS', 'Livrets uniquement'], ['AV_EUROS', 'Assurance vie en fonds en euros'], ['AV_OU_UC', 'Assurance vie ou fonds en unités de compte']] },
  { id: 'objectif', groupe: 'Connaissances et objectifs', libelle: 'Objectif de placement',
    choix: [['RETRAITE', 'Préparer la retraite'], ['MOYEN_TERME', 'Épargner à moyen terme'], ['TRANSMETTRE', 'Transmettre'], ['AUTRE', 'Autre']] },
  { id: 'horizon', groupe: 'Connaissances et objectifs', libelle: 'Horizon de placement',
    choix: [['MOINS_3_ANS', 'Moins de 3 ans'], ['DE_3_A_8_ANS', '3 à 8 ans'], ['PLUS_8_ANS', 'Plus de 8 ans']] },
  { id: 'reactionBaisse', groupe: 'Connaissances et objectifs', libelle: 'Réaction à une baisse de 20 % de votre épargne',
    aide: 'Sert à calculer votre profil investisseur.',
    choix: [['RETIRER', 'Je retire une partie de mon épargne'], ['ATTENDRE', 'J’attends la reprise'], ['INVESTIR', 'J’en profite pour investir davantage']] },
  { id: 'perteAcceptable', groupe: 'Connaissances et objectifs', libelle: 'Part de votre épargne que vous pourriez perdre sans gêne',
    choix: [['AUCUNE', 'Aucune'], ['JUSQU_A_10', 'Jusqu’à 10 %'], ['DE_10_A_30', '10 à 30 %'], ['PLUS_30', 'Plus de 30 %']] },
  { id: 'financeDurable', groupe: 'Connaissances et objectifs', libelle: 'Préférences en finance durable',
    choix: [['AUCUNE', 'Aucune préférence'], ['PART_MINIMALE', 'Part minimale'], ['SANS_PRECISION', 'Sans précision']] },
  { id: 'plafondRetraiteEuros', groupe: 'Épargne retraite', libelle: 'Plafond épargne retraite (€)',
    aide: 'Total imprimé sur votre avis d’imposition (« plafond pour les cotisations versées en N ») : il peut dépasser 37 680 €.' },
  { id: 'plafondRetraiteAnnee', groupe: 'Épargne retraite', libelle: 'Année de ce plafond', aide: 'L’année N indiquée sur votre avis d’imposition.' },
  { id: 'tmi', groupe: 'Épargne retraite', libelle: 'Tranche marginale d’imposition (TMI)',
    choix: [['0', '0 %'], ['11', '11 %'], ['30', '30 %'], ['41', '41 %'], ['45', '45 %']] },
  { id: 'anneeDepartRetraite', groupe: 'Épargne retraite', libelle: 'Année prévue de départ à la retraite' },
];

/** Réponse saisie : nombre pour les montants, années et TMI ; null si vide ou non numérique. */
export function reponsesDe(formulaire: FormData): Reponses {
  const reponses = {} as Record<keyof Reponses, string | number | null>;
  for (const q of QUESTIONS) {
    const valeur = String(formulaire.get(q.id) ?? '').trim();
    const nombre = !q.choix || q.id === 'tmi';
    reponses[q.id] = valeur === '' ? null : nombre ? (Number.isFinite(Number(valeur)) ? Number(valeur) : null) : valeur;
  }
  return reponses as unknown as Reponses;
}

// CU-10 : GET /profil, POST /profil, GET /profil/versions, avec le cookie de session.
@Injectable({ providedIn: 'root' })
export class ProfilService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/profil`;

  lire(): Observable<EtatProfil> {
    return this.http.get<EtatProfil>(this.url, { withCredentials: true });
  }

  valider(reponses: Reponses, consentement: boolean): Observable<Profil> {
    return this.http.post<Profil>(this.url, { reponses, consentement }, { withCredentials: true });
  }

  versions(): Observable<Profil[]> {
    return this.http.get<Profil[]>(`${this.url}/versions`, { withCredentials: true });
  }
}
