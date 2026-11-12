import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { refusDe } from '../beneficiaires/beneficiaires';
import { CadreEspace } from '../espace/cadre-espace';
import { BouchonsIdentiteService, CasFiscal, ReponseFiscale } from '../identification/bouchons-identite';
import { SuiviSession } from '../session/suivi-session';
import { DemandeLivretA, LivretAService } from './livret-a';

type Message = { texte: string; erreur: boolean };
type Chargement = { lu: false } | { lu: true; erreur: boolean };

export const ATTESTATION_OBLIGATOIRE =
  'Vous devez attester sur l’honneur ne pas détenir de Livret A dans un autre établissement.';
export const CHOIX_OBLIGATOIRE =
  'Indiquez si vous autorisez l’administration fiscale à communiquer le détail d’un éventuel Livret A existant.';
const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';

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
  imports: [CadreEspace, RouterLink],
  template: `
    <app-cadre-espace page="livret-a" [identifiant]="identifiant()">
      <section class="block">
        <div class="wrap">
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
              } @else {
                <form class="eligibilite" (submit)="valider($event, attestation.checked)">
                  <h2>Éligibilité</h2>
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

  private charger(): void {
    this.service.etat().subscribe({
      next: (etat) => {
        this.demande.set(etat.demande);
        this.chargement.set({ lu: true, erreur: false });
      },
      error: (erreur: HttpErrorResponse) => {
        // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
        if (erreur.status !== 401) {
          this.chargement.set({ lu: true, erreur: true });
        }
      },
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
    if (refus.termine) {
      this.charger();
    }
  }
}
