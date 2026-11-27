import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { Observable, defer, map, retry, timeout } from 'rxjs';
import { refusDe } from '../beneficiaires/beneficiaires';
import { BouchonsIdentiteService } from '../identification/bouchons-identite';
import { scenarioChoisi } from '../outils-test/scenarios';
import { refusFichier, tailleLisible } from '../identification/regles';
import { EtapePiece, LivretAService, NouvellePiece, ResultatDocument, TypePiece, VerificationPiece } from './livret-a';

/** CU-29 : 5 secondes par essai, puis jusqu'à 3 relances automatiques (comme CU-28). */
export const DELAI_ESSAI_MS = 5000;
export const RELANCES = 3;
const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';
const NUMERO = /^[A-Z0-9]{5,30}$/;

/** CU-29 : CNI, passeport ou titre de séjour (pas de permis de conduire). */
export const TYPES_PIECE_LIVRET_A: { code: TypePiece; libelle: string }[] = [
  { code: 'CARTE_NATIONALE_IDENTITE', libelle: "Carte nationale d'identité" },
  { code: 'PASSEPORT', libelle: 'Passeport' },
  { code: 'TITRE_SEJOUR', libelle: 'Titre de séjour' },
];

/**
 * Pièces de test du bouchon Stripe Identity (bouchons-identite/README.md), avec le résultat attendu pour le Livret A
 * (la pièce est comparée à l'identité du dossier de la cliente).
 */
export const SCENARIOS_PIECE_LIVRET_A = [
  { code: 'PIECE_CONFORME_A_LA_SAISIE', libelle: 'Pièce valide à votre nom : vérifiée' },
  { code: 'PIECE_VALIDE', libelle: 'Pièce au nom d’une autre personne (Dupont Marie Claire) : rejetée' },
  { code: 'PIECE_EXPIREE', libelle: 'Pièce expirée : rejetée' },
  { code: 'IMAGE_ILLISIBLE', libelle: 'Image illisible (floue, reflet, bords coupés) : à reprendre' },
  { code: 'ERREUR_TECHNIQUE', libelle: 'Erreur technique : à réessayer' },
  { code: 'SERVICE_INDISPONIBLE', libelle: 'Service indisponible : en attente après 3 relances' },
];

/** Selfies de test, avec le cas limite du seuil de 90 %. */
export const SCENARIOS_SELFIE_LIVRET_A = [
  { code: 'SELFIE_CONFORME', libelle: 'Selfie conforme (score 96 %) : vérifié' },
  { code: 'SCORE_90', libelle: 'Score 90 % (seuil atteint) : vérifié' },
  { code: 'SCORE_89', libelle: 'Score 89 % (juste sous le seuil) : demande bloquée' },
  { code: 'SELFIE_DIFFERENT', libelle: 'Selfie d’une autre personne (score 42 %) : demande bloquée' },
  { code: 'PHOTO_ECRAN', libelle: 'Photo imprimée ou vidéo présentée à la caméra : demande bloquée' },
  { code: 'SERVICE_INDISPONIBLE', libelle: 'Service indisponible : en attente après 3 relances' },
];

export interface SaisiePiece {
  typePiece: TypePiece | '';
  numeroPiece: string;
  dateExpiration: string;
}

export type ErreursPiece = Partial<Record<keyof SaisiePiece | 'fichier', string>>;

/** Numéro sans espaces ni tirets, en majuscules. */
export function numeroNormalise(numero: string): string {
  return numero.replace(/[\s-]/g, '').toUpperCase();
}

/** Contrôles avant envoi : type, numéro, date d'expiration à venir et fichier accepté. */
export function validerPiece(saisie: SaisiePiece, fichierRefus: string | null | undefined, aujourdhui: string): ErreursPiece {
  const erreurs: ErreursPiece = {};
  if (!saisie.typePiece) {
    erreurs.typePiece = 'Choisissez le type de pièce.';
  }
  if (!NUMERO.test(numeroNormalise(saisie.numeroPiece))) {
    erreurs.numeroPiece = 'Numéro de pièce invalide : 5 à 30 lettres ou chiffres.';
  }
  if (!saisie.dateExpiration) {
    erreurs.dateExpiration = "Date d'expiration obligatoire.";
  } else if (saisie.dateExpiration <= aujourdhui) {
    erreurs.dateExpiration = 'Cette pièce est expirée : transmettez une pièce en cours de validité.';
  }
  if (fichierRefus === undefined) {
    erreurs.fichier = 'Ajoutez la photo ou le scan de votre pièce.';
  } else if (fichierRefus) {
    erreurs.fichier = `${fichierRefus}. Vous pouvez envoyer un autre fichier.`;
  }
  return erreurs;
}

/** « 2026-11-18 » : date du jour (heure locale). */
export function aujourdhuiIso(date = new Date()): string {
  const mois = String(date.getMonth() + 1).padStart(2, '0');
  const jour = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${mois}-${jour}`;
}

/** Appel d'un bouchon : 5 s par essai, 3 relances ; « indisponible » compte comme un essai sans réponse. */
export function avecRelances<T>(appel: () => Observable<T>, indisponible: (reponse: T) => boolean, essai: (n: number) => void): Observable<T> {
  let essais = 0;
  return defer(() => {
    essais++;
    essai(essais);
    return appel().pipe(
      timeout(DELAI_ESSAI_MS),
      map((reponse) => {
        if (indisponible(reponse)) {
          throw new Error('indisponible');
        }
        return reponse;
      }),
    );
  }).pipe(retry({ count: RELANCES }));
}

// CU-29 (US-20, avec ex-CU-30) : étape « Ma pièce d'identité » de l'ouverture d'un Livret A, affichée seulement si
// la pièce du dossier a expiré. Nouvelle pièce (type, numéro, date d'expiration, fichier contrôlé dans le
// navigateur), analyse par le bouchon Stripe Identity, puis vérification de présence (selfie, seuil 90 %). Chaque
// résultat est transmis au backend, qui décide de la suite. Le bouchon ne reçoit pas d'image : le scénario de test
// choisi la remplace. Émet « validee » quand la nouvelle pièce est enregistrée dans le dossier.
@Component({
  selector: 'app-etape-piece-livret-a',
  template: `
    <div class="piece">
      <h2>Ma pièce d'identité <span class="etape">Étape {{ numero() }} sur {{ total() }}</span></h2>
      @if (message(); as m) {
        <p class="message" [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
      }
      @if (suspendu()) {
        <!-- Démarche arrêtée ou en attente d'un conseiller : pas de formulaire. -->
      } @else if (essai() > 0) {
        <p class="attente" role="status">Vérification en cours… (essai {{ essai() }} sur {{ relances + 1 }})</p>
      } @else if (verification()?.statut === 'SELFIE_A_FAIRE') {
        <div class="selfie">
          <h3>Vérification de présence</h3>
          <p>
            Prouvez que vous êtes bien la titulaire de la pièce : placez votre visage dans le cadre, puis tournez
            la tête quand cela vous est demandé. Votre visage est comparé à la photo de votre nouvelle pièce.
          </p>
          <button type="button" (click)="verifierSelfie()">Démarrer la vérification de présence</button>
        </div>
      } @else {
        <form (submit)="envoyer($event)" novalidate>
          <p class="aide">
            Votre pièce d'identité enregistrée a expiré le {{ date(donnees().dateExpirationActuelle) }}. Transmettez une
            pièce en cours de validité pour continuer.
          </p>
          <div class="champ">
            <label for="piece-type">Type de pièce</label>
            <select
              id="piece-type"
              name="typePiece"
              [value]="saisie().typePiece"
              [attr.aria-invalid]="erreurs().typePiece ? 'true' : null"
              [attr.aria-describedby]="erreurs().typePiece ? 'err-piece-type' : null"
              (change)="modifier('typePiece', $any($event.target).value)"
            >
              <option value="">Choisir</option>
              @for (t of types; track t.code) {
                <option [value]="t.code">{{ t.libelle }}</option>
              }
            </select>
            @if (erreurs().typePiece; as e) {
              <p class="erreur" id="err-piece-type">{{ e }}</p>
            }
          </div>
          <div class="champ">
            <label for="piece-numero">Numéro de la pièce</label>
            <input
              id="piece-numero"
              name="numeroPiece"
              type="text"
              autocomplete="off"
              [value]="saisie().numeroPiece"
              [attr.aria-invalid]="erreurs().numeroPiece ? 'true' : null"
              [attr.aria-describedby]="erreurs().numeroPiece ? 'err-piece-numero' : null"
              (input)="modifier('numeroPiece', $any($event.target).value)"
            />
            @if (erreurs().numeroPiece; as e) {
              <p class="erreur" id="err-piece-numero">{{ e }}</p>
            }
          </div>
          <div class="champ">
            <label for="piece-expiration">Date d'expiration</label>
            <input
              id="piece-expiration"
              name="dateExpiration"
              type="date"
              [value]="saisie().dateExpiration"
              [attr.aria-invalid]="erreurs().dateExpiration ? 'true' : null"
              [attr.aria-describedby]="erreurs().dateExpiration ? 'err-piece-expiration' : null"
              (input)="modifier('dateExpiration', $any($event.target).value)"
            />
            @if (erreurs().dateExpiration; as e) {
              <p class="erreur" id="err-piece-expiration">{{ e }}</p>
            }
          </div>
          <div class="champ">
            <label for="piece-fichier">Photo ou scan de la pièce (recto et verso)</label>
            <input
              id="piece-fichier"
              name="fichier"
              type="file"
              accept=".png,.jpg,.jpeg,.bmp,.gif,.pdf"
              [attr.aria-invalid]="erreurs().fichier ? 'true' : null"
              [attr.aria-describedby]="erreurs().fichier ? 'err-piece-fichier' : 'aide-piece-fichier'"
              (change)="choisir($event)"
            />
            <p class="muted" id="aide-piece-fichier">PNG, JPEG, BMP, GIF ou PDF · 2 Mo maximum. Le permis de conduire n'est pas accepté.</p>
            @if (fichier(); as f) {
              <p class="muted">{{ f.nom }} · {{ f.taille }}</p>
            }
            @if (erreurs().fichier; as e) {
              <p class="erreur" id="err-piece-fichier">{{ e }}</p>
            }
          </div>
          <button type="submit" [disabled]="envoi()">Envoyer ma pièce</button>
        </form>
      }
    </div>
  `,
  styles: `
    .piece { display: grid; gap: 12px; max-width: 640px; }
    h2 { color: #0b2545; font-size: 18px; margin: 0; display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
    h3 { color: #0f1b33; font-size: 16px; margin: 0; }
    .etape { font-size: 13px; font-weight: 400; color: #5d6887; }
    p { color: #5d6887; line-height: 1.6; margin: 0; }
    form, .selfie { display: grid; gap: 12px; }
    .champ { display: grid; gap: 4px; }
    label { color: #0f1b33; font-size: 14px; }
    input:not([type="file"]), select { font: inherit; padding: 9px 11px; border: 1px solid #b9c4d6; border-radius: 8px; max-width: 420px; }
    [aria-invalid="true"] { border-color: #b3261e; }
    .erreur { color: #b3261e; font-size: 13px; }
    .muted { font-size: 13px; }
    .bouchon { display: grid; gap: 4px; padding: 10px 12px; border: 1px dashed #b9c4d6; border-radius: 8px; }
    .bouchon label { font-size: 13px; color: #5d6887; }
    .attente { color: #8a4b00; font-weight: 600; }
    .message { font-weight: 600; color: #0a7d63; }
    .ko { color: #b3261e; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button[disabled] { opacity: .6; cursor: default; }
  `,
})
export class EtapePieceLivretA implements OnInit {
  private readonly service = inject(LivretAService);
  private readonly bouchons = inject(BouchonsIdentiteService);

  /** Pièce du dossier expirée et dernière vérification, chargées par la page. */
  readonly donnees = input.required<EtapePiece>();
  readonly numero = input(2);
  readonly total = input(3);
  /** Nouvelle pièce et selfie vérifiés : la pièce est enregistrée dans le dossier, l'éligibilité peut commencer. */
  readonly validee = output<void>();

  protected readonly types = TYPES_PIECE_LIVRET_A;
  protected readonly relances = RELANCES;
  /** D4 : résultats simulés choisis sur /test/scenarios, lus à l'envoi (jamais affichés à la cliente). */
  private readonly scenarioPiece = () => scenarioChoisi('livret-a.piece', SCENARIOS_PIECE_LIVRET_A);
  private readonly scenarioSelfie = () => scenarioChoisi('livret-a.selfie', SCENARIOS_SELFIE_LIVRET_A);
  protected readonly saisie = signal<SaisiePiece>({ typePiece: '', numeroPiece: '', dateExpiration: '' });
  protected readonly fichier = signal<{ nom: string; taille: string; refus: string | null } | null>(null);
  protected readonly erreurs = signal<ErreursPiece>({});
  protected readonly verification = signal<VerificationPiece | null>(null);
  protected readonly message = signal<{ texte: string; erreur: boolean } | null>(null);
  protected readonly suspendu = signal(false);
  protected readonly envoi = signal(false);
  /** Numéro de l'essai en cours auprès du bouchon (0 : aucun appel en cours). */
  protected readonly essai = signal(0);

  ngOnInit(): void {
    const verification = this.donnees().verification;
    this.verification.set(verification);
    if (verification) {
      this.afficher(verification);
    }
  }

  /** « 01/01/2024 » pour une date AAAA-MM-JJ. */
  protected date(date: string): string {
    const [annee, mois, jour] = date.split('-');
    return `${jour}/${mois}/${annee}`;
  }

  protected modifier(champ: keyof SaisiePiece, valeur: string): void {
    this.saisie.update((s) => ({ ...s, [champ]: valeur }));
  }

  protected choisir(evenement: Event): void {
    const fichier = (evenement.target as HTMLInputElement).files?.[0];
    if (!fichier) {
      return;
    }
    const refus = refusFichier(fichier);
    this.fichier.set({ nom: fichier.name, taille: tailleLisible(fichier.size), refus });
    this.erreurs.update((e) => ({ ...e, fichier: refus ? `${refus}. Vous pouvez envoyer un autre fichier.` : undefined }));
  }

  protected envoyer(evenement: Event): void {
    evenement.preventDefault();
    const saisie = this.saisie();
    const erreurs = validerPiece(saisie, this.fichier()?.refus, aujourdhuiIso());
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) {
      this.message.set({ texte: 'Vérifiez les champs signalés.', erreur: true });
      return;
    }
    const piece: NouvellePiece = {
      typePiece: saisie.typePiece as TypePiece,
      numeroPiece: numeroNormalise(saisie.numeroPiece),
      dateExpiration: saisie.dateExpiration,
    };
    this.envoi.set(true);
    this.message.set(null);
    this.service.soumettrePiece(piece).subscribe({
      next: (verification) => {
        this.envoi.set(false);
        this.verification.set(verification);
        this.analyser(verification);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  /** Analyse de la pièce par le bouchon Stripe Identity, puis résultat transmis au backend. */
  private analyser(verification: VerificationPiece): void {
    const saisie = verification.aVerifier;
    if (!saisie) {
      return;
    }
    let essais = 0;
    avecRelances(
      () => this.bouchons.verifierDocument(verification.reference, this.scenarioPiece(), saisie),
      (reponse) => reponse.statut === 'INDISPONIBLE',
      (n) => {
        essais = n;
        this.essai.set(n);
      },
    ).subscribe({
      next: (reponse) =>
        this.transmettre(
          this.service.transmettreDocument(verification.reference, reponse.statut as ResultatDocument, essais),
          reponse.motif ?? reponse.message,
        ),
      error: () => this.transmettre(this.service.transmettreDocument(verification.reference, 'SANS_REPONSE', essais), null),
    });
  }

  /** Vérification de présence par le bouchon, puis résultat et score transmis au backend. */
  protected verifierSelfie(): void {
    const verification = this.verification();
    if (!verification) {
      return;
    }
    let essais = 0;
    this.message.set(null);
    avecRelances(
      () => this.bouchons.verifierSelfie(verification.reference, this.scenarioSelfie()),
      (reponse) => reponse.resultat === 'INDISPONIBILITE_TECHNIQUE',
      (n) => {
        essais = n;
        this.essai.set(n);
      },
    ).subscribe({
      next: (reponse) =>
        this.transmettre(
          this.service.transmettreSelfie(
            verification.reference,
            reponse.resultat as 'CORRESPONDANCE' | 'NON_CORRESPONDANCE' | 'ECHEC_VERIFICATION_PRESENCE',
            reponse.score,
            essais,
          ),
          null,
        ),
      error: () => this.transmettre(this.service.transmettreSelfie(verification.reference, 'SANS_REPONSE', null, essais), null),
    });
  }

  private transmettre(appel: Observable<VerificationPiece>, motif: string | null): void {
    appel.subscribe({
      next: (verification) => {
        this.essai.set(0);
        this.verification.set(verification);
        this.afficher(verification, motif);
      },
      error: (erreur: HttpErrorResponse) => {
        this.essai.set(0);
        this.refus(erreur);
      },
    });
  }

  /** Suite selon le statut : selfie, éligibilité, pièce à reprendre ou démarche suspendue. */
  private afficher(verification: VerificationPiece, motif: string | null = null): void {
    switch (verification.statut) {
      case 'VALIDE':
        this.validee.emit();
        break;
      case 'SELFIE_A_FAIRE':
        this.message.set({ texte: verification.message, erreur: false });
        break;
      case 'BLOQUE':
      case 'EN_ATTENTE':
        this.suspendu.set(true);
        this.message.set({ texte: verification.message, erreur: verification.statut === 'BLOQUE' });
        break;
      default:
        // Pièce à reprendre (photo floue, reflet…) ou erreur technique : motif précis, la cliente recommence.
        if (motif) {
          this.message.set({ texte: `Votre pièce n'a pas pu être vérifiée : ${motif}. Vous pouvez la reprendre.`, erreur: true });
        }
    }
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

/** Pièce à fournir avant l'éligibilité : pièce du dossier expirée et pas encore remplacée. */
export function pieceAFournir(etape: EtapePiece | null): boolean {
  return !!etape?.requise && etape.verification?.statut !== 'VALIDE';
}
