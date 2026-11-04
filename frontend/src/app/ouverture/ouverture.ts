import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { ResultatDocusign } from '../identification/bouchons-identite';
import { StatutDossier } from '../identification/dossier';

// Ouverture du compte, enregistrée par le backend principal :
// CU-12 signature de la convention (GET/POST /dossiers/{reference}/signature...)
// CU-13 compte ouvert et envois (GET /dossiers/{reference}/ouverture, POST .../envois/{canal})

export type EtatSignature = 'A_SIGNER' | 'ENVOYEE' | 'EXPIREE' | 'SIGNEE' | 'ANNULEE';

export interface SignatureReponse {
  etat: EtatSignature;
  enveloppeId: string | null;
  codesFaux: number;
  essaisRestants: number;
  nouveauCodePossible: boolean;
  motifAnnulation: string | null;
  signeeLe: string | null;
  convention: string;
  empreinteDocument: string;
  statutDossier: StatutDossier;
}

export type ResultatEnvoi = 'ENVOYE' | 'EN_ECHEC' | 'ECHEC';
export type Canal = 'EMAIL' | 'SMS';

export interface OuvertureReponse {
  statutDossier: StatutDossier;
  ibanMasque: string | null;
  emailMasque: string;
  mobileMasque: string;
  envoiEmail: ResultatEnvoi | null;
  envoiSms: ResultatEnvoi | null;
}

/** Clé du navigateur qui garde la référence du dossier en cours de signature (reprise après fermeture). */
export const CLE_DOSSIER_EN_SIGNATURE = 'compte-unique.dossier-en-signature';

@Injectable({ providedIn: 'root' })
export class OuvertureService {
  private readonly http = inject(HttpClient);
  private readonly base = `${apiBase('backend')}/dossiers`;

  private url(reference: string, chemin: string): string {
    return `${this.base}/${encodeURIComponent(reference)}/${chemin}`;
  }

  signature(reference: string): Observable<SignatureReponse> {
    return this.http.get<SignatureReponse>(this.url(reference, 'signature'));
  }

  enveloppeEnvoyee(reference: string, enveloppeId: string): Observable<SignatureReponse> {
    return this.http.post<SignatureReponse>(this.url(reference, 'signature/enveloppe'), { enveloppeId });
  }

  resultatSignature(reference: string, resultat: Exclude<ResultatDocusign, 'ENVELOPPE_ENVOYEE'>): Observable<SignatureReponse> {
    return this.http.post<SignatureReponse>(this.url(reference, 'signature/resultat'), { resultat });
  }

  ouverture(reference: string): Observable<OuvertureReponse> {
    return this.http.get<OuvertureReponse>(this.url(reference, 'ouverture'));
  }

  renvoyer(reference: string, canal: Canal): Observable<OuvertureReponse> {
    return this.http.post<OuvertureReponse>(this.url(reference, `envois/${canal}`), {});
  }
}

/** Le navigateur peut refuser le stockage (navigation privée) : la reprise est alors simplement perdue. */
export function memoriserDossierEnSignature(reference: string | null): void {
  try {
    if (reference) {
      localStorage.setItem(CLE_DOSSIER_EN_SIGNATURE, reference);
    } else {
      localStorage.removeItem(CLE_DOSSIER_EN_SIGNATURE);
    }
  } catch {
    // stockage indisponible
  }
}

export function dossierEnSignature(): string | null {
  try {
    return localStorage.getItem(CLE_DOSSIER_EN_SIGNATURE);
  } catch {
    return null;
  }
}
