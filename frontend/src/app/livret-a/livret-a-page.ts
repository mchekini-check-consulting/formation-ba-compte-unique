import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { refusDe } from '../beneficiaires/beneficiaires';
import { BouchonsIdentiteService, CAS_FISCAUX, ReponseFiscale } from '../identification/bouchons-identite';
import { scenarioChoisi } from '../outils-test/scenarios';

// D4 : listes des bouchons déplacées avec les choix de test (/test/scenarios) ; réexportées pour les tests existants.
export { CAS_FISCAUX, CAS_SANCTIONS } from '../identification/bouchons-identite';
import { refusFichier } from '../identification/regles';
import { SuiviSession } from '../session/suivi-session';
import { EtapeIdentiteLivretA } from './etape-identite-livret-a';
import { EtapePieceLivretA, pieceAFournir } from './etape-piece-livret-a';
import { EtapeQuestionsLivretA } from './etape-questions-livret-a';
import { VersementInitialLivretA } from './versement-initial-livret-a';
import {
  ControleIdentite,
  DemandeLivretA,
  EtapePiece,
  EtapeQuestions,
  IdentiteLivretA,
  LIBELLES_DEMANDE,
  LivretAService,
  SuiteLivretAExistant,
  demandeTerminee,
} from './livret-a';

type Message = { texte: string; erreur: boolean };
type Chargement = { lu: false } | { lu: true; erreur: boolean };

export const ATTESTATION_OBLIGATOIRE =
  'Vous devez attester sur l’honneur ne pas détenir de Livret A dans un autre établissement.';
export const CHOIX_OBLIGATOIRE =
  'Indiquez si vous autorisez l’administration fiscale à communiquer le détail d’un éventuel Livret A existant.';
const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';
/** Message du backend quand l'éligibilité est demandée sans identité confirmée (CU-28). */
export const SANS_IDENTITE_CONFIRMEE = "Confirmez d'abord votre identité.";
/** Message du backend quand l'éligibilité est demandée avec une pièce du dossier expirée (CU-29). */
export const PIECE_EXPIREE = "Votre pièce d'identité a expiré : transmettez-en une nouvelle avant de continuer.";
/** Message du backend quand l'éligibilité est demandée sans questionnaire rempli (CU-32). */
export const SANS_QUESTIONNAIRE = "Répondez d'abord aux questions et acceptez le texte d'information sur vos données.";

/** CU-296 : choix à confirmer avant l'envoi. */
export const SUITE_OBLIGATOIRE = 'Choisissez de clôturer vous-même votre ancien livret ou de renoncer à l’ouverture.';
/** CU-296 : aucun fichier choisi. */
export const CERTIFICAT_OBLIGATOIRE = 'Sélectionnez votre certificat de clôture.';
// CU-31 (US-22) : étape « Éligibilité » de l'ouverture d'un Livret A par une cliente connectée. Information sur
// l'interdiction de détenir plusieurs Livrets A et ses risques, attestation sur l'honneur obligatoire, choix de
// partage du détail ; puis interrogation de l'administration fiscale (bouchon CU-295) et transmission de sa
// réponse au backend, qui applique les règles. Suivi de la demande ensuite (en cours, validée, existant).
@Component({
  selector: 'app-livret-a-page',
  imports: [RouterLink, EtapeIdentiteLivretA, EtapePieceLivretA, EtapeQuestionsLivretA, VersementInitialLivretA],
  template: `
    <div class="page">
      <section class="block">
        <div class="wrap">
          <!-- CU-27 critère 8 : retour à la page Livret A, à l'endroit quitté. -->
          <a class="retour-site" routerLink="/epargne/livret-a">← Retour au site</a>
          <div class="eyebrow">Épargne · Livret A</div>
          <h1 class="titre">Ouvrir mon Livret A</h1>

          <div class="livret">
            @if (message(); as m) {
              <p class="message" [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
            }

            @if (chargement(); as c) {
              @if (!c.lu) {
                <p>Chargement…</p>
              } @else if (c.erreur) {
                <p role="alert">Votre demande de Livret A est momentanément indisponible.</p>
              } @else if (demande(); as d) {
                <div class="suivi">
                  <h2>Ma demande</h2>
                  <p
                    class="statut"
                    [class.ok]="d.statut === 'OUVERTURE_VALIDEE'"
                    [class.attente]="enAttente(d)"
                    [class.ko]="terminee(d)"
                  >
                    {{ statut(d) }}
                  </p>
                  <p class="texte">{{ d.message }}</p>
                  @if (d.statut === 'OUVERTURE_VALIDEE') {
                    <!-- CU-33 : versement initial depuis le compte courant, débité à la signature (CU-34). -->
                    <app-versement-initial-livret-a [demande]="d" (demandeChange)="demande.set($event)" />
                  }
                  @if (d.statut === 'EN_COURS_DE_VERIFICATION') {
                    <p class="muted">Délai de réponse de l’administration fiscale : {{ d.delai }}.</p>
                  }
                  @if (d.detail ?? detail(); as dt) {
                    <p class="muted detail">
                      Livret A détenu chez {{ dt.etablissement }}, {{ dt.agence }}, ouvert le {{ date(dt.dateOuverture) }}.
                    </p>
                  } @else if (d.reponseFiscale === 'LIVRET_A_EXISTANT') {
                    <!-- CU-296 : partage du détail refusé. -->
                    <p class="muted detail">Livret A détenu dans un autre établissement (établissement non communiqué).</p>
                  }

                  @if (d.statut === 'LIVRET_A_EXISTANT') {
                    <!-- CU-296 : deux choix, que le partage du détail ait été accepté ou refusé. -->
                    <form class="choix-suite" (submit)="choisir($event, d)">
                      <fieldset>
                        <legend>Que souhaitez-vous faire de votre ancien Livret A ?</legend>
                        <label class="case">
                          <input type="radio" name="suite" value="CLOTURE_PAR_CLIENTE" (change)="suite.set('CLOTURE_PAR_CLIENTE')" />
                          Je clôture moi-même mon ancien livret
                        </label>
                        <label class="case">
                          <input type="radio" name="suite" value="RENONCIATION" (change)="suite.set('RENONCIATION')" />
                          Je renonce à l’ouverture
                        </label>
                      </fieldset>
                      <button type="submit" [disabled]="envoi()">Confirmer mon choix</button>
                    </form>
                  }

                  @if (d.statut === 'EN_ATTENTE_CERTIFICAT') {
                    <!-- CU-296 : dépôt du certificat de clôture, mêmes règles que la pièce d'identité (CU-29). -->
                    <form class="certificat" (submit)="deposer($event, d)">
                      @if (d.dateLimiteCertificat) {
                        <p class="limite">Date limite de dépôt : {{ date(d.dateLimiteCertificat) }}</p>
                      }
                      <label for="certificat-fichier">Mon certificat de clôture</label>
                      <input
                        id="certificat-fichier"
                        type="file"
                        accept=".png,.jpg,.jpeg,.bmp,.gif,.pdf"
                        aria-describedby="aide-certificat"
                        (change)="fichierChoisi($event)"
                      />
                      <p class="muted" id="aide-certificat">PNG, JPEG, BMP, GIF ou PDF · 2 Mo maximum</p>
                      @if (erreurFichier(); as e) {
                        <p class="ko" id="err-certificat" role="alert">{{ e }}</p>
                      }
                      <button type="submit" [disabled]="envoi()">Envoyer mon certificat</button>
                    </form>
                  }

                  @if (d.statut === 'CERTIFICAT_A_VERIFIER' && d.certificat; as c) {
                    <p class="muted certificat-depose">
                      Certificat « {{ c.nomFichier }} » déposé le {{ dateHeure(c.deposeLe) }} · vérification sous
                      {{ d.delaiVerification }}.
                    </p>
                  }

                  @if (terminee(d)) {
                    <!-- CU-296 : une demande annulée ou renoncée ne bloque plus une nouvelle demande. -->
                    <button type="button" class="nouvelle" (click)="recommencer()">Faire une nouvelle demande</button>
                  }
                  <p class="muted">
                    Attestation sur l’honneur le {{ dateHeure(d.attestationLe) }} · partage du détail
                    {{ d.partageDetailAutorise ? 'autorisé' : 'refusé' }} le {{ dateHeure(d.choixPartageLe) }}.
                  </p>
                  @if (d.statut === 'EN_COURS_DE_VERIFICATION' && d.transmission && !d.retard) {
                    <button type="button" class="secondaire" [disabled]="envoi()" (click)="interroger(d)">
                      Relancer la vérification
                    </button>
                  }
                  <a class="btn m" routerLink="/espace">Retour à mon espace</a>
                </div>
              } @else if (!identiteValidee() && etapeIdentite(); as etape) {
                <!-- CU-28 : étape « Mon identité » avant l'éligibilité. -->
                <app-etape-identite-livret-a
                  [donnees]="etape"
                  [total]="totalEtapes()"
                  (validee)="identiteValidee.set(true)"
                  (controleChange)="controleIdentite.set($event)"
                />
              } @else if (pieceRequise() && etapePiece(); as piece) {
                <!-- CU-29 : nouvelle pièce et vérification de présence, si la pièce du dossier a expiré. -->
                <app-etape-piece-livret-a [donnees]="piece" [numero]="2" [total]="totalEtapes()" (validee)="pieceValidee.set(true)" />
              } @else if (!questionsValidees() && etapeQuestions(); as questions) {
                <!-- CU-32 : questions obligatoires et texte d'information, avant l'envoi à l'administration fiscale. -->
                <app-etape-questions-livret-a
                  [donnees]="questions"
                  [numero]="totalEtapes() - 1"
                  [total]="totalEtapes()"
                  (validee)="questionsValidees.set(true)"
                />
              } @else {
                <form class="eligibilite" (submit)="valider($event, attestation.checked)">
                  <h2>Éligibilité <span class="etape">Étape {{ totalEtapes() }} sur {{ totalEtapes() }}</span></h2>
                  <div class="info">
                    <p><b>Un seul Livret A par personne, toutes banques confondues.</b> Détenir plusieurs Livrets A est interdit.</p>
                    <p>
                      En cas de multidétention, les intérêts du livret en trop sont imposés et une amende de 2 % des sommes
                      déposées peut être appliquée.
                    </p>
                    <p>
                      Avant toute ouverture, Hi-Tech Bank vérifie auprès de l’administration fiscale que vous ne détenez pas
                      déjà un Livret A. Ce contrôle est obligatoire.
                    </p>
                  </div>

                  <label class="case">
                    <input #attestation type="checkbox" name="attestation" />
                    J’atteste sur l’honneur ne pas détenir de Livret A dans un autre établissement.
                  </label>

                  <fieldset>
                    <legend>
                      J’autorise l’administration fiscale à communiquer à Hi-Tech Bank le détail d’un éventuel Livret A
                      existant (établissement, agence, date d’ouverture)
                    </legend>
                    <label class="case">
                      <input type="radio" name="partage" value="oui" (change)="partage.set(true)" /> Oui
                    </label>
                    <label class="case">
                      <input type="radio" name="partage" value="non" (change)="partage.set(false)" /> Non
                    </label>
                  </fieldset>

                  <button type="submit" [disabled]="envoi()">Valider et lancer la vérification</button>
                </form>
              }
            }
          </div>
        </div>
      </section>
    </div>
  `,
  styles: `
    .livret, .test { background: #fff; border: 1px solid #e5eaf3; border-radius: 20px; padding: 24px; display: grid; gap: 14px; }
    .tag { justify-self: start; font-size: 12px; color: #8a4b00; background: #fff3e0; border-radius: 6px; padding: 2px 8px; }
    h2 { color: #0b2545; font-size: 18px; margin: 0; }
    p { color: #5d6887; line-height: 1.6; margin: 0; }
    .info { display: grid; gap: 8px; background: #f4f7fc; border-radius: 12px; padding: 14px; }
    .info b { color: #0f1b33; }
    form, .suivi { display: grid; gap: 12px; max-width: 640px; }
    fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: 6px; }
    legend { color: #0f1b33; font-size: 14px; margin-bottom: 4px; }
    .case { display: flex; gap: 8px; align-items: flex-start; color: #0f1b33; font-size: 14px; }
    .case input { margin-top: 3px; }
    .cas { display: flex; flex-wrap: wrap; gap: 8px 18px; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button.secondaire { background: #fff; color: #0b2545; }
    button[disabled] { opacity: .6; cursor: default; }
    .statut { font-weight: 700; color: #0b2545; }
    .statut.ok { color: #0a7d63; }
    .statut.attente { color: #8a4b00; }
    .texte { color: #0f1b33; }
    .muted { font-size: 14px; }
    .message { font-weight: 600; color: #0a7d63; }
    .ko { color: #b3261e; }
    .btn { justify-self: start; }
    .etape { font-size: 13px; font-weight: 400; color: #5d6887; }
    .retour-site { display: inline-block; margin-bottom: 12px; color: #0b2545; font-weight: 700; }
    .choix-suite, .certificat { display: grid; gap: 10px; background: #f4f7fc; border-radius: 12px; padding: 14px; }
    .certificat label { color: #0f1b33; font-size: 14px; font-weight: 600; }
    .limite { color: #8a4b00; font-weight: 700; }
  `,
})
export class LivretAPage {
  private readonly suivi = inject(SuiviSession);
  private readonly service = inject(LivretAService);
  private readonly bouchons = inject(BouchonsIdentiteService);

  protected readonly casFiscaux = CAS_FISCAUX;
  protected readonly identifiant = signal('');
  protected readonly chargement = signal<Chargement>({ lu: false });
  protected readonly demande = signal<DemandeLivretA | null>(null);
  protected readonly detail = signal<ReponseFiscale['detail']>(null);
  protected readonly partage = signal<boolean | null>(null);
  /** D4 : réponse simulée de l'administration fiscale choisie sur /test/scenarios, lue à l'envoi. */
  private readonly cas = () => scenarioChoisi('livret-a.fiscal', CAS_FISCAUX);
  protected readonly envoi = signal(false);
  /** CU-28 : identité confirmée et contrôle sanctions/PPE validé ; l'éligibilité peut s'afficher. */
  protected readonly identiteValidee = signal(false);
  protected readonly etapeIdentite = signal<{ identite: IdentiteLivretA; controle: ControleIdentite | null } | null>(null);
  protected readonly controleIdentite = signal<ControleIdentite | null>(null);
  /** CU-29 : pièce du dossier expirée ou non, et dernière vérification de nouvelle pièce. */
  protected readonly etapePiece = signal<EtapePiece | null>(null);
  protected readonly pieceValidee = signal(false);
  /** Nouvelle pièce à fournir avant l'éligibilité. */
  protected readonly pieceRequise = computed(() => pieceAFournir(this.etapePiece()) && !this.pieceValidee());
  /** CU-32 : texte d'information et questionnaire de cette ouverture ; rempli, l'éligibilité peut s'afficher. */
  protected readonly etapeQuestions = signal<EtapeQuestions | null>(null);
  protected readonly questionsValidees = signal(false);
  /** 4 étapes si une nouvelle pièce est demandée, sinon 3 (identité, questions et données, éligibilité). */
  protected readonly totalEtapes = computed(() => (pieceAFournir(this.etapePiece()) ? 4 : 3));
  protected readonly message = signal<Message | null>(null);
  /** CU-296 : choix de la suite, certificat choisi et son éventuel refus avant envoi. */
  protected readonly suite = signal<SuiteLivretAExistant | null>(null);
  protected readonly fichier = signal<File | null>(null);
  protected readonly erreurFichier = signal<string | null>(null);

  constructor() {
    // CU-17 : ouvrir la page est une action de la cliente ; le suivi s'arrête quand elle la quitte.
    inject(DestroyRef).onDestroy(() => this.suivi.arreter());
    this.suivi.ouvrir().subscribe({
      next: (session) => {
        this.identifiant.set(session.identifiant);
        this.charger();
      },
      error: () => undefined,
    });
  }

  protected statut(demande: DemandeLivretA): string {
    return LIBELLES_DEMANDE[demande.statut];
  }

  protected terminee(demande: DemandeLivretA): boolean {
    return demandeTerminee(demande);
  }

  /** Statuts où la cliente ou la banque doit encore agir. */
  protected enAttente(demande: DemandeLivretA): boolean {
    return ['EN_COURS_DE_VERIFICATION', 'LIVRET_A_EXISTANT', 'EN_ATTENTE_CERTIFICAT', 'CERTIFICAT_A_VERIFIER'].includes(
      demande.statut,
    );
  }

  /** « 12/11/2026 à 10:00 », heure de Paris. */
  protected dateHeure(date: string): string {
    const d = new Date(date);
    const jour = new Intl.DateTimeFormat('fr-FR', { timeZone: 'Europe/Paris' }).format(d);
    const heure = new Intl.DateTimeFormat('fr-FR', { timeZone: 'Europe/Paris', hour: '2-digit', minute: '2-digit' }).format(d);
    return `${jour} à ${heure}`;
  }

  /** « 02/03/2015 » pour une date AAAA-MM-JJ. */
  protected date(date: string): string {
    const [annee, mois, jour] = date.split('-');
    return `${jour}/${mois}/${annee}`;
  }

  // Demande en cours ; sans demande, CU-28 : identité pré-remplie et dernier contrôle d'identité.
  private charger(): void {
    const echec = (erreur: HttpErrorResponse) => {
      // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
      if (erreur.status !== 401) {
        this.chargement.set({ lu: true, erreur: true });
      }
    };
    this.service.etat().subscribe({
      next: (etat) => {
        this.demande.set(etat.demande);
        if (etat.demande) {
          this.chargement.set({ lu: true, erreur: false });
          return;
        }
        this.chargerEtapes(echec);
      },
      error: echec,
    });
  }

  // CU-28, CU-29 et CU-32 : identité pré-remplie, dernier contrôle d'identité, pièce du dossier et questions.
  private chargerEtapes(echec: (erreur: HttpErrorResponse) => void): void {
    this.service.identite().subscribe({
      next: (etape) => {
        this.etapeIdentite.set(etape);
        this.controleIdentite.set(etape.controle);
        this.identiteValidee.set(etape.controle?.statut === 'VALIDE' && etape.controle.utilisable);
        this.service.piece().subscribe({
          next: (piece) => {
            this.etapePiece.set(piece);
            this.service.questions().subscribe({
              next: (questions) => {
                this.etapeQuestions.set(questions);
                this.questionsValidees.set(questions.questionnaire !== null);
                this.chargement.set({ lu: true, erreur: false });
              },
              error: echec,
            });
          },
          error: echec,
        });
      },
      error: echec,
    });
  }

  // CU-296 : après une annulation ou une renonciation, nouvelle demande depuis l'étape « Mon identité ».
  protected recommencer(): void {
    this.demande.set(null);
    this.detail.set(null);
    this.message.set(null);
    this.chargement.set({ lu: false });
    this.chargerEtapes((erreur) => {
      if (erreur.status !== 401) {
        this.chargement.set({ lu: true, erreur: true });
      }
    });
  }

  // CU-296 : « Je clôture moi-même mon ancien livret » ou « Je renonce à l'ouverture ».
  protected choisir(evenement: Event, demande: DemandeLivretA): void {
    evenement.preventDefault();
    const choix = this.suite();
    if (!choix) {
      this.message.set({ texte: SUITE_OBLIGATOIRE, erreur: true });
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.service.choisirSuite(demande.reference, choix).subscribe({
      next: (suite) => {
        this.envoi.set(false);
        this.suite.set(null);
        this.demande.set(suite);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  // CU-296 : fichier refusé avant envoi s'il dépasse 2 Mo ou n'est pas au bon format (règles de CU-29).
  protected fichierChoisi(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0] ?? null;
    const erreur = fichier ? refusFichier(fichier) : null;
    this.erreurFichier.set(erreur);
    this.fichier.set(erreur ? null : fichier);
    if (erreur) {
      champ.value = '';
    }
  }

  protected deposer(evenement: Event, demande: DemandeLivretA): void {
    evenement.preventDefault();
    const fichier = this.fichier();
    if (!fichier) {
      this.erreurFichier.set(this.erreurFichier() ?? CERTIFICAT_OBLIGATOIRE);
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.service.deposerCertificat(demande.reference, fichier).subscribe({
      next: (suite) => {
        this.envoi.set(false);
        this.fichier.set(null);
        this.erreurFichier.set(null);
        this.demande.set(suite);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  // Attestation obligatoire et choix de partage (contrôlés aussi par le backend), puis interrogation.
  protected valider(evenement: Event, attestation: boolean): void {
    evenement.preventDefault();
    const partage = this.partage();
    if (!attestation) {
      this.message.set({ texte: ATTESTATION_OBLIGATOIRE, erreur: true });
      return;
    }
    if (partage === null) {
      this.message.set({ texte: CHOIX_OBLIGATOIRE, erreur: true });
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.service.demander(true, partage).subscribe({
      next: (demande) => {
        this.demande.set(demande);
        this.interroger(demande);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  // Interrogation de l'administration fiscale (bouchon) puis transmission de sa réponse au backend. Si le
  // bouchon ne répond pas, la réponse transmise est « indisponible » : la demande reste en cours.
  protected interroger(demande: DemandeLivretA): void {
    const transmission = demande.transmission;
    if (!transmission) {
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.bouchons
      .interrogerAdministrationFiscale({
        demandeId: demande.reference,
        ...transmission,
        partageDetailAutorise: demande.partageDetailAutorise,
        casDeTest: this.cas(),
      })
      .subscribe({
        next: (reponse) => {
          this.detail.set(reponse.detail);
          this.transmettre(demande.reference, reponse.interrogationId, reponse.resultat, reponse.detail);
        },
        error: () => this.transmettre(demande.reference, null, 'INDISPONIBLE'),
      });
  }

  // CU-296 : le détail du livret existant est transmis au backend, qui ne le garde que si le partage est autorisé.
  private transmettre(
    reference: string,
    interrogationId: string | null,
    resultat: ReponseFiscale['resultat'],
    detail: ReponseFiscale['detail'] = null,
  ): void {
    this.service.transmettreReponse(reference, interrogationId, resultat, detail).subscribe({
      next: (demande) => {
        this.envoi.set(false);
        this.demande.set(demande);
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
    const refus = refusDe(erreur, INDISPONIBLE);
    this.message.set({ texte: refus.texte, erreur: true });
    // CU-28 : identité à (re)confirmer avant l'éligibilité.
    if (refus.texte === SANS_IDENTITE_CONFIRMEE) {
      this.identiteValidee.set(false);
    }
    // CU-29 : pièce du dossier expirée, nouvelle pièce à transmettre.
    if (refus.texte === PIECE_EXPIREE) {
      this.pieceValidee.set(false);
      this.service.piece().subscribe({ next: (piece) => this.etapePiece.set(piece), error: () => undefined });
    }
    // CU-32 : questions à remplir (ou nouvelle version du texte à accepter) avant l'éligibilité.
    if (refus.texte === SANS_QUESTIONNAIRE) {
      this.questionsValidees.set(false);
      this.service.questions().subscribe({ next: (questions) => this.etapeQuestions.set(questions), error: () => undefined });
    }
    if (refus.termine) {
      this.charger();
    }
  }
}
