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

/**
 * CU-160 : solde à afficher, ou null si la valeur reçue est absente ou incohérente (négative,
 * non entière, pas un nombre) : la page affiche alors « Votre solde est momentanément
 * indisponible ». Le backend ne renvoie jamais de solde négatif ; l'écran ne se fie pas à lui seul.
 */
export function soldeAffichable(compte: Pick<Compte, 'soldeCentimes'> | null | undefined): string | null {
  const centimes: unknown = compte?.soldeCentimes;
  return typeof centimes === 'number' && Number.isSafeInteger(centimes) && centimes >= 0
    ? formaterSolde(centimes)
    : null;
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

// CU-11 : un virement reçu, tel qu'affiché dans la rubrique Versements.
// CU-144 : sortant -> montant débité, au profit de beneficiaire (emetteur vide) ; simule -> bouchon de test.
export interface Mouvement {
  date: string;
  libelle: string;
  emetteur: string | null;
  montantCentimes: number;
  sortant: boolean;
  beneficiaire: string | null;
  simule: boolean;
}

// CU-11 : IBAN complet à communiquer à l'émetteur (compte actif seulement) et derniers mouvements.
export interface RubriqueVersements {
  iban: string | null;
  mouvements: Mouvement[];
}

// CU-20 : les comptes de la cliente connectée (GET /comptes, GET /comptes/{id}).
// CU-11 : rubrique Versements (GET /comptes/{id}/versements).
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

  versements(id: string): Observable<RubriqueVersements> {
    return this.http.get<RubriqueVersements>(`${this.url}/${encodeURIComponent(id)}/versements`, {
      withCredentials: true,
    });
  }
}
