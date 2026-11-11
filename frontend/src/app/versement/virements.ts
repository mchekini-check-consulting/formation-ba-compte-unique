import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

// CU-147 (US-21) : montant en euros tel que saisi (« 12,50 ») : le backend contrôle les décimales.
export interface DemandeVirement {
  beneficiaireId: number;
  montant: string;
  libelle: string;
}

// Critère 9 : récapitulatif affiché avant la saisie du code SMS (IBAN masqué).
export interface Recapitulatif {
  virementId: number;
  beneficiaire: string;
  nomBeneficiaire: string;
  ibanMasque: string;
  montantCentimes: number;
  libelle: string;
  message: string;
}

export interface Execution {
  message: string;
  soldeCentimes: number;
}

// CU-147 : virement vers un bénéficiaire (POST /virements, POST /virements/{id}/signature), avec le cookie de
// session. Bouchon de test (dev, qualif, local) : refus simulé de la banque destinataire (critère 14).
@Injectable({ providedIn: 'root' })
export class VirementsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/virements`;
  private readonly urlTest = `${apiBase('backend')}/test/virements`;

  preparer(demande: DemandeVirement): Observable<Recapitulatif> {
    return this.http.post<Recapitulatif>(this.url, demande, { withCredentials: true });
  }

  signer(virementId: number, code: string): Observable<Execution> {
    return this.http.post<Execution>(`${this.url}/${virementId}/signature`, { code }, { withCredentials: true });
  }

  simulerRefus(virementId: number, motif: string): Observable<Execution> {
    return this.http.post<Execution>(`${this.urlTest}/${virementId}/refus`, { motif });
  }
}
