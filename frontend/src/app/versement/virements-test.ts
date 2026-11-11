import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { apiBase } from '../api';

export interface VirementSimule {
  iban: string;
  montantCentimes: number;
  emetteur: string;
  libelle: string;
}

export interface ResultatSimulation {
  resultat: 'CREDITE' | 'RENVOYE';
  message: string;
}

export interface VirementSortantSimule {
  /** IBAN du compte débité. */
  iban: string;
  montantCentimes: number;
  beneficiaire: string;
  ibanBeneficiaire: string;
  libelle: string;
}

export interface ResultatVirementSortant {
  message: string;
  soldeCentimes: number;
}

/**
 * Montant saisi en euros -> centimes (« 12,50 » -> 1250, « 1 000 » -> 100000). null si la saisie n'est pas un
 * montant supérieur à 0 avec deux chiffres au plus après la virgule : la page affiche alors « Montant invalide ».
 */
export function montantEnCentimes(saisie: string): number | null {
  const texte = saisie.replace(/[\s  ]/g, '').replace(',', '.');
  const montant = /^(\d{1,13})(?:\.(\d{1,2}))?$/.exec(texte);
  if (!montant) {
    return null;
  }
  const centimes = Number(montant[1]) * 100 + Number((montant[2] ?? '').padEnd(2, '0'));
  return centimes > 0 ? centimes : null;
}

// CU-143 (TECH-04) : bouchon « Simuler un virement entrant », en dev, qualif et local seulement. Le backend
// n'a pas ce point d'accès en preprod et prod (404) : disponible() renvoie alors false et le bouton est masqué.
// CU-144 (TECH-05) : bouchon « Simuler un virement sortant », même interrupteur côté backend : disponible()
// vaut pour les deux bouchons.
@Injectable({ providedIn: 'root' })
export class VirementsTestService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/test/virements-entrants`;
  private readonly urlSortants = `${apiBase('backend')}/test/virements-sortants`;

  disponible(): Observable<boolean> {
    return this.http.get(this.url, { observe: 'response' }).pipe(
      map(() => true),
      catchError(() => of(false)),
    );
  }

  simuler(virement: VirementSimule): Observable<ResultatSimulation> {
    return this.http.post<ResultatSimulation>(this.url, virement);
  }

  simulerSortant(virement: VirementSortantSimule): Observable<ResultatVirementSortant> {
    return this.http.post<ResultatVirementSortant>(this.urlSortants, virement);
  }
}
