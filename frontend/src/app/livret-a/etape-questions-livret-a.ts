import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { refusDe } from '../beneficiaires/beneficiaires';
import { EtapeQuestions, LivretAService, ObjetEpargne, OrigineFonds } from './livret-a';

/** Choix de la BA du 25/11/2026 (points à valider de CU-32). */
export const OBJETS: { code: ObjetEpargne; libelle: string }[] = [
  { code: 'PRECAUTION', libelle: 'Épargne de précaution' },
  { code: 'PROJET', libelle: 'Financer un projet' },
  { code: 'AUTRE', libelle: 'Autre' },
];

export const ORIGINES: { code: OrigineFonds; libelle: string }[] = [
  { code: 'SALAIRE_REVENUS', libelle: 'Salaire ou revenus' },
  { code: 'EPARGNE_EXISTANTE', libelle: 'Épargne existante' },
  { code: 'HERITAGE_DONATION', libelle: 'Héritage ou donation' },
  { code: 'VENTE_BIEN', libelle: 'Vente d’un bien' },
  { code: 'AUTRE', libelle: 'Autre (à préciser)' },
];

export const PRECISION_MAX = 200;
export const OBJET_OBLIGATOIRE = 'Choisissez l’objet de votre épargne.';
export const ORIGINE_OBLIGATOIRE = 'Choisissez l’origine des fonds.';
export const PRECISION_OBLIGATOIRE = 'Précisez l’origine des fonds.';
export const PRECISION_TROP_LONGUE = 'Précision : 200 caractères au maximum.';
/** Même message que le backend : la cliente ne peut pas continuer, mais pourra revenir plus tard. */
export const TEXTE_REFUSE =
  'Sans votre accord, nous ne pouvons pas vérifier auprès de l’administration fiscale que vous ne détenez pas déjà un Livret A. Vous pourrez reprendre votre demande plus tard.';
const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';

export interface SaisieQuestions {
  objetEpargne: ObjetEpargne | '';
  origineFonds: OrigineFonds | '';
  precisionOrigine: string;
  texteAccepte: boolean;
}

export type ErreursQuestions = Partial<Record<'objetEpargne' | 'origineFonds' | 'precisionOrigine' | 'texte', string>>;

/** Questionnaire incomplet, « Autre » sans précision (ou trop longue), texte d'information non accepté. */
export function validerQuestions(saisie: SaisieQuestions, texteAAccepter: boolean): ErreursQuestions {
  const erreurs: ErreursQuestions = {};
  if (!saisie.objetEpargne) {
    erreurs.objetEpargne = OBJET_OBLIGATOIRE;
  }
  if (!saisie.origineFonds) {
    erreurs.origineFonds = ORIGINE_OBLIGATOIRE;
  } else if (saisie.origineFonds === 'AUTRE') {
    const precision = saisie.precisionOrigine.trim();
    if (!precision) {
      erreurs.precisionOrigine = PRECISION_OBLIGATOIRE;
    } else if (precision.length > PRECISION_MAX) {
      erreurs.precisionOrigine = PRECISION_TROP_LONGUE;
    }
  }
  if (texteAAccepter && !saisie.texteAccepte) {
    erreurs.texte = TEXTE_REFUSE;
  }
  return erreurs;
}

// CU-32 (US-23) : étape « Questions et données » de l'ouverture d'un Livret A, après l'identité et la pièce,
// avant l'éligibilité où les données partent à l'administration fiscale. Questionnaire obligatoire (objet de
// l'épargne, origine des fonds) et texte d'information, à accepter seulement s'il ne l'a pas été dans sa version
// en vigueur. Émet « validee » quand l'éligibilité peut commencer.
@Component({
  selector: 'app-etape-questions-livret-a',
  template: `
    <div class="questions">
      <h2>Questions et données <span class="etape">Étape {{ numero() }} sur {{ total() }}</span></h2>
      @if (message(); as m) {
        <p class="message" [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
      }
      <form (submit)="valider($event)" novalidate>
        <fieldset [attr.aria-describedby]="erreurs().objetEpargne ? 'err-objet' : null">
          <legend>Quel est l’objet de votre épargne ?</legend>
          @for (o of objets; track o.code) {
            <label class="choix">
              <input
                type="radio"
                name="objetEpargne"
                [value]="o.code"
                [checked]="saisie().objetEpargne === o.code"
                (change)="modifier('objetEpargne', o.code)"
              />
              {{ o.libelle }}
            </label>
          }
          @if (erreurs().objetEpargne; as e) {
            <p class="erreur" id="err-objet">{{ e }}</p>
          }
        </fieldset>

        <fieldset [attr.aria-describedby]="erreurs().origineFonds ? 'err-origine' : null">
          <legend>D’où proviennent les fonds que vous allez verser ?</legend>
          @for (o of origines; track o.code) {
            <label class="choix">
              <input
                type="radio"
                name="origineFonds"
                [value]="o.code"
                [checked]="saisie().origineFonds === o.code"
                (change)="modifier('origineFonds', o.code)"
              />
              {{ o.libelle }}
            </label>
          }
          @if (erreurs().origineFonds; as e) {
            <p class="erreur" id="err-origine">{{ e }}</p>
          }
          @if (saisie().origineFonds === 'AUTRE') {
            <div class="champ">
              <label for="precision-origine">Précisez l’origine des fonds ({{ precisionMax }} caractères au maximum)</label>
              <textarea
                id="precision-origine"
                name="precisionOrigine"
                rows="2"
                [attr.maxlength]="precisionMax"
                [value]="saisie().precisionOrigine"
                [attr.aria-invalid]="erreurs().precisionOrigine ? 'true' : null"
                [attr.aria-describedby]="erreurs().precisionOrigine ? 'err-precision' : null"
                (input)="modifier('precisionOrigine', $any($event.target).value)"
              ></textarea>
              @if (erreurs().precisionOrigine; as e) {
                <p class="erreur" id="err-precision">{{ e }}</p>
              }
            </div>
          }
        </fieldset>

        @if (texteAAccepter()) {
          <div class="texte">
            <h3>Vos données</h3>
            <p>{{ texte() }}</p>
            <p class="muted">Texte d’information, version {{ versionTexte() }}.</p>
            <label class="case">
              <input
                type="checkbox"
                name="texteAccepte"
                [checked]="saisie().texteAccepte"
                [attr.aria-invalid]="erreurs().texte ? 'true' : null"
                [attr.aria-describedby]="erreurs().texte ? 'err-texte' : null"
                (change)="modifier('texteAccepte', $any($event.target).checked)"
              />
              J’ai lu ce texte et j’accepte la transmission de mes données à l’administration fiscale pour ce contrôle.
            </label>
            @if (erreurs().texte; as e) {
              <p class="erreur" id="err-texte">{{ e }}</p>
            }
          </div>
        }

        <button type="submit" [disabled]="envoi()">Continuer</button>
      </form>
    </div>
  `,
  styles: `
    .questions { display: grid; gap: 12px; max-width: 640px; }
    h2 { color: #0b2545; font-size: 18px; margin: 0; display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
    h3 { color: #0f1b33; font-size: 15px; margin: 0; }
    .etape { font-size: 13px; font-weight: 400; color: #5d6887; }
    p { color: #5d6887; line-height: 1.6; margin: 0; }
    form { display: grid; gap: 16px; }
    fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: 6px; }
    legend { color: #0f1b33; font-weight: 700; margin-bottom: 4px; }
    .choix, .case { display: flex; gap: 8px; align-items: flex-start; color: #0f1b33; font-size: 14px; }
    .choix input, .case input { margin-top: 4px; }
    .champ { display: grid; gap: 4px; margin-top: 4px; }
    label { color: #0f1b33; font-size: 14px; }
    textarea { font: inherit; padding: 9px 11px; border: 1px solid #b9c4d6; border-radius: 8px; max-width: 520px; }
    textarea[aria-invalid="true"] { border-color: #b3261e; }
    .texte { display: grid; gap: 8px; background: #f4f7fc; border-radius: 12px; padding: 12px 14px; }
    .muted { font-size: 13px; }
    .erreur { color: #b3261e; font-size: 13px; }
    .message { font-weight: 600; color: #0a7d63; }
    .ko { color: #b3261e; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button[disabled] { opacity: .6; cursor: default; }
  `,
})
export class EtapeQuestionsLivretA implements OnInit {
  private readonly service = inject(LivretAService);

  /** Texte d'information et questionnaire déjà rempli, chargés par la page. */
  readonly donnees = input.required<EtapeQuestions>();
  /** Numéro de l'étape (3 si une nouvelle pièce est demandée, sinon 2). */
  readonly numero = input(2);
  readonly total = input(3);
  /** Questionnaire enregistré : l'éligibilité peut commencer. */
  readonly validee = output<void>();

  protected readonly objets = OBJETS;
  protected readonly origines = ORIGINES;
  protected readonly precisionMax = PRECISION_MAX;
  protected readonly texte = signal('');
  protected readonly versionTexte = signal('');
  protected readonly texteAAccepter = signal(true);
  protected readonly saisie = signal<SaisieQuestions>({
    objetEpargne: '',
    origineFonds: '',
    precisionOrigine: '',
    texteAccepte: false,
  });
  protected readonly erreurs = signal<ErreursQuestions>({});
  protected readonly message = signal<{ texte: string; erreur: boolean } | null>(null);
  protected readonly envoi = signal(false);

  ngOnInit(): void {
    const { texte, versionTexte, texteAAccepter } = this.donnees();
    this.texte.set(texte);
    this.versionTexte.set(versionTexte);
    this.texteAAccepter.set(texteAAccepter);
  }

  protected modifier<K extends keyof SaisieQuestions>(champ: K, valeur: SaisieQuestions[K]): void {
    this.saisie.update((s) => ({ ...s, [champ]: valeur }));
  }

  protected valider(evenement: Event): void {
    evenement.preventDefault();
    const saisie = this.saisie();
    const erreurs = validerQuestions(saisie, this.texteAAccepter());
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) {
      this.message.set({ texte: erreurs.texte ?? 'Vérifiez les réponses signalées.', erreur: true });
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.service
      .repondreQuestions({
        objetEpargne: saisie.objetEpargne as ObjetEpargne,
        origineFonds: saisie.origineFonds as OrigineFonds,
        precisionOrigine: saisie.origineFonds === 'AUTRE' ? saisie.precisionOrigine.trim() : null,
        texteAccepte: saisie.texteAccepte,
      })
      .subscribe({
        next: () => {
          this.envoi.set(false);
          this.validee.emit();
        },
        error: (erreur: HttpErrorResponse) => this.refus(erreur),
      });
  }

  private refus(erreur: HttpErrorResponse): void {
    this.envoi.set(false);
    // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
    if (erreur.status === 401) {
      return;
    }
    this.message.set({ texte: refusDe(erreur, INDISPONIBLE).texte, erreur: true });
  }
}
