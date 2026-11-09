import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { ComptesService, RubriqueVersements as Rubrique, formaterDate, formaterSolde } from '../comptes/comptes';
import { VirementsTestService, montantEnCentimes } from './virements-test';

type Message = { texte: string; erreur: boolean };

// CU-11 (US-06) : rubrique Versements de la page « Faire un versement » : IBAN complet à communiquer à l'émetteur
// d'un virement instantané (compte actif) et derniers mouvements (date, libellé, émetteur, montant).
// CU-143 (TECH-04) : en environnement de test seulement, bouton « Simuler un virement entrant », formulaire
// prérempli avec l'IBAN du compte. Aucun virement réel n'est émis.
@Component({
  selector: 'app-rubrique-versements',
  template: `
    <section class="versements" aria-label="Versements">
      @if (erreur()) {
        <p role="alert">Vos versements sont momentanément indisponibles.</p>
      } @else if (rubrique(); as r) {
        @if (r.iban) {
          <h3>IBAN de votre compte courant</h3>
          <p>Pour alimenter votre compte par virement instantané, communiquez cet IBAN à l’émetteur :</p>
          <p class="iban">{{ r.iban }}</p>
        }
        <h3>Mouvements</h3>
        @if (r.mouvements.length === 0) {
          <p class="vide">Aucun mouvement pour le moment.</p>
        } @else {
          <ul class="mouvements">
            @for (m of r.mouvements; track $index) {
              <li>
                <span class="date">{{ date(m.date) }}</span>
                <span class="libelle">{{ m.libelle }}<small>{{ m.emetteur }}</small></span>
                <span class="montant">+{{ montant(m.montantCentimes) }}</span>
              </li>
            }
          </ul>
        }
      }

      @if (bouchon()) {
        <div class="test">
          <button type="button" (click)="formulaire.set(!formulaire())">Simuler un virement entrant</button>
          <small>Environnement de test : données factices, aucun virement réel.</small>
          @if (formulaire()) {
            <form (submit)="simuler($event, iban.value, montantSaisi.value, emetteur.value, libelle.value)">
              <label for="sim-iban">IBAN du compte</label>
              <input #iban id="sim-iban" name="iban" [value]="rubrique()?.iban ?? ''" autocomplete="off" />
              <label for="sim-montant">Montant (€)</label>
              <input #montantSaisi id="sim-montant" name="montant" inputmode="decimal" autocomplete="off" />
              <label for="sim-emetteur">Nom de l’émetteur</label>
              <input #emetteur id="sim-emetteur" name="emetteur" maxlength="70" autocomplete="off" />
              <label for="sim-libelle">Libellé</label>
              <input #libelle id="sim-libelle" name="libelle" maxlength="140" autocomplete="off" />
              <button type="submit" [disabled]="envoi()">Simuler</button>
            </form>
          }
          @if (message(); as m) {
            <p [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .versements { background: #fff; border: 1px solid #e5eaf3; border-radius: 20px; padding: 24px; }
    h3 { color: #0b2545; font-size: 16px; margin: 20px 0 8px; }
    h3:first-child { margin-top: 0; }
    p { color: #5d6887; line-height: 1.6; }
    .iban { font-family: ui-monospace, monospace; font-size: 18px; font-weight: 700; color: #0f1b33; letter-spacing: .04em; overflow-wrap: anywhere; }
    .mouvements { list-style: none; padding: 0; margin: 0; }
    .mouvements li { display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: baseline; padding: 10px 0; border-bottom: 1px solid #e3e8f2; }
    .date { color: #5d6887; font-size: 13px; white-space: nowrap; }
    .libelle { color: #0f1b33; overflow-wrap: anywhere; }
    .libelle small { display: block; color: #5d6887; }
    .montant { color: #0a7d63; font-weight: 700; white-space: nowrap; font-variant-numeric: tabular-nums; }
    .test { margin-top: 24px; padding: 16px; border: 1px dashed #b7c3d9; border-radius: 12px; }
    .test small { display: block; color: #5d6887; margin-top: 6px; }
    form { display: grid; gap: 6px; margin-top: 12px; max-width: 420px; }
    label { font-size: 14px; color: #0f1b33; margin-top: 6px; }
    input { padding: 8px 10px; border: 1px solid #b7c3d9; border-radius: 8px; font: inherit; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 0; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button[disabled] { opacity: .6; cursor: default; }
    .ko { color: #b3261e; }
    @media (max-width: 600px) {
      .mouvements li { grid-template-columns: 1fr auto; }
      .date { grid-column: 1 / -1; }
    }
  `,
})
export class RubriqueVersements implements OnInit {
  readonly compteId = input.required<string>();
  /** Un virement simulé a été crédité : la page recharge le compte pour afficher le nouveau solde. */
  readonly credite = output<void>();

  private readonly comptes = inject(ComptesService);
  private readonly virements = inject(VirementsTestService);

  protected readonly rubrique = signal<Rubrique | null>(null);
  protected readonly erreur = signal(false);
  protected readonly bouchon = signal(false);
  protected readonly formulaire = signal(false);
  protected readonly envoi = signal(false);
  protected readonly message = signal<Message | null>(null);

  protected readonly date = formaterDate;
  protected readonly montant = formaterSolde;

  ngOnInit(): void {
    this.charger();
    this.virements.disponible().subscribe((disponible) => this.bouchon.set(disponible));
  }

  private charger(): void {
    this.comptes.versements(this.compteId()).subscribe({
      next: (rubrique) => {
        this.erreur.set(false);
        this.rubrique.set(rubrique);
      },
      error: (erreur: HttpErrorResponse) => {
        // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
        if (erreur.status !== 401) {
          this.erreur.set(true);
        }
      },
    });
  }

  protected simuler(evenement: Event, iban: string, montant: string, emetteur: string, libelle: string): void {
    evenement.preventDefault();
    const montantCentimes = montantEnCentimes(montant);
    if (montantCentimes === null) {
      this.message.set({ texte: 'Montant invalide', erreur: true });
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.virements.simuler({ iban, montantCentimes, emetteur, libelle }).subscribe({
      next: (resultat) => {
        this.envoi.set(false);
        this.message.set({ texte: resultat.message, erreur: resultat.resultat !== 'CREDITE' });
        if (resultat.resultat === 'CREDITE') {
          this.charger();
          this.credite.emit();
        }
      },
      error: (erreur: HttpErrorResponse) => {
        this.envoi.set(false);
        const texte: unknown = erreur.error?.message;
        this.message.set({
          texte: typeof texte === 'string' ? texte : 'La simulation n’a pas pu aboutir. Veuillez réessayer.',
          erreur: true,
        });
      },
    });
  }
}
