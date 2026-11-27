import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { defer, retry } from 'rxjs';
import { refusDe } from '../beneficiaires/beneficiaires';
import { BouchonsIdentiteService, CAS_SANCTIONS } from '../identification/bouchons-identite';
import { scenarioChoisi } from '../outils-test/scenarios';
import { emailValide } from '../identification/regles';
import { ControleIdentite, Coordonnees, IdentiteLivretA, LivretAService, ResultatSanctions } from './livret-a';

type Champ = keyof Coordonnees;
export type ErreursCoordonnees = Partial<Record<Champ | 'residence', string>>;

/** CU-28 critère 7 : 1 envoi puis jusqu'à 3 relances automatiques (5 secondes chacun). */
export const RELANCES = 3;
export const RESIDENCE_OBLIGATOIRE = 'Vous devez confirmer votre résidence fiscale pour continuer.';
export const EMAIL_INVALIDE = 'Adresse e-mail invalide : elle doit contenir « @ ».';
const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';
const MOBILE = /^(0|\+33\s?)[67]([\s.-]?\d{2}){4}$/;

const OBLIGATOIRES: Partial<Record<Champ, string>> = {
  voie: 'Adresse obligatoire.',
  codePostal: 'Code postal obligatoire.',
  ville: 'Ville obligatoire.',
  paysAdresse: 'Pays obligatoire.',
  email: 'Adresse e-mail obligatoire.',
  mobile: 'Téléphone mobile obligatoire.',
};

export const CHAMPS: { cle: Champ; libelle: string; autocomplete: string; type: string }[] = [
  { cle: 'voie', libelle: 'Numéro et voie', autocomplete: 'address-line1', type: 'text' },
  { cle: 'complement', libelle: 'Complément d’adresse (facultatif)', autocomplete: 'address-line2', type: 'text' },
  { cle: 'codePostal', libelle: 'Code postal', autocomplete: 'postal-code', type: 'text' },
  { cle: 'ville', libelle: 'Ville', autocomplete: 'address-level2', type: 'text' },
  { cle: 'paysAdresse', libelle: 'Pays', autocomplete: 'country-name', type: 'text' },
  { cle: 'email', libelle: 'Adresse e-mail', autocomplete: 'email', type: 'email' },
  { cle: 'mobile', libelle: 'Téléphone mobile', autocomplete: 'tel', type: 'tel' },
];

/** Critères 3 à 5 : coordonnée vidée, e-mail sans « @ », résidence fiscale non confirmée. */
export function validerCoordonnees(c: Coordonnees, residenceConfirmee: boolean): ErreursCoordonnees {
  const erreurs: ErreursCoordonnees = {};
  for (const [champ, message] of Object.entries(OBLIGATOIRES) as [Champ, string][]) {
    if (!(c[champ] ?? '').trim()) {
      erreurs[champ] = message;
    }
  }
  if (!erreurs.email && !emailValide(c.email.trim())) {
    erreurs.email = EMAIL_INVALIDE;
  }
  if (!erreurs.codePostal && c.paysAdresse.trim() === 'France' && !/^\d{5}$/.test(c.codePostal.trim())) {
    erreurs.codePostal = 'Code postal à 5 chiffres.';
  }
  if (!erreurs.mobile && !MOBILE.test(c.mobile.trim())) {
    erreurs.mobile = 'Numéro de téléphone mobile invalide.';
  }
  if (!residenceConfirmee) {
    erreurs.residence = RESIDENCE_OBLIGATOIRE;
  }
  return erreurs;
}

// CU-28 (US-19) : étape « Mon identité » de l'ouverture d'un Livret A. Identité reprise du dossier client (nom,
// prénom, date de naissance non modifiables), coordonnées corrigibles, résidence fiscale à confirmer ; puis
// contrôle sanctions/PPE (bouchon de l'API Gels des avoirs) en 5 secondes, relancé jusqu'à 3 fois, et
// transmission du résultat au backend, qui décide de la suite. Émet « validee » quand l'éligibilité peut commencer.
@Component({
  selector: 'app-etape-identite-livret-a',
  template: `
    <div class="identite">
      <h2>Mon identité <span class="etape">Étape 1 sur {{ total() }}</span></h2>
      @if (message(); as m) {
        <p class="message" [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
      }
      @if (suspendu()) {
        <!-- Démarche bloquée, en attente d'un responsable ou d'un conseiller : pas de formulaire. -->
      } @else if (essai() > 0) {
        <p class="attente" role="status">Vérification de votre identité en cours… (essai {{ essai() }} sur {{ relances + 1 }})</p>
      } @else if (identite(); as i) {
        <form (submit)="valider($event)" novalidate>
          <p class="aide">Vos informations sont reprises de votre dossier client : vérifiez-les avant de continuer.</p>
          <dl class="fige">
            <div><dt>Nom</dt><dd>{{ i.nom }}</dd></div>
            <div><dt>Prénom(s)</dt><dd>{{ i.prenoms }}</dd></div>
            <div><dt>Date de naissance</dt><dd>{{ date(i.dateNaissance) }}</dd></div>
          </dl>
          <p class="muted">Ces informations ne sont pas modifiables en ligne.</p>

          <fieldset>
            <legend>Mes coordonnées (modifiables)</legend>
            @for (champ of champs; track champ.cle) {
              <div class="champ">
                <label [for]="'id-' + champ.cle">{{ champ.libelle }}</label>
                <input
                  [id]="'id-' + champ.cle"
                  [name]="champ.cle"
                  [type]="champ.type"
                  [attr.autocomplete]="champ.autocomplete"
                  [value]="saisie()[champ.cle] ?? ''"
                  [attr.aria-invalid]="erreurs()[champ.cle] ? 'true' : null"
                  [attr.aria-describedby]="erreurs()[champ.cle] ? 'err-' + champ.cle : null"
                  (input)="modifier(champ.cle, $any($event.target).value)"
                />
                @if (erreurs()[champ.cle]; as e) {
                  <p class="erreur" [id]="'err-' + champ.cle">{{ e }}</p>
                }
              </div>
            }
          </fieldset>

          <div class="residence">
            <p>Résidence fiscale : <b>{{ i.residenceFiscale }}</b></p>
            <label class="case">
              <input
                type="checkbox"
                name="residence"
                [checked]="residenceConfirmee()"
                [attr.aria-invalid]="erreurs().residence ? 'true' : null"
                [attr.aria-describedby]="erreurs().residence ? 'err-residence' : null"
                (change)="residenceConfirmee.set($any($event.target).checked)"
              />
              Je confirme que ma résidence fiscale est : {{ i.residenceFiscale }}.
            </label>
            @if (erreurs().residence; as e) {
              <p class="erreur" id="err-residence">{{ e }}</p>
            }
          </div>

          <button type="submit" [disabled]="envoi()">Confirmer mon identité</button>
        </form>
      }
    </div>
  `,
  styles: `
    .identite { display: grid; gap: 12px; max-width: 640px; }
    h2 { color: #0b2545; font-size: 18px; margin: 0; display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
    .etape { font-size: 13px; font-weight: 400; color: #5d6887; }
    p { color: #5d6887; line-height: 1.6; margin: 0; }
    form { display: grid; gap: 12px; }
    .fige { display: grid; gap: 6px; margin: 0; background: #f4f7fc; border-radius: 12px; padding: 12px 14px; }
    .fige div { display: flex; gap: 8px; flex-wrap: wrap; }
    .fige dt { color: #5d6887; min-width: 140px; }
    .fige dd { margin: 0; color: #0f1b33; font-weight: 600; }
    fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: 10px; }
    legend { color: #0f1b33; font-weight: 700; margin-bottom: 4px; }
    .champ { display: grid; gap: 4px; }
    label { color: #0f1b33; font-size: 14px; }
    input:not([type="checkbox"]) { font: inherit; padding: 9px 11px; border: 1px solid #b9c4d6; border-radius: 8px; max-width: 420px; }
    input[aria-invalid="true"] { border-color: #b3261e; }
    .erreur { color: #b3261e; font-size: 13px; }
    .case { display: flex; gap: 8px; align-items: flex-start; }
    .case input { margin-top: 4px; }
    .residence { display: grid; gap: 6px; }
    .residence b { color: #0f1b33; }
    .muted { font-size: 13px; }
    .attente { color: #8a4b00; font-weight: 600; }
    .message { font-weight: 600; color: #0a7d63; }
    .ko { color: #b3261e; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button[disabled] { opacity: .6; cursor: default; }
  `,
})
export class EtapeIdentiteLivretA implements OnInit {
  private readonly service = inject(LivretAService);
  private readonly bouchons = inject(BouchonsIdentiteService);

  /** Identité pré-remplie et dernier contrôle, chargés par la page. */
  readonly donnees = input.required<{ identite: IdentiteLivretA; controle: ControleIdentite | null }>();
  /** Nombre d'étapes du parcours (3 si une nouvelle pièce est demandée, CU-29). */
  readonly total = input(2);
  /** D4 : réponse du bouchon sanctions/PPE choisie sur /test/scenarios, lue à l'envoi (jamais affichée à la cliente). */
  private readonly casSanctions = () => scenarioChoisi('livret-a.sanctions', CAS_SANCTIONS);
  /** Identité confirmée et contrôle validé : l'éligibilité peut commencer. */
  readonly validee = output<void>();
  /** Contrôle en cours ou terminé (la validation d'un responsable se simule sur /test/livret-a). */
  readonly controleChange = output<ControleIdentite>();

  protected readonly champs = CHAMPS;
  protected readonly relances = RELANCES;
  protected readonly identite = signal<IdentiteLivretA | null>(null);
  protected readonly saisie = signal<Coordonnees>({ voie: '', complement: null, codePostal: '', ville: '', paysAdresse: '', email: '', mobile: '' });
  protected readonly residenceConfirmee = signal(false);
  protected readonly erreurs = signal<ErreursCoordonnees>({});
  protected readonly message = signal<{ texte: string; erreur: boolean } | null>(null);
  protected readonly suspendu = signal(false);
  protected readonly envoi = signal(false);
  /** Numéro de l'essai en cours du contrôle sanctions/PPE (0 : aucun contrôle en cours). */
  protected readonly essai = signal(0);

  ngOnInit(): void {
    const { identite, controle } = this.donnees();
    this.saisie.set({ ...identite.coordonnees });
    this.identite.set(identite);
    if (controle?.statut === 'EN_COURS' && controle.aControler) {
      // Page rechargée pendant le contrôle : il reprend.
      this.controler(controle);
    } else if (controle && controle.statut !== 'VALIDE' && controle.statut !== 'EN_COURS') {
      this.suspendre(controle);
    }
  }

  /** « 14/05/1990 » pour une date AAAA-MM-JJ. */
  protected date(date: string): string {
    const [annee, mois, jour] = date.split('-');
    return `${jour}/${mois}/${annee}`;
  }

  protected modifier(champ: Champ, valeur: string): void {
    this.saisie.update((c) => ({ ...c, [champ]: valeur }));
  }

  protected valider(evenement: Event): void {
    evenement.preventDefault();
    const erreurs = validerCoordonnees(this.saisie(), this.residenceConfirmee());
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) {
      this.message.set({ texte: 'Vérifiez les champs signalés.', erreur: true });
      return;
    }
    const c = this.saisie();
    const complement = c.complement?.trim() ? c.complement.trim() : null;
    this.envoi.set(true);
    this.message.set(null);
    this.service.confirmerIdentite({ ...c, complement }, true).subscribe({
      next: (controle) => this.suivre(controle),
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  /** Suite selon l'état du contrôle : envoi au contrôle sanctions/PPE, éligibilité ou message d'attente. */
  private suivre(controle: ControleIdentite): void {
    this.controleChange.emit(controle);
    this.envoi.set(false);
    if (controle.statut === 'EN_COURS' && controle.aControler) {
      this.controler(controle);
    } else if (controle.statut === 'VALIDE') {
      this.validee.emit();
    } else if (controle.statut !== 'EN_COURS') {
      this.suspendre(controle);
    }
  }

  /** Démarche bloquée, en attente d'un responsable ou d'un conseiller : message, sans formulaire. */
  private suspendre(controle: ControleIdentite): void {
    this.suspendu.set(true);
    this.message.set({ texte: controle.message, erreur: false });
  }

  /** Critères 6 et 7 : 5 secondes par essai, jusqu'à 3 relances ; sans réponse, « SANS_REPONSE ». */
  private controler(controle: ControleIdentite): void {
    let essais = 0;
    defer(() => {
      essais++;
      this.essai.set(essais);
      return this.bouchons.controlerSanctions({ ...controle.aControler!, casDeTest: this.casSanctions() });
    })
      .pipe(retry({ count: RELANCES }))
      .subscribe({
        next: (reponse) => this.transmettre(controle.reference, reponse.resultat, essais),
        error: () => this.transmettre(controle.reference, 'SANS_REPONSE', essais),
      });
  }

  private transmettre(reference: string, resultat: ResultatSanctions, tentatives: number): void {
    this.service.transmettreControle(reference, resultat, tentatives).subscribe({
      next: (controle) => {
        this.essai.set(0);
        this.suivre(controle);
      },
      error: (erreur: HttpErrorResponse) => {
        this.essai.set(0);
        this.refus(erreur);
      },
    });
  }

  private refus(erreur: HttpErrorResponse): void {
    this.envoi.set(false);
    // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
    if (erreur.status === 401) {
      return;
    }
    const refus = refusDe(erreur, INDISPONIBLE);
    this.message.set({ texte: refus.texte, erreur: true });
    if (refus.termine) {
      this.suspendu.set(true);
    }
  }
}
