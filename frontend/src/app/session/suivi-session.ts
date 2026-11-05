import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ConnexionService, SessionEnCours } from '../connexion';

/** Au plus un signalement d'activité au serveur par intervalle (le dernier n'est jamais perdu). */
export const INTERVALLE_ACTIVITE_MS = 15_000;
/** Relecture de la session (sans la prolonger) : une déconnexion ailleurs est vue au plus tard ici. */
export const INTERVALLE_VERIFICATION_MS = 60_000;
export const CANAL_SESSION = 'compte-unique-session';
export const MOTIF_EXPIREE = 'expiree';

const EVENEMENTS_ACTIVITE = ['click', 'keydown', 'input'] as const;

type MessageOnglet = { type: 'activite'; expireDansSecondes: number } | { type: 'deconnexion' };

/** « 9:05 » à partir d'un nombre de secondes. */
export function formaterRestant(secondes: number): string {
  const s = Math.max(0, secondes);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

// CU-17 : suivi de la session de la cliente dans son espace.
// - Une action (clic, saisie, ouverture d'une page) remet le délai de 10 minutes à zéro, côté
//   serveur ; les lectures et rafraîchissements automatiques ne le prolongent pas.
// - Le compte à rebours affiché n'est qu'un repère : c'est le serveur qui refuse une demande
//   arrivée après 10 minutes sans activité.
// - Les onglets ouverts se préviennent entre eux (activité, déconnexion).
@Injectable({ providedIn: 'root' })
export class SuiviSession {
  private readonly api = inject(ConnexionService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  /** Secondes restantes avant la fermeture de la session, null hors de l'espace. */
  readonly restant = signal<number | null>(null);

  private echeance = 0;
  private dernierSignalement = 0;
  private signalementEnCours = false;
  private signalementDiffere: ReturnType<typeof setTimeout> | null = null;
  private minuteur: ReturnType<typeof setInterval> | null = null;
  private verification: ReturnType<typeof setInterval> | null = null;
  private canal: BroadcastChannel | null = null;
  private readonly surActivite = () => this.activite();

  /**
   * Ouverture d'une page de l'espace : c'est une action de la cliente. Démarre le suivi et renvoie
   * la session ; sans session active, retour à la connexion.
   */
  ouvrir(): Observable<SessionEnCours> {
    this.dernierSignalement = Date.now();
    return this.api.activite().pipe(
      tap({
        next: (session) => {
          this.demarrer();
          this.recaler(session.expireDansSecondes);
          this.prevenirLesOnglets({ type: 'activite', expireDansSecondes: session.expireDansSecondes });
        },
        error: (erreur: HttpErrorResponse) => {
          if (erreur.status === 401) {
            this.fermer(null);
          }
        },
      }),
    );
  }

  /** « Me déconnecter » : session révoquée, autres onglets prévenus, retour à la connexion. */
  deconnecter(): void {
    const quitter = () => {
      this.prevenirLesOnglets({ type: 'deconnexion' });
      this.fermer(null);
    };
    this.api.deconnecter().subscribe({ next: quitter, error: quitter });
  }

  /** Fin du suivi (page quittée), sans toucher à la session. */
  arreter(): void {
    for (const evenement of EVENEMENTS_ACTIVITE) {
      this.document.removeEventListener(evenement, this.surActivite, true);
    }
    if (this.minuteur) clearInterval(this.minuteur);
    if (this.verification) clearInterval(this.verification);
    if (this.signalementDiffere) clearTimeout(this.signalementDiffere);
    this.canal?.close();
    this.minuteur = this.verification = this.signalementDiffere = this.canal = null;
    this.restant.set(null);
  }

  private demarrer(): void {
    if (this.minuteur) {
      return;
    }
    for (const evenement of EVENEMENTS_ACTIVITE) {
      this.document.addEventListener(evenement, this.surActivite, true);
    }
    this.minuteur = setInterval(() => this.decompter(), 1000);
    this.verification = setInterval(() => this.verifier(false), INTERVALLE_VERIFICATION_MS);
    if (typeof BroadcastChannel !== 'undefined') {
      this.canal = new BroadcastChannel(CANAL_SESSION);
      this.canal.onmessage = (message: MessageEvent<MessageOnglet>) => this.messageRecu(message.data);
    }
  }

  // Une action : signalée tout de suite, ou à la fin de l'intervalle si un signalement vient
  // d'être fait (le délai repart donc toujours au plus tôt de la dernière action).
  private activite(): void {
    if (this.signalementEnCours || this.signalementDiffere) {
      return;
    }
    const attente = this.dernierSignalement + INTERVALLE_ACTIVITE_MS - Date.now();
    if (attente > 0) {
      this.signalementDiffere = setTimeout(() => {
        this.signalementDiffere = null;
        this.signaler();
      }, attente);
    } else {
      this.signaler();
    }
  }

  private signaler(): void {
    this.signalementEnCours = true;
    this.dernierSignalement = Date.now();
    this.api.activite().subscribe({
      next: (session) => {
        this.signalementEnCours = false;
        this.recaler(session.expireDansSecondes);
        this.prevenirLesOnglets({ type: 'activite', expireDansSecondes: session.expireDansSecondes });
      },
      error: (erreur: HttpErrorResponse) => {
        this.signalementEnCours = false;
        if (erreur.status === 401) {
          this.fermer(this.restantCalcule() <= 0 ? MOTIF_EXPIREE : null);
        }
      },
    });
  }

  private decompter(): void {
    const restant = this.restantCalcule();
    this.restant.set(Math.max(0, restant));
    if (restant <= 0) {
      this.verifier(true);
    }
  }

  // Lecture de la session, sans la prolonger : un autre onglet a peut-être agi entre-temps.
  private verifier(echeanceAtteinte: boolean): void {
    if (echeanceAtteinte && this.minuteur) {
      clearInterval(this.minuteur);
      this.minuteur = null;
    }
    this.api.session().subscribe({
      next: (session) => {
        this.recaler(session.expireDansSecondes);
        if (!this.minuteur && this.restant() !== null) {
          this.minuteur = setInterval(() => this.decompter(), 1000);
        }
      },
      error: (erreur: HttpErrorResponse) => {
        if (erreur.status === 401) {
          this.fermer(echeanceAtteinte ? MOTIF_EXPIREE : null);
        }
      },
    });
  }

  private messageRecu(message: MessageOnglet): void {
    if (message.type === 'deconnexion') {
      this.fermer(null);
    } else {
      this.recaler(message.expireDansSecondes);
    }
  }

  private recaler(expireDansSecondes: number): void {
    this.echeance = Date.now() + expireDansSecondes * 1000;
    this.restant.set(expireDansSecondes);
  }

  private restantCalcule(): number {
    return Math.ceil((this.echeance - Date.now()) / 1000);
  }

  private prevenirLesOnglets(message: MessageOnglet): void {
    this.canal?.postMessage(message);
  }

  private fermer(motif: string | null): void {
    this.arreter();
    void this.router.navigate(['/connexion'], motif ? { queryParams: { motif } } : {});
  }
}
