import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Beneficiaire, BeneficiairesService, refusDe } from '../beneficiaires/beneficiaires';
import { formaterSolde } from '../comptes/comptes';
import { montantEnCentimes } from './virements-test';
import { Recapitulatif, VirementsService } from './virements';

type Message = { texte: string; erreur: boolean };
type Liste = { lue: false } | { lue: true; erreur: boolean; beneficiaires: Beneficiaire[] };

const INDISPONIBLE = 'Le virement est momentanément indisponible. Veuillez réessayer plus tard.';
const LIBELLE_MAX = 140;

// CU-147 (US-21) : « Faire un virement » vers un de mes bénéficiaires (CU-146), sur la page Virement.
// 1. Choix d'un bénéficiaire de la liste : son nom et son IBAN masqué sont affichés, jamais ressaisis (critère 2).
// 2. Montant et libellé ; contrôles immédiats (montant, libellé), puis ceux du backend (solde, premier virement...).
// 3. Récapitulatif et code reçu par SMS : le virement n'est exécuté qu'avec le bon code (critères 9 à 12).
@Component({
  selector: 'app-formulaire-virement',
  imports: [RouterLink],
  template: `
    <section class="virement" aria-label="Virement vers un bénéficiaire">
      <h3>Virement vers un bénéficiaire</h3>
      @if (message(); as m) {
        <p class="message" [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
      }
      @if (liste(); as l) {
        @if (!l.lue) {
          <p>Chargement…</p>
        } @else if (l.erreur) {
          <p role="alert">Vos bénéficiaires sont momentanément indisponibles.</p>
        } @else if (l.beneficiaires.length === 0) {
          <p class="vide">
            Aucun bénéficiaire enregistré. <a routerLink="/beneficiaires">Ajoutez un bénéficiaire</a> pour lui faire un virement.
          </p>
        } @else if (recap(); as r) {
          <form class="signature" (submit)="signer($event, code.value)">
            <dl class="recap">
              <dt>Bénéficiaire</dt><dd>{{ r.beneficiaire }} ({{ r.nomBeneficiaire }})</dd>
              <dt>IBAN</dt><dd>{{ r.ibanMasque }}</dd>
              <dt>Montant</dt><dd>{{ euros(r.montantCentimes) }}</dd>
              <dt>Libellé</dt><dd>{{ r.libelle }}</dd>
            </dl>
            <p>{{ r.message }}</p>
            <label for="vir-code">Code reçu par SMS</label>
            <input #code id="vir-code" name="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
            <div class="actions">
              <button type="submit" [disabled]="envoi()">Valider le virement</button>
              <button type="button" class="secondaire" (click)="annuler()">Annuler</button>
            </div>
          </form>
        } @else {
          <form class="saisie" (submit)="preparer($event, montant.value, libelle.value)">
            <label for="vir-beneficiaire">Bénéficiaire</label>
            <select #choix id="vir-beneficiaire" name="beneficiaire" (change)="choisir(choix.value)">
              @for (b of l.beneficiaires; track b.id) {
                <option [value]="b.id" [selected]="b.id === choisi()?.id">{{ b.libelle }}</option>
              }
            </select>
            @if (choisi(); as b) {
              <p class="verifie">{{ b.nomVerifie }} · {{ b.ibanMasque }}</p>
            }
            <label for="vir-montant">Montant (€)</label>
            <input #montant id="vir-montant" name="montant" inputmode="decimal" autocomplete="off" />
            <label for="vir-libelle">Libellé</label>
            <input #libelle id="vir-libelle" name="libelle" autocomplete="off" />
            <button type="submit" [disabled]="envoi()">Continuer</button>
          </form>
        }
      }
    </section>
  `,
  styles: `
    .virement { background: #fff; border: 1px solid #e5eaf3; border-radius: 20px; padding: 24px; margin-bottom: 20px; }
    h3 { color: #0b2545; font-size: 16px; margin: 0 0 12px; }
    p { color: #5d6887; line-height: 1.6; margin: 4px 0; }
    form { display: grid; gap: 6px; max-width: 420px; }
    label { font-size: 14px; color: #0f1b33; margin-top: 6px; }
    input, select { padding: 8px 10px; border: 1px solid #b7c3d9; border-radius: 8px; font: inherit; min-width: 0; background: #fff; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button.secondaire { background: #fff; color: #0b2545; }
    button[disabled] { opacity: .6; cursor: default; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }
    .verifie { font-size: 14px; }
    .recap { display: grid; grid-template-columns: auto 1fr; gap: 4px 16px; margin: 0 0 8px; }
    .recap dt { color: #5d6887; }
    .recap dd { margin: 0; color: #0f1b33; font-weight: 600; overflow-wrap: anywhere; }
    .message { font-weight: 600; color: #0a7d63; }
    .ko { color: #b3261e; }
  `,
})
export class FormulaireVirement implements OnInit {
  /** Virement exécuté : la page recharge le solde et les mouvements. */
  readonly execute = output<void>();

  private readonly beneficiaires = inject(BeneficiairesService);
  private readonly virements = inject(VirementsService);

  protected readonly liste = signal<Liste>({ lue: false });
  protected readonly choixId = signal<number | null>(null);
  protected readonly recap = signal<Recapitulatif | null>(null);
  protected readonly envoi = signal(false);
  protected readonly message = signal<Message | null>(null);
  protected readonly euros = formaterSolde;

  /** Bénéficiaire choisi : le premier de la liste par défaut. */
  protected readonly choisi = computed(() => {
    const liste = this.liste();
    if (!liste.lue) {
      return null;
    }
    return liste.beneficiaires.find((b) => b.id === this.choixId()) ?? liste.beneficiaires[0] ?? null;
  });

  ngOnInit(): void {
    this.beneficiaires.lister().subscribe({
      next: (beneficiaires) => this.liste.set({ lue: true, erreur: false, beneficiaires }),
      error: (erreur: HttpErrorResponse) => {
        // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
        if (erreur.status !== 401) {
          this.liste.set({ lue: true, erreur: true, beneficiaires: [] });
        }
      },
    });
  }

  protected choisir(id: string): void {
    this.choixId.set(Number(id));
  }

  // Critères 3 et 7 contrôlés tout de suite ; les autres par le backend (message exact du ticket).
  protected preparer(evenement: Event, montant: string, libelle: string): void {
    evenement.preventDefault();
    const beneficiaire = this.choisi();
    if (!beneficiaire) {
      return;
    }
    if (montantEnCentimes(montant) === null) {
      this.message.set({ texte: 'Montant invalide', erreur: true });
      return;
    }
    if (libelle.trim().length > LIBELLE_MAX) {
      this.message.set({ texte: 'Libellé trop long', erreur: true });
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.virements.preparer({ beneficiaireId: beneficiaire.id, montant, libelle }).subscribe({
      next: (recap) => {
        this.envoi.set(false);
        this.recap.set(recap);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  protected signer(evenement: Event, code: string): void {
    evenement.preventDefault();
    const recap = this.recap();
    if (!recap) {
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.virements.signer(recap.virementId, code.trim()).subscribe({
      next: (execution) => {
        this.envoi.set(false);
        this.recap.set(null);
        this.message.set({ texte: execution.message, erreur: false });
        this.execute.emit();
      },
      error: (erreur: HttpErrorResponse) => {
        if (this.refus(erreur)?.termine) {
          this.recap.set(null);
        }
      },
    });
  }

  protected annuler(): void {
    this.recap.set(null);
    this.message.set(null);
  }

  private refus(erreur: HttpErrorResponse): { termine: boolean } | null {
    this.envoi.set(false);
    // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
    if (erreur.status === 401) {
      return null;
    }
    const refus = refusDe(erreur, INDISPONIBLE);
    this.message.set({ texte: refus.texte, erreur: true });
    return refus;
  }
}
