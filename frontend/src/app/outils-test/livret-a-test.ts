import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ControleIdentite, DemandeLivretA, LivretAService } from '../livret-a/livret-a';

/** CU-296 : motif proposé par défaut pour un refus simulé du certificat. */
export const MOTIF_REFUS_TEST = 'Certificat illisible';

// D4 (PO, 27/11/2026) : page /test/livret-a (local, dev et qualif). Les simulations qui étaient dans la page Livret A
// de la cliente agissent ici sur sa démarche en cours (cliente connectée dans ce navigateur) : validation d'un
// responsable pour une PPE (CU-28 critère 9), expiration du délai de l'administration fiscale (CU-295 critère 5),
// validation ou refus du certificat de clôture par un conseiller (CU-296). Retour sur « Ouvrir mon Livret A » ensuite.
@Component({
  selector: 'app-livret-a-test',
  imports: [RouterLink],
  template: `
    <main class="livret-a-test">
      <a routerLink="/livret-a">← Ouvrir mon Livret A</a>
      <h1>Livret A (test)</h1>
      <p>
        Démarche de la cliente connectée dans ce navigateur. Réponses des bouchons : <a routerLink="/test/scenarios">Scénarios</a> ·
        date limite et relance à J-15 : <a routerLink="/test/date-metier">Date métier</a>.
      </p>
      @if (message(); as m) {
        <p [class.erreur]="m.erreur" [class.succes]="!m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
      }
      @if (!lu()) {
        <p>Chargement…</p>
      } @else {
        <h2>Contrôle d’identité (CU-28)</h2>
        @if (controle(); as c) {
          <p data-test="controle">{{ c.message }}</p>
          @if (c.statut === 'ATTENTE_RESPONSABLE') {
            <button type="button" data-test="responsable" [disabled]="envoi()" (click)="validerResponsable(c.reference)">
              Simuler la validation du responsable
            </button>
          }
        } @else {
          <p>Aucun contrôle d’identité en cours.</p>
        }

        <h2>Demande de Livret A (CU-295, CU-296)</h2>
        @if (demande(); as d) {
          <p data-test="demande">{{ d.message }}</p>
          @if (d.statut === 'EN_COURS_DE_VERIFICATION' && d.interrogationLe && !d.retard) {
            <button type="button" data-test="expiration" [disabled]="envoi()" (click)="agir(service.simulerExpirationDelai(d.reference))">
              Simuler l’expiration du délai
            </button>
          }
          @if (d.statut === 'CERTIFICAT_A_VERIFIER') {
            <button type="button" data-test="valider-certificat" [disabled]="envoi()" (click)="agir(service.simulerValidationCertificat(d.reference))">
              Simuler la validation du certificat par un conseiller
            </button>
            <p class="refus">
              <label for="motif-refus">Motif du refus</label>
              <input #motif id="motif-refus" type="text" [value]="motifRefus" />
              <button type="button" data-test="refuser-certificat" [disabled]="envoi()" (click)="agir(service.simulerRefusCertificat(d.reference, motif.value))">
                Simuler le refus du certificat par un conseiller
              </button>
            </p>
          }
        } @else {
          <p>Aucune demande de Livret A en cours.</p>
        }
      }
    </main>
  `,
  styles: `
    .livret-a-test { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h2 { color: #0b2545; margin-top: 28px; }
    button { padding: 8px 14px; border-radius: 8px; border: 0; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button:disabled { opacity: 0.6; cursor: default; }
    .refus { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    input { padding: 6px 8px; border: 1px solid #c9d3e6; border-radius: 6px; font: inherit; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
  `,
})
export class LivretATest {
  protected readonly service = inject(LivretAService);
  protected readonly motifRefus = MOTIF_REFUS_TEST;
  protected readonly lu = signal(false);
  protected readonly demande = signal<DemandeLivretA | null>(null);
  protected readonly controle = signal<ControleIdentite | null>(null);
  protected readonly envoi = signal(false);
  protected readonly message = signal<{ texte: string; erreur: boolean } | null>(null);

  constructor() {
    this.charger();
  }

  protected validerResponsable(reference: string): void {
    this.agir(this.service.simulerValidationResponsable(reference));
  }

  /** Simulation sur la démarche en cours, puis relecture de son état. */
  protected agir(appel: Observable<unknown>): void {
    this.envoi.set(true);
    this.message.set(null);
    appel.subscribe({
      next: () => {
        this.envoi.set(false);
        this.message.set({ texte: 'Simulation enregistrée : revenez sur « Ouvrir mon Livret A ».', erreur: false });
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => {
        this.envoi.set(false);
        const texte: unknown = erreur.error?.message;
        this.message.set({ texte: typeof texte === 'string' ? texte : 'La simulation n’a pas pu aboutir.', erreur: true });
      },
    });
  }

  private charger(): void {
    const echec = () => {
      this.lu.set(true);
      this.message.set({ texte: 'Connectez-vous d’abord avec la cliente de test, en environnement de test.', erreur: true });
    };
    this.service.etat().subscribe({
      next: (etat) => {
        this.demande.set(etat.demande);
        this.service.identite().subscribe({
          next: (etape) => {
            this.controle.set(etape.controle);
            this.lu.set(true);
          },
          error: echec,
        });
      },
      error: echec,
    });
  }
}
