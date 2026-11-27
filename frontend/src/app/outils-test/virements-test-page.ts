import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ComptesService, Mouvement, formaterDate, formaterSolde } from '../comptes/comptes';
import { VirementsService } from '../versement/virements';
import { VirementsTestService, montantEnCentimes } from '../versement/virements-test';

/** CU-147 critère 14 : motif du refus simulé de la banque destinataire. */
export const MOTIF_REFUS_BANQUE = 'Compte du bénéficiaire clôturé';

// D4 (PO, 27/11/2026) : page /test/virements (local, dev et qualif). Les simulations qui étaient dans « Faire un
// virement » de la cliente se font ici, sur le compte courant de la cliente connectée dans ce navigateur : virement
// entrant (CU-143, TECH-04) et refus d'un virement exécuté par la banque destinataire (CU-147 critère 14). Données
// factices, aucun virement réel. Le bouchon Vérification du Bénéficiaire (CU-145) a sa propre page.
@Component({
  selector: 'app-virements-test-page',
  imports: [RouterLink],
  template: `
    <main class="virements-test">
      <a routerLink="/versement">← Faire un virement</a>
      <h1>Virements (test)</h1>
      <p>
        Environnement de test : données factices, aucun virement réel.
        <a routerLink="/test/verification-beneficiaire">Bouchon Vérification du Bénéficiaire (VoP)</a>
      </p>
      @if (message(); as m) {
        <p [class.erreur]="m.erreur" [class.succes]="!m.erreur" [attr.role]="m.erreur ? 'alert' : 'status'">{{ m.texte }}</p>
      }
      @if (!lu()) {
        <p>Chargement…</p>
      } @else if (compteId()) {
        <h2>Simuler un virement entrant (CU-143)</h2>
        <form data-test="entrant" (submit)="simuler($event, iban.value, montantSaisi.value, emetteur.value, libelle.value)">
          <label for="sim-iban">IBAN du compte</label>
          <input #iban id="sim-iban" name="iban" [value]="ibanCompte()" autocomplete="off" />
          <label for="sim-montant">Montant (€)</label>
          <input #montantSaisi id="sim-montant" name="montant" inputmode="decimal" autocomplete="off" />
          <label for="sim-emetteur">Nom de l’émetteur</label>
          <input #emetteur id="sim-emetteur" name="emetteur" maxlength="70" autocomplete="off" />
          <label for="sim-libelle">Libellé</label>
          <input #libelle id="sim-libelle" name="libelle" maxlength="140" autocomplete="off" />
          <button type="submit" [disabled]="envoi()">Simuler</button>
        </form>

        <h2>Simuler un refus de la banque destinataire (CU-147 critère 14)</h2>
        @if (executes().length === 0) {
          <p>Aucun virement exécuté vers un bénéficiaire.</p>
        } @else {
          <ul data-test="executes">
            @for (m of executes(); track m.virementId) {
              <li>
                {{ date(m.date) }} · {{ m.libelle }} · {{ montant(m.montantCentimes) }}
                <button type="button" [disabled]="envoi()" (click)="refuser(m.virementId!)">Simuler un refus de la banque</button>
              </li>
            }
          </ul>
        }
      }
    </main>
  `,
  styles: `
    .virements-test { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h2 { color: #0b2545; margin-top: 28px; }
    form { display: grid; gap: 6px; max-width: 420px; }
    input { padding: 8px 10px; border: 1px solid #b7c3d9; border-radius: 8px; font: inherit; }
    button { justify-self: start; padding: 8px 14px; border-radius: 8px; border: 0; background: #0b2545; color: #fff; font: inherit; cursor: pointer; }
    button:disabled { opacity: 0.6; cursor: default; }
    li { margin: 8px 0; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
  `,
})
export class VirementsTestPage {
  private readonly comptes = inject(ComptesService);
  private readonly entrants = inject(VirementsTestService);
  private readonly virements = inject(VirementsService);
  protected readonly date = formaterDate;
  protected readonly montant = formaterSolde;
  protected readonly lu = signal(false);
  protected readonly compteId = signal<string | null>(null);
  protected readonly ibanCompte = signal('');
  protected readonly executes = signal<Mouvement[]>([]);
  protected readonly envoi = signal(false);
  protected readonly message = signal<{ texte: string; erreur: boolean } | null>(null);

  constructor() {
    this.comptes.mesComptes().subscribe({
      next: (comptes) => {
        const compte = comptes[0];
        if (!compte) {
          this.fin('Aucun compte courant pour cette cliente.');
          return;
        }
        this.compteId.set(String(compte.id));
        this.charger();
      },
      error: () => this.fin('Connectez-vous d’abord avec la cliente de test, en environnement de test.'),
    });
  }

  protected simuler(evenement: Event, iban: string, montant: string, emetteur: string, libelle: string): void {
    evenement.preventDefault();
    const montantCentimes = montantEnCentimes(montant);
    if (montantCentimes === null) {
      this.message.set({ texte: 'Montant invalide', erreur: true });
      return;
    }
    this.envoi.set(true);
    this.message.set(null);
    this.entrants.simuler({ iban, montantCentimes, emetteur, libelle }).subscribe({
      next: (resultat) => {
        this.envoi.set(false);
        this.message.set({ texte: resultat.message, erreur: resultat.resultat !== 'CREDITE' });
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  protected refuser(virementId: number): void {
    this.envoi.set(true);
    this.message.set(null);
    this.virements.simulerRefus(virementId, MOTIF_REFUS_BANQUE).subscribe({
      next: (resultat) => {
        this.envoi.set(false);
        this.message.set({ texte: resultat.message, erreur: false });
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => this.refus(erreur),
    });
  }

  private charger(): void {
    this.comptes.versements(this.compteId()!).subscribe({
      next: (rubrique) => {
        this.ibanCompte.set(rubrique.iban ?? '');
        this.executes.set(rubrique.mouvements.filter((m) => m.sortant && m.virementId && m.statut === 'exécuté'));
        this.lu.set(true);
      },
      error: () => this.fin('Les mouvements du compte sont indisponibles.'),
    });
  }

  private fin(texte: string): void {
    this.lu.set(true);
    this.message.set({ texte, erreur: true });
  }

  private refus(erreur: HttpErrorResponse): void {
    this.envoi.set(false);
    const texte: unknown = erreur.error?.message;
    this.message.set({ texte: typeof texte === 'string' ? texte : 'La simulation n’a pas pu aboutir. Veuillez réessayer.', erreur: true });
  }
}
