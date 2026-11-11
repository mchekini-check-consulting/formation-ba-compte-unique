import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CadreEspace } from '../espace/cadre-espace';
import { SuiviSession } from '../session/suivi-session';
import { AjoutEnAttente, Beneficiaire, BeneficiairesService, refusDe } from './beneficiaires';

type Message = { texte: string; erreur: boolean };
type Liste = { lue: false } | { lue: true; erreur: boolean; beneficiaires: Beneficiaire[] };

const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';

// CU-146 (US-22) : page « Mes bénéficiaires » de l'espace (même menu que l'espace). Liste des bénéficiaires actifs
// (libellé, nom vérifié, IBAN masqué, date d'ajout, « vérifié »), ajout en deux temps (IBAN, nom et libellé, puis
// code reçu par SMS), modification du libellé et suppression. Les refus du backend sont affichés tels quels.
@Component({
  selector: 'app-mes-beneficiaires',
  imports: [CadreEspace, RouterLink],
  template: `
    <app-cadre-espace page="beneficiaires" [identifiant]="identifiant()">
      <section class="block">
        <div class="wrap">
          <div class="eyebrow">Mes comptes · Virements</div>
          <h1 class="titre">Mes bénéficiaires</h1>
          <p class="lead">
            Enregistrez les comptes vers lesquels vous faites des virements. Le nom du titulaire est vérifié auprès de
            sa banque, puis l’ajout est confirmé par un code reçu par SMS.
          </p>

          <div class="beneficiaires">
            @if (message(); as m) {
              <p class="message" [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
            }

            @if (attente(); as a) {
              <form class="code" (submit)="confirmer($event, code.value)">
                <h3>Confirmez l’ajout de {{ a.nomVerifie }}</h3>
                <p>{{ a.message }}</p>
                <label for="code-sms">Code reçu par SMS</label>
                <input #code id="code-sms" name="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
                <div class="actions">
                  <button type="submit" [disabled]="envoi()">Confirmer</button>
                  <button type="button" class="secondaire" (click)="annulerAjout()">Annuler</button>
                </div>
              </form>
            } @else if (ajout()) {
              <form class="ajout" (submit)="ajouter($event, iban.value, nom.value, libelle.value)">
                <h3>Ajouter un bénéficiaire</h3>
                <label for="benef-iban">IBAN</label>
                <input #iban id="benef-iban" name="iban" maxlength="42" autocomplete="off" />
                <label for="benef-nom">Nom du titulaire du compte</label>
                <input #nom id="benef-nom" name="nom" maxlength="70" autocomplete="off" />
                <label for="benef-libelle">Libellé (facultatif, par exemple « Papa »)</label>
                <input #libelle id="benef-libelle" name="libelle" maxlength="70" autocomplete="off" />
                <div class="actions">
                  <button type="submit" [disabled]="envoi()">Vérifier et recevoir le code</button>
                  <button type="button" class="secondaire" (click)="ajout.set(false)">Annuler</button>
                </div>
              </form>
            } @else {
              <button type="button" class="ouvrir" (click)="ouvrirAjout()">Ajouter un bénéficiaire</button>
            }

            @if (liste(); as l) {
              @if (!l.lue) {
                <p>Chargement…</p>
              } @else if (l.erreur) {
                <p role="alert">Vos bénéficiaires sont momentanément indisponibles.</p>
              } @else if (l.beneficiaires.length === 0) {
                <p class="vide">Aucun bénéficiaire pour le moment.</p>
              } @else {
                <ul class="liste">
                  @for (b of l.beneficiaires; track b.id) {
                    <li>
                      @if (enEdition() === b.id) {
                        <form class="renommer" (submit)="renommer($event, b, nouveau.value)">
                          <label [for]="'libelle-' + b.id">Libellé</label>
                          <input #nouveau [id]="'libelle-' + b.id" name="libelle" maxlength="70" [value]="b.libelle" />
                          <button type="submit" [disabled]="envoi()">Enregistrer</button>
                          <button type="button" class="secondaire" (click)="enEdition.set(null)">Annuler</button>
                        </form>
                      } @else {
                        <div class="identite">
                          <b>{{ b.libelle }}</b>
                          <span>{{ b.nomVerifie }} · {{ b.ibanMasque }}</span>
                          <small>Ajouté le {{ dateAjout(b.ajouteLe) }} <em class="verifie">{{ b.verification }}</em></small>
                        </div>
                        <div class="actions">
                          @if (aSupprimer() === b.id) {
                            <span>Supprimer ce bénéficiaire ?</span>
                            <button type="button" class="danger" [disabled]="envoi()" (click)="supprimer(b)">Oui, supprimer</button>
                            <button type="button" class="secondaire" (click)="aSupprimer.set(null)">Non</button>
                          } @else {
                            <button type="button" class="secondaire" (click)="editer(b.id)">Modifier le libellé</button>
                            <button type="button" class="secondaire" (click)="aSupprimer.set(b.id)">Supprimer</button>
                          }
                        </div>
                      }
                    </li>
                  }
                </ul>
                <a class="btn m" routerLink="/versement">Faire un virement</a>
              }
            }
          </div>
        </div>
      </section>
    </app-cadre-espace>
  `,
  styles: `
    .beneficiaires { background: #fff; border: 1px solid #e5eaf3; border-radius: 20px; padding: 24px; display: grid; gap: 16px; }
    h3 { color: #0b2545; font-size: 16px; margin: 0 0 4px; }
    p { color: #5d6887; line-height: 1.6; margin: 0; }
    form { display: grid; gap: 6px; max-width: 460px; }
    label { font-size: 14px; color: #0f1b33; margin-top: 6px; }
    input { padding: 8px 10px; border: 1px solid #b7c3d9; border-radius: 8px; font: inherit; min-width: 0; }
    button { padding: 8px 14px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button.secondaire { background: #fff; color: #0b2545; }
    button.danger { background: #b3261e; border-color: #b3261e; }
    button.ouvrir { justify-self: start; }
    button[disabled] { opacity: .6; cursor: default; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 6px; }
    .liste { list-style: none; padding: 0; margin: 0; }
    .liste li { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; padding: 14px 0; border-bottom: 1px solid #e3e8f2; }
    .identite { display: grid; gap: 2px; min-width: 0; }
    .identite b { color: #0f1b33; overflow-wrap: anywhere; }
    .identite span, .identite small { color: #5d6887; overflow-wrap: anywhere; }
    .verifie { font-style: normal; font-size: 12px; color: #0a7d63; background: #e3f6ef; border-radius: 6px; padding: 1px 6px; margin-left: 6px; }
    .renommer { grid-template-columns: 1fr; width: 100%; }
    .message { font-weight: 600; color: #0a7d63; }
    .ko { color: #b3261e; }
    .vide { color: #5d6887; }
    .btn { justify-self: start; }
  `,
})
export class MesBeneficiaires {
  private readonly suivi = inject(SuiviSession);
  private readonly service = inject(BeneficiairesService);

  protected readonly identifiant = signal('');
  protected readonly liste = signal<Liste>({ lue: false });
  protected readonly ajout = signal(false);
  protected readonly attente = signal<AjoutEnAttente | null>(null);
  protected readonly enEdition = signal<number | null>(null);
  protected readonly aSupprimer = signal<number | null>(null);
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

  /** « 11/11/2026 » : date d'ajout, heure de Paris. */
  protected dateAjout(date: string): string {
    return new Intl.DateTimeFormat('fr-FR', { timeZone: 'Europe/Paris' }).format(new Date(date));
  }

  private charger(): void {
    this.service.lister().subscribe({
      next: (beneficiaires) => this.liste.set({ lue: true, erreur: false, beneficiaires }),
      error: (erreur: HttpErrorResponse) => {
        // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
        if (erreur.status !== 401) {
          this.liste.set({ lue: true, erreur: true, beneficiaires: [] });
        }
      },
    });
  }

  protected ouvrirAjout(): void {
    this.ajout.set(true);
    this.message.set(null);
  }

  // Critères 1 à 8 : le nom est obligatoire (contrôlé aussi par le backend) ; un code est envoyé par SMS.
  protected ajouter(evenement: Event, iban: string, nom: string, libelle: string): void {
    evenement.preventDefault();
    if (!nom.trim()) {
      this.message.set({ texte: 'Indiquez un nom pour ce bénéficiaire', erreur: true });
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.service.ajouter({ iban, nom, libelle }).subscribe({
      next: (attente) => {
        this.envoi.set(false);
        this.attente.set(attente);
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  // Critères 8 à 10 : bon code -> bénéficiaire enregistré ; code faux, expiré ou 3e erreur -> message du backend.
  protected confirmer(evenement: Event, code: string): void {
    evenement.preventDefault();
    const attente = this.attente();
    if (!attente) {
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.service.confirmer(attente.ajoutId, code.trim()).subscribe({
      next: (beneficiaire) => {
        this.envoi.set(false);
        this.attente.set(null);
        this.ajout.set(false);
        this.message.set({ texte: `Bénéficiaire « ${beneficiaire.libelle} » ajouté.`, erreur: false });
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => {
        const refus = this.refus(erreur);
        if (refus?.termine) {
          this.attente.set(null);
        }
      },
    });
  }

  protected annulerAjout(): void {
    this.attente.set(null);
    this.ajout.set(false);
    this.message.set(null);
  }

  protected editer(id: number): void {
    this.enEdition.set(id);
    this.aSupprimer.set(null);
    this.message.set(null);
  }

  // Critère 13 : seul le libellé change.
  protected renommer(evenement: Event, beneficiaire: Beneficiaire, libelle: string): void {
    evenement.preventDefault();
    this.envoi.set(true);
    this.service.renommer(beneficiaire.id, libelle).subscribe({
      next: () => {
        this.envoi.set(false);
        this.enEdition.set(null);
        this.message.set({ texte: 'Libellé modifié.', erreur: false });
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  // Critères 15 et 16 : le bénéficiaire disparaît de la liste.
  protected supprimer(beneficiaire: Beneficiaire): void {
    this.envoi.set(true);
    this.service.supprimer(beneficiaire.id).subscribe({
      next: () => {
        this.envoi.set(false);
        this.aSupprimer.set(null);
        this.message.set({ texte: `Bénéficiaire « ${beneficiaire.libelle} » supprimé.`, erreur: false });
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => {
        this.aSupprimer.set(null);
        this.refus(erreur);
        this.charger();
      },
    });
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
