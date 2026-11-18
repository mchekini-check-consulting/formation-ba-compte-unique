import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { refusDe } from '../beneficiaires/beneficiaires';
import { CadreEspace } from '../espace/cadre-espace';
import { BouchonsIdentiteService, CasDeTest, CasFiscal, ReponseFiscale } from '../identification/bouchons-identite';
import { SuiviSession } from '../session/suivi-session';
import { EtapeIdentiteLivretA } from './etape-identite-livret-a';
import { EtapePieceLivretA, pieceAFournir } from './etape-piece-livret-a';
import { ControleIdentite, DemandeLivretA, EtapePiece, IdentiteLivretA, LivretAService } from './livret-a';

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

/** CU-28 : cas de test du bouchon sanctions/PPE (API Gels des avoirs). */
export const CAS_SANCTIONS: { code: CasDeTest; libelle: string }[] = [
  { code: 'CONFORME', libelle: 'Aucune correspondance' },
  { code: 'SANCTIONNE', libelle: 'Gel des avoirs' },
  { code: 'PEP', libelle: 'Personne politiquement exposée' },
  { code: 'INDISPONIBLE', libelle: 'Sans réponse (5 s par essai)' },
];

/** CU-295 : cas de test du bouchon de l'administration fiscale. */
export const CAS_FISCAUX: { code: CasFiscal; libelle: string }[] = [
  { code: 'SANS_LIVRET_A', libelle: 'Aucun autre Livret A' },
  { code: 'DETENU_AILLEURS', libelle: 'Livret A détenu ailleurs' },
  { code: 'SANS_REPONSE', libelle: 'Sans réponse' },
  { code: 'INDISPONIBLE', libelle: 'Service indisponible' },
];

const STATUTS: Record<DemandeLivretA['statut'], string> = {
  EN_COURS_DE_VERIFICATION: 'En cours de vérification',
  OUVERTURE_VALIDEE: 'Ouverture validée',
  LIVRET_A_EXISTANT: 'Livret A existant dans un autre établissement',
};

// CU-31 (US-22) : étape « Éligibilité » de l'ouverture d'un Livret A par une cliente connectée. Information sur
// l'interdiction de détenir plusieurs Livrets A et ses risques, attestation sur l'honneur obligatoire, choix de
// partage du détail ; puis interrogation de l'administration fiscale (bouchon CU-295) et transmission de sa
// réponse au backend, qui applique les règles. Suivi de la demande ensuite (en cours, validée, existant).
@Component({
  selector: 'app-livret-a-page',
  imports: [CadreEspace, RouterLink, EtapeIdentiteLivretA, EtapePieceLivretA],
  template: `
    <app-cadre-espace page="livret-a" [identifiant]="identifiant()">
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
                  <p class="statut" [class.ok]="d.statut === 'OUVERTURE_VALIDEE'" [class.attente]="d.statut === 'EN_COURS_DE_VERIFICATION'">
                    {{ statut(d) }}
                  </p>
                  <p class="texte">{{ d.message }}</p>
                  @if (d.statut === 'EN_COURS_DE_VERIFICATION') {
                    <p class="muted">Délai de réponse de l’administration fiscale : {{ d.delai }}.</p>
                  }
                  @if (detail(); as dt) {
                    <p class="muted detail">
                      Livret A détenu chez {{ dt.etablissement }}, {{ dt.agence }}, ouvert le {{ date(dt.dateOuverture) }}.
                    </p>
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
                  [casSanctions]="casSanctions()"
                  (validee)="identiteValidee.set(true)"
                  (controleChange)="controleIdentite.set($event)"
                />
              } @else if (pieceRequise() && etapePiece(); as piece) {
                <!-- CU-29 : nouvelle pièce et vérification de présence, si la pièce du dossier a expiré. -->
                <app-etape-piece-livret-a [donnees]="piece" [numero]="2" [total]="totalEtapes()" (validee)="pieceValidee.set(true)" />
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

          <section class="test" aria-labelledby="titre-test-fiscal">
            <div class="tag">Environnement de test uniquement</div>
            <h2 id="titre-test-sanctions">Bouchon du contrôle sanctions et PPE (CU-28)</h2>
            <div class="cas" role="group" aria-labelledby="titre-test-sanctions">
              @for (c of casSanctionsTest; track c.code) {
                <label class="case">
                  <input
                    type="radio"
                    name="cas-sanctions"
                    [value]="c.code"
                    [checked]="casSanctions() === c.code"
                    (change)="casSanctions.set(c.code)"
                  />
                  {{ c.libelle }}
                </label>
              }
            </div>
            @if (controleIdentite(); as ci) {
              @if (ci.statut === 'ATTENTE_RESPONSABLE') {
                <button type="button" class="secondaire" [disabled]="envoi()" (click)="validerResponsable(ci.reference)">
                  Simuler la validation du responsable
                </button>
              }
            }
            <h2 id="titre-test-fiscal">Bouchon de l’administration fiscale (CU-295)</h2>
            <div class="cas" role="group" aria-label="Réponse de l'administration fiscale">
              @for (c of casFiscaux; track c.code) {
                <label class="case">
                  <input type="radio" name="cas-fiscal" [value]="c.code" [checked]="cas() === c.code" (change)="cas.set(c.code)" />
                  {{ c.libelle }}
                </label>
              }
            </div>
            @if (demande(); as d) {
              @if (d.statut === 'EN_COURS_DE_VERIFICATION' && d.interrogationLe && !d.retard) {
                <button type="button" class="secondaire" [disabled]="envoi()" (click)="expirerDelai(d)">
                  Simuler l’expiration du délai
                </button>
              }
            }
          </section>
        </div>
      </section>
    </app-cadre-espace>
  `,
  styles: `
    .livret, .test { background: #fff; border: 1px solid #e5eaf3; border-radius: 20px; padding: 24px; display: grid; gap: 14px; }
    .test { margin-top: 20px; border-style: dashed; }
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
  protected readonly cas = signal<CasFiscal>('SANS_LIVRET_A');
  protected readonly envoi = signal(false);
  /** CU-28 : identité confirmée et contrôle sanctions/PPE validé ; l'éligibilité peut s'afficher. */
  protected readonly identiteValidee = signal(false);
  protected readonly etapeIdentite = signal<{ identite: IdentiteLivretA; controle: ControleIdentite | null } | null>(null);
  protected readonly controleIdentite = signal<ControleIdentite | null>(null);
  protected readonly casSanctions = signal<CasDeTest>('CONFORME');
  /** CU-29 : pièce du dossier expirée ou non, et dernière vérification de nouvelle pièce. */
  protected readonly etapePiece = signal<EtapePiece | null>(null);
  protected readonly pieceValidee = signal(false);
  /** Nouvelle pièce à fournir avant l'éligibilité. */
  protected readonly pieceRequise = computed(() => pieceAFournir(this.etapePiece()) && !this.pieceValidee());
  /** 3 étapes si une nouvelle pièce est demandée, sinon 2. */
  protected readonly totalEtapes = computed(() => (pieceAFournir(this.etapePiece()) ? 3 : 2));
  protected readonly casSanctionsTest = CAS_SANCTIONS;
  protected readonly message = signal<Message | null>(null);

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
    return STATUTS[demande.statut];
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
        this.service.identite().subscribe({
          next: (etape) => {
            this.etapeIdentite.set(etape);
            this.controleIdentite.set(etape.controle);
            this.identiteValidee.set(etape.controle?.statut === 'VALIDE' && etape.controle.utilisable);
            this.service.piece().subscribe({
              next: (piece) => {
                this.etapePiece.set(piece);
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
          this.transmettre(demande.reference, reponse.interrogationId, reponse.resultat);
        },
        error: () => this.transmettre(demande.reference, null, 'INDISPONIBLE'),
      });
  }

  private transmettre(reference: string, interrogationId: string | null, resultat: ReponseFiscale['resultat']): void {
    this.service.transmettreReponse(reference, interrogationId, resultat).subscribe({
      next: (demande) => {
        this.envoi.set(false);
        this.demande.set(demande);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  // CU-295 critère 5 : la demande est traitée comme restée sans réponse au bout de 2 jours ouvrés.
  protected expirerDelai(demande: DemandeLivretA): void {
    this.envoi.set(true);
    this.message.set(null);
    this.service.simulerExpirationDelai(demande.reference).subscribe({
      next: () => {
        this.envoi.set(false);
        this.charger();
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
    if (refus.termine) {
      this.charger();
    }
  }

  /** CU-28 critère 9 (test) : un responsable valide la démarche d'une personne politiquement exposée. */
  protected validerResponsable(reference: string): void {
    this.envoi.set(true);
    this.message.set(null);
    this.service.simulerValidationResponsable(reference).subscribe({
      next: (controle) => {
        this.envoi.set(false);
        this.controleIdentite.set(controle);
        this.identiteValidee.set(controle.statut === 'VALIDE' && controle.utilisable);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }
}
