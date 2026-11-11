import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ElementRef,
  OnInit,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import {
  ComptesService,
  Mouvement,
  RubriqueVersements as Rubrique,
  formaterDate,
  formaterSolde,
} from '../comptes/comptes';
import { VirementsService } from './virements';
import { VirementsTestService, montantEnCentimes } from './virements-test';

type Message = { texte: string; erreur: boolean };

// CU-11 (US-06) : rubrique Versements de la page « Faire un virement » : IBAN complet à communiquer à l'émetteur
// d'un virement instantané (compte actif) et derniers mouvements (date, libellé, émetteur, montant).
// CU-143 (TECH-04) : en environnement de test seulement, bouton « Simuler un virement entrant », formulaire
// prérempli avec l'IBAN du compte. Aucun virement réel n'est émis.
// Les virements sortants s'affichent en « − » avec leur bénéficiaire, et tout mouvement simulé porte la mention
// « simulé ». Le bouchon « Simuler un virement sortant » (CU-144) a été retiré : un virement sortant ne part que
// vers un bénéficiaire (CU-147, formulaire de la page).
// « Historique des mouvements » : les 5 derniers mouvements, et « Voir tout » ouvre une fenêtre avec tout l'historique
// renvoyé par le backend (50 derniers mouvements), fermée par ✕, la touche Échap ou un clic à côté.
@Component({
  selector: 'app-rubrique-versements',
  imports: [NgTemplateOutlet],
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
        <div class="entete">
          <h3>Historique des mouvements</h3>
          @if (r.mouvements.length > 0) {
            <button type="button" class="voir-tout" (click)="ouvrirHistorique()">Voir tout</button>
          }
        </div>
        @if (r.mouvements.length === 0) {
          <p class="vide">Aucun mouvement pour le moment.</p>
        } @else {
          <ul class="mouvements">
            @for (m of r.mouvements.slice(0, apercu); track $index) {
              <li>
                <ng-container *ngTemplateOutlet="ligne; context: { $implicit: m }" />
              </li>
            }
          </ul>
        }
        @if (historique()) {
          <div class="voile" (click)="fermerHistorique()"></div>
          <div
            class="historique"
            role="dialog"
            aria-modal="true"
            aria-labelledby="titre-historique"
            (keydown.escape)="fermerHistorique()"
          >
            <div class="entete">
              <h3 id="titre-historique">Historique des mouvements</h3>
              <button #fermer type="button" class="fermer" aria-label="Fermer" (click)="fermerHistorique()">✕</button>
            </div>
            <p class="nombre">{{ r.mouvements.length }} mouvement{{ r.mouvements.length > 1 ? 's' : '' }}, du plus récent au plus ancien.</p>
            <ul class="mouvements">
              @for (m of r.mouvements; track $index) {
                <li>
                  <ng-container *ngTemplateOutlet="ligne; context: { $implicit: m }" />
                </li>
              }
            </ul>
          </div>
        }
        <ng-template #ligne let-m>
          <span class="date">{{ date(m.date) }}</span>
          <span class="libelle">
            {{ m.libelle }}
            @if (m.simule) {
              <em class="simule">simulé</em>
            }
            @if (m.statut) {
              <em class="statut" [class.refuse]="m.statut === 'refusé'">{{ m.statut }}</em>
            }
            <small>{{ contrepartie(m) }}</small>
            @if (bouchon() && m.sortant && m.virementId && m.statut === 'exécuté') {
              <button type="button" class="refus" [disabled]="envoi()" (click)="simulerRefus(m.virementId)">
                Simuler un refus de la banque
              </button>
            }
          </span>
          <span class="montant" [class.debit]="m.sortant">{{ m.sortant ? '−' : '+' }}{{ montant(m.montantCentimes) }}</span>
        </ng-template>
      }

      @if (bouchon()) {
        <div class="test">
          <div class="boutons">
            <button type="button" (click)="ouvrir('entrant')">Simuler un virement entrant</button>
          </div>
          <small>
            Environnement de test : données factices, aucun virement réel.
            <a href="/test/verification-beneficiaire">Bouchon Vérification du Bénéficiaire (VoP)</a>
          </small>
          @if (formulaire() === 'entrant') {
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
    .entete { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 20px 0 8px; }
    .entete h3 { margin: 0; }
    .entete:first-child { margin-top: 0; }
    .voir-tout { padding: 6px 12px; border-radius: 8px; border: 1px solid #0b2545; background: #fff; color: #0b2545; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; white-space: nowrap; }
    .voile { position: fixed; inset: 0; background: rgba(15, 27, 51, .45); z-index: 1000; }
    .historique { position: fixed; z-index: 1001; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(640px, calc(100vw - 32px)); max-height: calc(100vh - 64px); overflow-y: auto; background: #fff; border-radius: 20px; padding: 24px; box-shadow: 0 20px 60px rgba(15, 27, 51, .3); }
    .historique .entete { position: sticky; top: -24px; background: #fff; padding: 4px 0 8px; margin: -4px 0 0; }
    .fermer { width: 36px; height: 36px; border-radius: 50%; border: 1px solid #b7c3d9; background: #fff; color: #0b2545; font-size: 16px; cursor: pointer; }
    .nombre { font-size: 14px; margin: 0 0 8px; }
    p { color: #5d6887; line-height: 1.6; }
    .iban { font-family: ui-monospace, monospace; font-size: 18px; font-weight: 700; color: #0f1b33; letter-spacing: .04em; overflow-wrap: anywhere; }
    .mouvements { list-style: none; padding: 0; margin: 0; }
    .mouvements li { display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: baseline; padding: 10px 0; border-bottom: 1px solid #e3e8f2; }
    .date { color: #5d6887; font-size: 13px; white-space: nowrap; }
    .libelle { color: #0f1b33; overflow-wrap: anywhere; }
    .libelle small { display: block; color: #5d6887; }
    .montant { color: #0a7d63; font-weight: 700; white-space: nowrap; font-variant-numeric: tabular-nums; }
    .montant.debit { color: #0f1b33; }
    .simule { font-style: normal; font-size: 12px; color: #8a5a00; background: #fff4d6; border-radius: 6px; padding: 1px 6px; margin-left: 6px; }
    .statut { font-style: normal; font-size: 12px; color: #0a7d63; background: #e3f6ef; border-radius: 6px; padding: 1px 6px; margin-left: 6px; }
    .statut.refuse { color: #b3261e; background: #fde7e5; }
    button.refus { margin-top: 4px; padding: 4px 10px; font-size: 13px; background: #fff; color: #0b2545; border: 1px dashed #b7c3d9; }
    .test { margin-top: 24px; padding: 16px; border: 1px dashed #b7c3d9; border-radius: 12px; }
    .boutons { display: flex; flex-wrap: wrap; gap: 8px; }
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
  /** CU-147 : augmenté par la page après un virement exécuté ; les mouvements sont alors relus. */
  readonly actualisation = input(0);
  /**
   * Un virement simulé a été crédité (CU-143) ou recrédité après un refus de la banque (CU-147) : la page recharge le
   * compte pour afficher le
   * nouveau solde.
   */
  readonly credite = output<void>();

  private readonly comptes = inject(ComptesService);
  private readonly virements = inject(VirementsTestService);
  private readonly virementsBeneficiaires = inject(VirementsService);

  /** Nombre de mouvements affichés dans la rubrique ; « Voir tout » montre les autres dans la fenêtre. */
  protected readonly apercu = 5;
  protected readonly historique = signal(false);
  private readonly boutonFermer = viewChild<ElementRef<HTMLButtonElement>>('fermer');

  constructor() {
    effect(() => {
      if (this.actualisation() > 0) {
        untracked(() => this.charger());
      }
    });
    // Accessibilité : à l'ouverture de la fenêtre, le clavier se place sur « Fermer ».
    effect(() => this.boutonFermer()?.nativeElement.focus());
  }

  protected ouvrirHistorique(): void {
    this.historique.set(true);
  }

  protected fermerHistorique(): void {
    this.historique.set(false);
  }

  protected readonly rubrique = signal<Rubrique | null>(null);
  protected readonly erreur = signal(false);
  protected readonly bouchon = signal(false);
  protected readonly formulaire = signal<'entrant' | null>(null);
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

  /**
   * Ligne sous le libellé : émetteur d'un virement reçu ; bénéficiaire (et IBAN masqué, CU-147) d'un virement émis ;
   * recrédit d'un virement refusé par la banque destinataire, avec le motif.
   */
  protected contrepartie(m: Mouvement): string {
    const iban = m.ibanMasque ? ` · ${m.ibanMasque}` : '';
    if (m.recredit) {
      return `Recrédit : virement vers ${m.beneficiaire}${iban} refusé${m.motif ? ` (${m.motif})` : ''}`;
    }
    return m.sortant ? `Vers ${m.beneficiaire}${iban}` : (m.emetteur ?? '');
  }

  // CU-147 critère 14 (test) : la banque destinataire refuse un virement exécuté -> recrédit et motif.
  protected simulerRefus(virementId: number): void {
    this.envoi.set(true);
    this.message.set(null);
    this.virementsBeneficiaires.simulerRefus(virementId, 'Compte du bénéficiaire clôturé').subscribe({
      next: (resultat) => {
        this.envoi.set(false);
        this.message.set({ texte: resultat.message, erreur: false });
        this.charger();
        this.credite.emit();
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  /** Ouvre le formulaire demandé, ou le referme s'il était déjà ouvert. */
  protected ouvrir(sens: 'entrant'): void {
    this.formulaire.set(this.formulaire() === sens ? null : sens);
    this.message.set(null);
  }

  private refus(erreur: HttpErrorResponse): void {
    this.envoi.set(false);
    const texte: unknown = erreur.error?.message;
    this.message.set({
      texte: typeof texte === 'string' ? texte : 'La simulation n’a pas pu aboutir. Veuillez réessayer.',
      erreur: true,
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
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }
}
