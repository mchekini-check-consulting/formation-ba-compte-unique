import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { apiBase } from '../api';

export type ResultatVerification = 'CORRESPOND' | 'NE_CORRESPOND_PAS' | 'INTROUVABLE' | 'INDISPONIBLE';

export interface AppelVerification {
  appeleLe: string;
  iban: string;
  resultat: ResultatVerification;
}

// Compte fictif d'une autre banque connu du bouchon, utilisable pour ajouter un bénéficiaire en test.
export interface CompteExterne {
  iban: string;
  banque: string;
  titulaire: string;
}

export interface EtatBouchon {
  indisponible: boolean;
  appels: AppelVerification[];
  comptesExternes: CompteExterne[];
}

export const LIBELLES_RESULTAT: Record<ResultatVerification, string> = {
  CORRESPOND: 'correspond',
  NE_CORRESPOND_PAS: 'ne correspond pas',
  INTROUVABLE: 'introuvable',
  INDISPONIBLE: 'indisponible',
};

type Etat = 'chargement' | 'affiche' | 'indisponible';

// CU-145 (TECH-06) : page /test/verification-beneficiaire. En environnement de test (dev, qualif, local), le bouchon
// « Vérification du Bénéficiaire (VoP) » (Verification of Payee, obligatoire en zone euro depuis le 9 octobre 2025) :
// sélecteur « Service indisponible », essai d'une vérification (IBAN de test
// et nom) et journal de test (date, IBAN, résultat). Le backend répond 404 en preprod et prod : la page l'indique.
@Component({
  selector: 'app-verification-test',
  imports: [RouterLink],
  template: `
    <main class="verification-test">
      <a routerLink="/">← Retour au site</a>
      <h1>Vérification du Bénéficiaire (VoP) — bouchon de test</h1>
      <p class="muted">
        Les banques vérifient l’exactitude d’un IBAN et de son titulaire en temps réel grâce à un protocole interbancaire
        standardisé appelé Verification of Payee (VoP) (ou Vérification du Bénéficiaire), obligatoire et gratuit dans
        toute la zone euro depuis le 9 octobre 2025.
      </p>
      <p class="muted">
        Ce bouchon simule la VoP : un IBAN de test correspond au compte de test de même IBAN ; son titulaire est le prénom et le
        nom du dossier (par exemple « Test Cliente-B » pour FR76 9999 9000 0199 0000 0000 202). Des comptes fictifs
        d’autres banques sont aussi connus (liste ci-dessous).
      </p>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="vide" role="status">Bouchon non disponible dans cet environnement.</p>
        }
        @case ('affiche') {
          <label class="selecteur">
            <input type="checkbox" [checked]="indisponible()" (change)="basculer($any($event.target).checked)" />
            Service indisponible
          </label>

          <form (submit)="verifier($event, iban.value, nom.value)">
            <label for="verif-iban">IBAN</label>
            <input #iban id="verif-iban" name="iban" autocomplete="off" />
            <label for="verif-nom">Nom</label>
            <input #nom id="verif-nom" name="nom" autocomplete="off" />
            <button type="submit">Vérifier</button>
          </form>
          @if (resultat(); as r) {
            <p class="resultat" role="status">{{ r }}</p>
          }

          <h2>Comptes d’autres banques connus du bouchon</h2>
          <p class="muted">À utiliser pour ajouter un bénéficiaire hors de notre banque. Tout autre IBAN est « introuvable ».</p>
          <div class="tableau">
            <table class="externes">
              <thead>
                <tr>
                  <th scope="col">IBAN</th>
                  <th scope="col">Banque</th>
                  <th scope="col">Titulaire</th>
                </tr>
              </thead>
              <tbody>
                @for (compte of comptesExternes(); track compte.iban) {
                  <tr>
                    <td>{{ compte.iban }}</td>
                    <td>{{ compte.banque }}</td>
                    <td>{{ compte.titulaire }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <h2>Journal de test</h2>
          @if (appels().length === 0) {
            <p class="vide">Aucun appel.</p>
          } @else {
            <div class="tableau">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col">IBAN</th>
                    <th scope="col">Résultat</th>
                  </tr>
                </thead>
                <tbody>
                  @for (appel of appels(); track $index) {
                    <tr>
                      <td>{{ appel.appeleLe }}</td>
                      <td>{{ appel.iban }}</td>
                      <td>{{ libelle(appel.resultat) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }
      }
    </main>
  `,
  styles: `
    .verification-test { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1, h2 { color: #0b2545; margin: 24px 0 12px; }
    .muted, .vide { color: #5d6887; line-height: 1.6; }
    .selecteur { display: flex; gap: 8px; align-items: center; font-weight: 700; margin: 16px 0; }
    form { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; margin: 16px 0; }
    form label { font-weight: 700; }
    input:not([type=checkbox]) { padding: 9px 12px; border: 1px solid #d9dee6; border-radius: 10px; min-width: 0; }
    button { padding: 10px 18px; border-radius: 10px; border: 1px solid #0b2545; background: #fff; color: #0b2545; font-weight: 700; cursor: pointer; }
    .resultat { font-weight: 700; }
    .tableau { overflow-x: auto; border: 1px solid #d9dee6; border-radius: 12px; background: #fff; }
    table { border-collapse: collapse; width: 100%; font-size: 14px; }
    th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eef1f5; white-space: nowrap; }
    th { background: #f4f6fa; }
  `,
})
export class VerificationTest {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('backend')}/test/verification-beneficiaire`;

  protected readonly etat = signal<Etat>('chargement');
  protected readonly indisponible = signal(false);
  protected readonly appels = signal<AppelVerification[]>([]);
  protected readonly comptesExternes = signal<CompteExterne[]>([]);
  protected readonly resultat = signal<string | null>(null);

  constructor() {
    this.charger();
  }

  protected libelle(resultat: ResultatVerification): string {
    return LIBELLES_RESULTAT[resultat] ?? resultat;
  }

  private afficher(etat: EtatBouchon): void {
    this.indisponible.set(etat.indisponible);
    this.appels.set(etat.appels);
    this.comptesExternes.set(etat.comptesExternes ?? []);
    this.etat.set('affiche');
  }

  private charger(): void {
    this.http.get<EtatBouchon>(this.url).subscribe({
      next: (etat) => this.afficher(etat),
      error: () => this.etat.set('indisponible'),
    });
  }

  protected basculer(indisponible: boolean): void {
    this.http.put<EtatBouchon>(`${this.url}/indisponible`, { indisponible }).subscribe({
      next: (etat) => this.afficher(etat),
      error: () => this.etat.set('indisponible'),
    });
  }

  protected verifier(evenement: Event, iban: string, nom: string): void {
    evenement.preventDefault();
    this.http.post<{ resultat: ResultatVerification; nomVerifie: string | null }>(this.url, { iban, nom }).subscribe({
      next: (verification) => {
        const nomVerifie = verification.nomVerifie ? ` : ${verification.nomVerifie}` : '';
        this.resultat.set(`Résultat : ${this.libelle(verification.resultat)}${nomVerifie}`);
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => {
        const message: unknown = erreur.error?.message;
        this.resultat.set(typeof message === 'string' ? message : 'La vérification n’a pas pu aboutir.');
      },
    });
  }
}
