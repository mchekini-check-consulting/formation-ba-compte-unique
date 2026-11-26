import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { refusDe } from '../beneficiaires/beneficiaires';
import { Compte, ComptesService, formaterSolde } from '../comptes/comptes';
import { DemandeLivretA, LivretAService } from './livret-a';
import { LIVRET_A, eurosAuCentime, pourcentage, simuler } from './parametres';

/** Mêmes messages que le backend (CU-33). Minimum et plafond repris des paramètres du Livret A (CU-27). */
export const MINIMUM = `Le versement minimum à l'ouverture est de ${LIVRET_A.versementMinimum} €`;
export const PLAFOND = 'Le plafond du Livret A est de 22 950 €';
export const MONTANT_INVALIDE = 'Montant invalide : saisissez un montant en euros, par exemple 150 ou 150,50';
export const SOLDE_INSUFFISANT = 'Solde de votre compte courant insuffisant';
export const SANS_COMPTE_COURANT = 'Votre compte courant est momentanément indisponible : le versement ne peut pas être choisi.';
const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';
const MONTANT = /^\d+([.,]\d{1,2})?$/;

/** Montant saisi -> centimes, ou message de refus (mêmes règles que le backend). */
export function lireMontant(saisie: string, soldeCentimes: number | null): { centimes: number } | { erreur: string } {
  const lu = saisie.replace(/[\s  ]/g, '');
  if (!lu) {
    return { erreur: MINIMUM };
  }
  if (!MONTANT.test(lu)) {
    return { erreur: MONTANT_INVALIDE };
  }
  const [entier, decimales = ''] = lu.split(/[.,]/);
  if (entier.replace(/^0+/, '').length > 6) {
    return { erreur: PLAFOND };
  }
  const centimes = Number(entier) * 100 + Number(decimales.padEnd(2, '0'));
  if (centimes > LIVRET_A.plafond * 100) {
    return { erreur: PLAFOND };
  }
  if (centimes < LIVRET_A.versementMinimum * 100) {
    return { erreur: MINIMUM };
  }
  if (soldeCentimes !== null && soldeCentimes < centimes) {
    return { erreur: SOLDE_INSUFFISANT };
  }
  return { centimes };
}

// CU-33 (US-24) : versement initial du Livret A, une fois l'ouverture validée. Seul le compte courant Hi-Tech
// Bank est proposé ; le montant (10 € à 22 950 €, au plus le solde) est enregistré dans la demande puis affiché
// avant la signature, avec le taux et l'estimation des intérêts annuels du simulateur (CU-27). Le débit du compte
// courant et la création du Livret A ont lieu à la signature (CU-34). Émet la demande mise à jour.
@Component({
  selector: 'app-versement-initial-livret-a',
  template: `
    <div class="versement">
      <h3>Mon versement initial</h3>
      @if (erreur(); as e) {
        <p class="ko" role="alert">{{ e }}</p>
      }
      @if (recapitulatif() && !modification()) {
        <div class="recap">
          <dl>
            <div><dt>Versement initial</dt><dd>{{ montantAffiche() }}</dd></div>
            <div><dt>Compte débité</dt><dd>Compte courant Hi-Tech Bank {{ compte()?.ibanMasque ?? '' }}</dd></div>
            <div><dt>Taux en vigueur</dt><dd>{{ taux }}</dd></div>
            <div><dt>Intérêts estimés sur un an</dt><dd>{{ interetsAffiches() }}</dd></div>
          </dl>
          <p class="muted">Le compte courant sera débité à la signature de votre convention.</p>
          <p class="suite">Prochaine étape : la signature de votre convention.</p>
          <button type="button" class="secondaire" (click)="modification.set(true)">Modifier le montant</button>
        </div>
      } @else if (compte(); as c) {
        <form (submit)="valider($event)" novalidate>
          <fieldset>
            <legend>Compte à débiter</legend>
            <label class="case">
              <input type="radio" name="source" checked disabled />
              Compte courant Hi-Tech Bank {{ c.ibanMasque }}
              @if (solde(); as s) {
                <span class="muted">· solde {{ s }}</span>
              }
            </label>
          </fieldset>
          <div class="champ">
            <label for="montant-versement">Montant du versement initial (de {{ minimum }} € à 22 950 €)</label>
            <input
              id="montant-versement"
              name="montant"
              inputmode="decimal"
              autocomplete="off"
              [value]="saisie()"
              [attr.aria-invalid]="erreurMontant() ? 'true' : null"
              [attr.aria-describedby]="erreurMontant() ? 'err-montant' : null"
              (input)="saisie.set($any($event.target).value)"
            />
            @if (erreurMontant(); as e) {
              <p class="ko" id="err-montant">{{ e }}</p>
            }
          </div>
          <button type="submit" [disabled]="envoi()">Valider ce montant</button>
        </form>
      } @else if (compteLu()) {
        <p class="ko" role="alert">{{ sansCompte }}</p>
      }
    </div>
  `,
  styles: `
    .versement { display: grid; gap: 10px; margin-top: 8px; padding: 14px; border: 1px solid #d5ddeb; border-radius: 12px; max-width: 560px; }
    h3 { color: #0b2545; font-size: 16px; margin: 0; }
    form { display: grid; gap: 12px; }
    fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: 6px; }
    legend { color: #0f1b33; font-weight: 700; margin-bottom: 4px; }
    .case { display: flex; gap: 8px; align-items: baseline; color: #0f1b33; font-size: 14px; flex-wrap: wrap; }
    .champ { display: grid; gap: 4px; }
    label { color: #0f1b33; font-size: 14px; }
    input:not([type="radio"]) { font: inherit; padding: 9px 11px; border: 1px solid #b9c4d6; border-radius: 8px; max-width: 220px; }
    input[aria-invalid="true"] { border-color: #b3261e; }
    dl { display: grid; gap: 6px; margin: 0; }
    dl div { display: flex; gap: 8px; flex-wrap: wrap; }
    dt { color: #5d6887; min-width: 200px; }
    dd { margin: 0; color: #0f1b33; font-weight: 600; }
    .recap { display: grid; gap: 8px; }
    .suite { color: #0a7d63; font-weight: 600; margin: 0; }
    .muted { color: #5d6887; font-size: 13px; margin: 0; }
    .ko { color: #b3261e; font-size: 13px; margin: 0; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button.secondaire { background: #fff; color: #0b2545; }
    button[disabled] { opacity: .6; cursor: default; }
  `,
})
export class VersementInitialLivretA implements OnInit {
  private readonly service = inject(LivretAService);
  private readonly comptes = inject(ComptesService);

  /** Demande dont l'ouverture est validée. */
  readonly demande = input.required<DemandeLivretA>();
  /** Demande mise à jour avec le versement enregistré. */
  readonly demandeChange = output<DemandeLivretA>();

  protected readonly taux = pourcentage(LIVRET_A.taux);
  protected readonly minimum = LIVRET_A.versementMinimum;
  protected readonly sansCompte = SANS_COMPTE_COURANT;
  protected readonly compte = signal<Compte | null>(null);
  protected readonly compteLu = signal(false);
  protected readonly saisie = signal('');
  protected readonly erreurMontant = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly envoi = signal(false);
  /** Récapitulatif affiché, ou formulaire rouvert pour changer de montant. */
  protected readonly modification = signal(false);

  protected readonly recapitulatif = computed(() => this.demande().versementInitialCentimes ?? null);
  protected readonly montantAffiche = computed(() => formaterSolde(this.recapitulatif() ?? 0));
  protected readonly interetsAffiches = computed(() => {
    const interets = simuler((this.recapitulatif() ?? 0) / 100)?.interets;
    return interets === null || interets === undefined ? '—' : eurosAuCentime(interets);
  });
  protected readonly solde = computed(() => {
    const centimes = this.compte()?.soldeCentimes;
    return typeof centimes === 'number' && centimes >= 0 ? formaterSolde(centimes) : null;
  });

  ngOnInit(): void {
    this.comptes.mesComptes().subscribe({
      next: (comptes) => {
        this.compte.set(comptes.find((c) => c.statut === 'ACTIF') ?? null);
        this.compteLu.set(true);
      },
      error: () => this.compteLu.set(true),
    });
  }

  protected valider(evenement: Event): void {
    evenement.preventDefault();
    const soldeCentimes = this.compte()?.soldeCentimes;
    const lu = lireMontant(this.saisie(), typeof soldeCentimes === 'number' ? soldeCentimes : null);
    if ('erreur' in lu) {
      this.erreurMontant.set(lu.erreur);
      return;
    }
    this.erreurMontant.set(null);
    this.erreur.set(null);
    this.envoi.set(true);
    this.service.choisirVersementInitial(this.demande().reference, this.saisie().trim()).subscribe({
      next: (demande) => {
        this.envoi.set(false);
        this.modification.set(false);
        this.demandeChange.emit(demande);
      },
      error: (erreur: HttpErrorResponse) => {
        this.envoi.set(false);
        // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
        if (erreur.status !== 401) {
          this.erreur.set(refusDe(erreur, INDISPONIBLE).texte);
        }
      },
    });
  }
}
