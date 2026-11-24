import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** Date métier en vigueur (CU-380). */
export interface EtatDateMetier {
  dateMetier: string;
  simulee: boolean;
  modifiable: boolean;
}

/** Un changement de date métier tracé. */
export interface ChangementDateMetier {
  ancienneDate: string;
  nouvelleDate: string;
  auteur: string;
  changeLe: string;
}

// CU-380 (TECH-12) : routes /test/date-metier du backend (changement refusé en preprod et prod : 403).
@Injectable({ providedIn: 'root' })
export class DateMetierService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/test/date-metier`;

  lire(): Observable<EtatDateMetier> {
    return this.http.get<EtatDateMetier>(this.url);
  }

  /** date au format « AAAA-MM-JJTHH:MM », heure de Paris. */
  changer(date: string, auteur: string): Observable<EtatDateMetier> {
    return this.http.put<EtatDateMetier>(this.url, { date, auteur });
  }

  revenirALaDateReelle(auteur: string): Observable<EtatDateMetier> {
    return this.http.post<EtatDateMetier>(`${this.url}/date-reelle`, { auteur });
  }

  journal(): Observable<ChangementDateMetier[]> {
    return this.http.get<ChangementDateMetier[]>(`${this.url}/journal`);
  }
}

/** Date lisible à l'heure de Paris, ex. « jeudi 31 décembre 2026 à 11:00 ». */
export function afficherDate(iso: string): string {
  return new Date(iso).toLocaleString('fr-FR', {
    timeZone: 'Europe/Paris',
    dateStyle: 'full',
    timeStyle: 'short',
  });
}
