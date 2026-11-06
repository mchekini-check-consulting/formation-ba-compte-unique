import { Component, computed, input } from '@angular/core';
import { Compte, formaterDate, soldeAffichable } from './comptes';

// CU-19 : carte « Compte courant » de la maquette. Sans compte lu ou avec un solde refusé,
// seul le message d'indisponibilité s'affiche : aucune valeur, aucune date, aucun statut.
@Component({
  selector: 'app-carte-compte',
  template: `
    <div class="acct">
      <div class="name">Compte courant</div>
      @if (solde(); as montant) {
        <div class="solde">{{ montant }}</div>
        @if (miseAJour(); as date) {
          <div class="stamp">Mis à jour le {{ date }}</div>
        }
        <div class="statut">
          <span class="pill" [class.off]="cloture()">{{ cloture() ? 'Compte clôturé' : 'Compte ouvert' }}</span>
        </div>
        @if (message(); as texte) {
          <div class="msg">{{ texte }}</div>
        }
      } @else {
        <div class="solde err" role="alert">Votre solde est momentanément indisponible</div>
      }
    </div>
  `,
  styles: `
    .acct { position: relative; overflow: hidden; background: radial-gradient(700px 320px at 95% -10%, rgba(0, 184, 148, .35) 0%, transparent 60%), linear-gradient(130deg, #0b2545 0%, #12304f 60%, #143556 100%); color: #fff; border-radius: 28px; padding: 34px; box-shadow: 0 30px 60px rgba(11, 37, 69, .25); }
    .name { font-size: 15px; font-weight: 700; color: #d4e0f5; }
    .solde { font-size: 56px; line-height: 1.05; font-weight: 800; letter-spacing: -.03em; margin: 18px 0 10px; font-variant-numeric: tabular-nums; }
    .solde.err { font-size: 22px; font-weight: 700; letter-spacing: 0; color: #ffd9a6; }
    .stamp { font-size: 13px; color: #a9bedd; }
    .statut { margin-top: 14px; }
    .pill { display: inline-block; font-size: 12px; font-weight: 700; padding: 5px 11px; border-radius: 999px; white-space: nowrap; background: rgba(127, 227, 204, .18); color: #7fe3cc; }
    .pill.off { background: rgba(255, 255, 255, .14); color: #d4e0f5; }
    .msg { margin-top: 22px; background: rgba(255, 255, 255, .08); border: 1px solid rgba(255, 255, 255, .14); border-radius: 16px; padding: 14px 16px; font-size: 14.5px; line-height: 1.55; color: #e6eefb; }
    @media (max-width: 980px) {
      .acct { padding: 26px 22px; }
      .solde { font-size: 44px; }
    }
  `,
})
export class CarteCompte {
  readonly compte = input.required<Compte | null>();

  protected readonly solde = computed(() => soldeAffichable(this.compte()));

  protected readonly miseAJour = computed(() => {
    const date = this.compte()?.soldeMisAJourLe;
    return date ? formaterDate(date) : null;
  });

  protected readonly cloture = computed(() => this.compte()?.statut === 'CLOTURE');

  protected readonly message = computed(() => {
    if (this.cloture()) {
      return 'Compte clôturé';
    }
    return this.compte()?.premierVersementAttendu
      ? 'Votre compte est ouvert. Vous pouvez effectuer un premier versement quand vous le souhaitez.'
      : null;
  });
}
