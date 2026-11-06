import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { ControleSanctionsReponse } from './bouchons-identite';
import { Identite, versIso } from './regles';

// Dossier d'ouverture de compte, enregistré par le backend principal (CUB-8) :
// PUT /dossiers/{reference}/identite puis PUT /dossiers/{reference}/controles.

export type ResultatControle = 'OK' | 'NOK';

export type StatutDossier =
  | 'EN_COURS_DE_VERIFICATION'
  | 'ACCEPTE'
  | 'ANNULE'
  | 'CONVENTION_SIGNEE'
  | 'CREATION_A_RELANCER'
  | 'COMPTE_OUVERT';

/** Résultat des contrôles sanctions et PEP, affiché à la cliente et enregistré en base. */
export interface Controles {
  sanctions: ResultatControle;
  pep: ResultatControle;
}

export interface DossierReponse {
  reference: string;
  statut: StatutDossier;
  indiceAmericain: boolean;
  residenceFrancaise: boolean;
  controleSanctions: ResultatControle | null;
  controlePep: ResultatControle | null;
  controleLe: string | null;
}

/**
 * CU-9 : refus (409) d'une nouvelle ouverture pour une personne déjà connue. Les messages viennent
 * du backend (textes exacts du ticket). Seule une ouverture en cours donne de quoi la reprendre ;
 * seule une ouverture annulée par contrôle donne les coordonnées du conseiller.
 */
export type CodeRefusOuverture = 'ESPACE_EXISTANT' | 'ACTIVATION_ATTENDUE' | 'OUVERTURE_ANNULEE' | 'OUVERTURE_EN_COURS';

export interface RepriseOuverture {
  reference: string;
  /** IDENTITE : le dossier attend encore la cliente ; OUVERTURE : il attend la banque. */
  etape: 'IDENTITE' | 'OUVERTURE';
}

export interface RefusOuverture {
  code: CodeRefusOuverture;
  message: string;
  reprise: RepriseOuverture | null;
  conseiller: string | null;
}

/** CU-9 : annulations que l'écran signale (pièce, selfie, questions : contrôle automatique). */
export type MotifAnnulation = 'CONTROLE_AUTOMATIQUE' | 'DEMANDE_CLIENTE';

/** Seul le contrôle en correspondance est en échec (NOK). */
export function controlesDe(resultat: ControleSanctionsReponse['resultat']): Controles {
  return {
    sanctions: resultat === 'CORRESPONDANCE_SANCTIONS' ? 'NOK' : 'OK',
    pep: resultat === 'CORRESPONDANCE_PEP' ? 'NOK' : 'OK',
  };
}

@Injectable({ providedIn: 'root' })
export class DossierService {
  private readonly http = inject(HttpClient);
  private readonly base = `${apiBase('backend')}/dossiers`;

  enregistrerIdentite(reference: string, identite: Identite): Observable<DossierReponse> {
    return this.http.put<DossierReponse>(`${this.base}/${encodeURIComponent(reference)}/identite`, {
      ...identite,
      dateNaissance: versIso(identite.dateNaissance),
      dateExpiration: versIso(identite.dateExpiration),
    });
  }

  enregistrerControles(reference: string, controles: Controles): Observable<DossierReponse> {
    return this.http.put<DossierReponse>(`${this.base}/${encodeURIComponent(reference)}/controles`, controles);
  }

  /** CU-9 : annulation du dossier par un contrôle des étapes pièce, selfie ou questions. */
  annuler(reference: string, motif: MotifAnnulation): Observable<DossierReponse> {
    return this.http.put<DossierReponse>(`${this.base}/${encodeURIComponent(reference)}/annulation`, { motif });
  }
}
