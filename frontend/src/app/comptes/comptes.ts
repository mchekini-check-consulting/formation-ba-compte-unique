import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export interface Compte {
  id: number;
  ibanMasque: string;
  statut: string;
  soldeCentimes: number | null;
  soldeMisAJourLe: string | null;
  premierVersementAttendu: boolean;
}

const LIBELLES_STATUT: Record<string, string> = { ACTIF: 'Actif', CLOTURE: 'Clôturé' };

export function libelleStatut(statut: string): string {
  return LIBELLES_STATUT[statut] ?? statut;
}

// CU-19 : 124050 centimes -> « 1 240,50 € » (espaces insécables, calcul en nombres entiers).
export function formaterSolde(centimes: number): string {
  const euros = String(Math.trunc(centimes / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  return `${euros},${String(centimes % 100).padStart(2, '0')} €`;
}

const FORMAT_DATE_PARIS = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

// CU-19 : « jj/mm/aaaa à hh:mm », heure de Paris.
export function formaterDate(date: string): string {
  const parties = FORMAT_DATE_PARIS.formatToParts(new Date(date));
  const partie = (type: Intl.DateTimeFormatPartTypes) => parties.find((p) => p.type === type)?.value;
  return `${partie('day')}/${partie('month')}/${partie('year')} à ${partie('hour')}:${partie('minute')}`;
}

// CU-20 : les comptes de la cliente connectée (GET /comptes, GET /comptes/{id}).
@Injectable({ providedIn: 'root' })
export class ComptesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/comptes`;

  mesComptes(): Observable<Compte[]> {
    return this.http.get<Compte[]>(this.url, { withCredentials: true });
  }

  consulter(id: string): Observable<Compte> {
    return this.http.get<Compte>(`${this.url}/${encodeURIComponent(id)}`, { withCredentials: true });
  }
}
