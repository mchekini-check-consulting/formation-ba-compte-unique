import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { SuiviSession } from '../session/suivi-session';
import { EtatProfil, LIBELLES_PROFIL, LIBELLES_STATUT, Profil, ProfilService, QUESTIONS, Question, reponsesDe } from './profil';

type Message = { texte: string; erreur: boolean };

const INDISPONIBLE = 'Le service est momentanément indisponible. Veuillez réessayer plus tard.';
const GROUPES = [...new Set(QUESTIONS.map((q) => q.groupe))].map((nom) => ({
  nom,
  questions: QUESTIONS.filter((q) => q.groupe === nom),
}));

/** « 24/11/2026 », heure de Paris. */
export function formaterJour(date: string): string {
  return new Intl.DateTimeFormat('fr-FR', { timeZone: 'Europe/Paris' }).format(new Date(date));
}

// CU-10 (US-05) : page « Mon profil investisseur » de l'espace. Statut du profil, questionnaire pré-rempli avec la
// dernière version (revalidation après 12 mois), erreurs du backend affichées sous chaque question, et historique
// des versions avec leur date.
@Component({
  selector: 'app-profil-page',
  imports: [],
  template: `
    <div class="page">
      <section class="block">
        <div class="wrap">
          <div class="eyebrow">Mon profil investisseur</div>
          <h1 class="titre">Mon profil investisseur</h1>
          <p class="lead">
            Rempli une fois, il sert à toutes vos souscriptions de PER et d’assurance vie pendant 12 mois.
          </p>

          <div class="profil">
            @if (indisponible()) {
              <p class="ko" role="alert">{{ indisponible() }}</p>
            } @else if (etat(); as e) {
              <p class="statut" data-test="statut">
                Statut : <b>{{ libelleStatut[e.statut] }}</b>
                @switch (e.statut) {
                  @case ('VALIDE') {
                    · profil {{ libelleProfil[e.profil!.profilInvestisseur] }}, valable jusqu’au {{ jour(e.profil!.revaliderLe) }}.
                  }
                  @case ('A_REVALIDER') {
                    · votre profil a plus de 12 mois. Vérifiez vos réponses, déjà pré-remplies, puis validez-le à nouveau.
                  }
                  @default {
                    · répondez à toutes les questions pour pouvoir souscrire un PER ou une assurance vie.
                  }
                }
              </p>

              @if (message(); as m) {
                <p class="message" [class.ko]="m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
              }

              <form (submit)="valider($event)" novalidate>
                @for (groupe of groupes; track groupe.nom) {
                  <fieldset>
                    <legend>{{ groupe.nom }}</legend>
                    @for (q of groupe.questions; track q.id) {
                      <div class="question">
                        <label [for]="q.id">{{ q.libelle }}</label>
                        @if (q.choix) {
                          <select [id]="q.id" [name]="q.id" [attr.aria-invalid]="!!erreurs()[q.id]">
                            <option value="">Choisir</option>
                            @for (c of q.choix; track c[0]) {
                              <option [value]="c[0]" [selected]="valeur(q) === c[0]">{{ c[1] }}</option>
                            }
                          </select>
                        } @else {
                          <input type="number" min="0" step="1" [id]="q.id" [name]="q.id" [value]="valeur(q)"
                            [attr.aria-invalid]="!!erreurs()[q.id]" />
                        }
                        @if (q.aide) {
                          <small>{{ q.aide }}</small>
                        }
                        @if (erreurs()[q.id]; as erreur) {
                          <span class="ko">{{ erreur }}</span>
                        }
                      </div>
                    }
                  </fieldset>
                }
                <label class="accord">
                  <input type="checkbox" id="consentement" name="consentement" />
                  J’accepte que mes réponses soient utilisées pour mes souscriptions.
                </label>
                @if (erreurs()['consentement']; as erreur) {
                  <span class="ko">{{ erreur }}</span>
                }
                <button type="submit" [disabled]="envoi()">Valider mon profil</button>
              </form>

              @if (versions().length) {
                <h2>Mes versions</h2>
                @for (v of versions(); track v.version) {
                  <details>
                    <summary>Version {{ v.version }} du {{ jour(v.valideLe) }} · profil {{ libelleProfil[v.profilInvestisseur] }}</summary>
                    <ul>
                      @for (q of questions; track q.id) {
                        <li>{{ q.libelle }} : {{ libelle(q, v) }}</li>
                      }
                    </ul>
                  </details>
                }
              }
            } @else {
              <p>Chargement…</p>
            }
          </div>
        </div>
      </section>
    </div>
  `,
  styles: `
    .profil { background: #fff; border: 1px solid #e5eaf3; border-radius: 20px; padding: 24px; display: grid; gap: 16px; }
    p { color: #5d6887; line-height: 1.6; margin: 0; }
    h2 { color: #0b2545; font-size: 18px; margin: 12px 0 0; }
    form { display: grid; gap: 16px; }
    fieldset { border: 1px solid #e5eaf3; border-radius: 14px; padding: 12px 16px; display: grid; gap: 12px; min-width: 0; }
    legend { color: #0b2545; font-weight: 700; padding: 0 6px; }
    .question { display: grid; gap: 4px; }
    label { font-size: 14px; color: #0f1b33; font-weight: 600; }
    small { color: #5d6887; }
    select, input[type='number'] { padding: 8px 10px; border: 1px solid #b7c3d9; border-radius: 8px; font: inherit; min-width: 0; max-width: 460px; }
    [aria-invalid='true'] { border-color: #b3261e; }
    .accord { display: flex; gap: 8px; align-items: flex-start; font-weight: 400; }
    button { justify-self: start; padding: 10px 18px; border-radius: 8px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button[disabled] { opacity: .6; cursor: default; }
    summary { cursor: pointer; color: #0f1b33; }
    ul { color: #5d6887; line-height: 1.6; }
    .statut b { color: #0b2545; }
    .message { font-weight: 600; color: #0a7d63; }
    .ko { color: #b3261e; font-weight: 600; }
  `,
})
export class ProfilPage {
  private readonly suivi = inject(SuiviSession);
  private readonly service = inject(ProfilService);

  protected readonly groupes = GROUPES;
  protected readonly questions = QUESTIONS;
  protected readonly libelleStatut = LIBELLES_STATUT;
  protected readonly libelleProfil = LIBELLES_PROFIL;
  protected readonly jour = formaterJour;
  protected readonly identifiant = signal('');
  protected readonly etat = signal<EtatProfil | null>(null);
  protected readonly versions = signal<Profil[]>([]);
  protected readonly indisponible = signal('');
  protected readonly erreurs = signal<Record<string, string>>({});
  protected readonly message = signal<Message | null>(null);
  protected readonly envoi = signal(false);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.suivi.arreter());
    this.suivi.ouvrir().subscribe({
      next: (session) => {
        this.identifiant.set(session.identifiant);
        this.charger();
      },
      error: () => undefined,
    });
  }

  /** Réponse pré-remplie depuis la dernière version, vide sans profil. */
  protected valeur(q: Question): string {
    return String(this.etat()?.profil?.reponses[q.id] ?? '');
  }

  protected libelle(q: Question, version: Profil): string {
    const valeur = String(version.reponses[q.id] ?? '');
    return q.choix?.find(([code]) => code === valeur)?.[1] ?? valeur;
  }

  protected valider(event: Event): void {
    event.preventDefault();
    const formulaire = new FormData(event.target as HTMLFormElement);
    this.envoi.set(true);
    this.service.valider(reponsesDe(formulaire), formulaire.has('consentement')).subscribe({
      next: (profil) => {
        this.envoi.set(false);
        this.erreurs.set({});
        this.message.set({
          texte: `Profil validé : ${LIBELLES_PROFIL[profil.profilInvestisseur]}. À revalider avant le ${formaterJour(profil.revaliderLe)}.`,
          erreur: false,
        });
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => {
        this.envoi.set(false);
        this.erreurs.set(erreur.status === 400 ? (erreur.error?.champs ?? {}) : {});
        this.message.set({ texte: erreur.status === 400 ? erreur.error?.message : INDISPONIBLE, erreur: true });
      },
    });
  }

  private charger(): void {
    this.service.lire().subscribe({
      next: (etat) => this.etat.set(etat),
      // 401 : retour à la connexion par l'intercepteur sessionFermee (CU-169).
      error: (erreur: HttpErrorResponse) => erreur.status !== 401 && this.indisponible.set(INDISPONIBLE),
    });
    this.service.versions().subscribe({ next: (versions) => this.versions.set(versions), error: () => undefined });
  }
}
