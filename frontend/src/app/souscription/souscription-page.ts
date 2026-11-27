import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { eurosAuCentime } from '../livret-a/parametres';
import { EtatSouscription, ORIGINES, OrigineFonds, SouscriptionService } from './souscription';

type Etat = 'chargement' | 'affiche' | 'indisponible';

const MINIMUM = 'Le premier versement minimum est de 100 €';

/** Montant saisi en euros (virgule ou point, espaces acceptés) ; NaN si la saisie n'est pas un nombre. */
export function lireMontant(saisie: string): number {
  const texte = saisie.replace(/\s/g, '').replace(',', '.');
  return /^\d+(\.\d{1,2})?$/.test(texte) ? Number(texte) : Number.NaN;
}

// CU-312 (PER-04) : souscrire son PER après « Continuer » sur la page Découvrir. Profil non validé : renvoi vers le
// questionnaire. Premier versement (100 € au moins, depuis le compte courant) et origine des fonds, puis signature
// par code SMS (valable 5 minutes ; 3 codes erronés : 30 minutes de blocage). Les refus viennent du backend.
@Component({
  selector: 'app-souscription-page',
  imports: [RouterLink],
  template: `
    <main class="souscription">
      <a routerLink="/decouvrir" [queryParams]="{ produit: 'PER' }">← Découvrir le PER</a>
      <h1>Souscrire mon PER</h1>

      @if (message()) {
        <p class="succes" role="status">{{ message() }}</p>
      }
      @if (erreur()) {
        <p class="erreur" role="alert">{{ erreur() }}</p>
      }

      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="erreur" role="alert">La souscription est momentanément indisponible. Réessayez plus tard.</p>
        }
        @case ('affiche') {
          @if (donnees()!.etape === 'SIGNEE') {
            <section class="carte" data-test="signee">
              <h2>Votre PER est ouvert</h2>
              <p>Contrat n° {{ donnees()!.numeroContrat }}, en gestion pilotée.</p>
              <a routerLink="/gestion" [queryParams]="{ produit: 'PER' }">Voir ma gestion</a>
            </section>
          } @else if (donnees()!.profil !== 'VALIDE') {
            <p class="carte" data-test="profil-requis">
              Votre profil investisseur doit être complété ou revalidé avant de souscrire.
              <a routerLink="/profil">Compléter mon profil</a>
            </p>
          } @else if (donnees()!.etape === 'VERSEMENT' || modification()) {
            <form class="carte" data-test="versement" (submit)="continuer($event)">
              <h2>Mon premier versement</h2>
              <p>Prélevé sur votre compte courant Hi-Tech Bank à la signature, sur le compartiment 1 (versements volontaires).</p>
              <label>Montant (€)
                <input inputmode="decimal" [value]="montant()" (input)="montant.set(valeur($event))" />
              </label>
              @if (sousLeMinimum()) {
                <p class="erreur" data-test="minimum">{{ minimum }}</p>
              }
              <fieldset>
                <legend>Origine des fonds</legend>
                @for (origine of origines; track origine.code) {
                  <label class="choix">
                    <input type="radio" name="origine" [value]="origine.code" [checked]="origineFonds() === origine.code"
                      (change)="origineFonds.set(origine.code)" />
                    {{ origine.libelle }}
                  </label>
                }
              </fieldset>
              @if (origineFonds() === 'AUTRE') {
                <label>Précisez
                  <input maxlength="200" [value]="precision()" (input)="precision.set(valeur($event))" />
                </label>
              }
              <button class="btn p" type="submit" data-test="continuer" [disabled]="!peutContinuer() || envoi()">Continuer</button>
            </form>
          } @else {
            <section class="carte" data-test="signature">
              <h2>Signer mon PER</h2>
              <p>Premier versement : <strong>{{ eurosAuCentime(donnees()!.montant!) }}</strong>, {{ libelleOrigine() }}.
                <button class="lien" type="button" data-test="modifier" (click)="modifier()">Modifier</button></p>
              <p>Un code à 6 chiffres vous est envoyé par SMS ; il est valable 5 minutes.</p>
              <button class="btn o" type="button" data-test="code" [disabled]="envoi()" (click)="envoyerCode()">
                {{ donnees()!.codeEnvoyeLe ? 'Recevoir un nouveau code' : 'Recevoir mon code par SMS' }}
              </button>
              @if (donnees()!.codeEnvoyeLe) {
                <form class="code" data-test="formulaire-code" (submit)="signer($event)">
                  <label>Code reçu par SMS
                    <input inputmode="numeric" maxlength="6" autocomplete="one-time-code" [value]="code()"
                      (input)="code.set(valeur($event))" />
                  </label>
                  <button class="btn p" type="submit" [disabled]="code().length !== 6 || envoi()">Signer</button>
                </form>
              }
            </section>
          }
        }
      }
    </main>
  `,
  styles: `
    .souscription { max-width: 720px; margin: 0 auto; padding: 24px 16px; display: grid; gap: 16px; }
    .carte { display: grid; gap: 12px; padding: 16px; border: 1px solid #d0d5dd; border-radius: 12px; background: #fff; }
    label { display: grid; gap: 4px; font-weight: 700; }
    .choix { display: flex; gap: 8px; align-items: center; font-weight: 400; }
    fieldset { border: 0; padding: 0; display: grid; gap: 6px; }
    legend { font-weight: 700; margin-bottom: 4px; }
    input { padding: 8px; font: inherit; max-width: 100%; }
    .code { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
    .btn { justify-self: start; padding: 10px 18px; border-radius: 999px; font-weight: 700; cursor: pointer; font: inherit; }
    .btn.o { border: 1px solid #0b2545; background: #fff; color: #0b2545; }
    .btn.p { border: 0; background: #0b2545; color: #fff; }
    .btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .lien { border: 0; background: none; color: #0b2545; text-decoration: underline; cursor: pointer; font: inherit; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
  `,
})
export class SouscriptionPage {
  private readonly service = inject(SouscriptionService);
  protected readonly origines = ORIGINES;
  protected readonly minimum = MINIMUM;
  protected readonly eurosAuCentime = eurosAuCentime;
  protected readonly etat = signal<Etat>('chargement');
  protected readonly donnees = signal<EtatSouscription | null>(null);
  protected readonly modification = signal(false);
  protected readonly montant = signal('');
  protected readonly origineFonds = signal<OrigineFonds | null>(null);
  protected readonly precision = signal('');
  protected readonly code = signal('');
  protected readonly envoi = signal(false);
  protected readonly message = signal('');
  protected readonly erreur = signal('');
  protected readonly sousLeMinimum = computed(() => {
    const montant = lireMontant(this.montant());
    return this.montant().trim() !== '' && (Number.isNaN(montant) || montant < (this.donnees()?.minimum ?? 100));
  });
  protected readonly peutContinuer = computed(() =>
    this.montant().trim() !== '' && !this.sousLeMinimum() && this.origineFonds() !== null
    && (this.origineFonds() !== 'AUTRE' || this.precision().trim() !== ''));
  protected readonly libelleOrigine = computed(() => {
    const d = this.donnees();
    const libelle = ORIGINES.find((o) => o.code === d?.origineFonds)?.libelle.replace(' (à préciser)', '') ?? '';
    return d?.originePrecision ? `${libelle} : ${d.originePrecision}` : libelle;
  });

  constructor() {
    this.service.etat().subscribe({
      next: (donnees) => {
        this.afficher(donnees);
        this.etat.set('affiche');
      },
      error: () => this.etat.set('indisponible'),
    });
  }

  protected valeur(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected modifier(): void {
    this.modification.set(true);
    this.message.set('');
    this.erreur.set('');
  }

  protected continuer(event: Event): void {
    event.preventDefault();
    if (!this.peutContinuer()) {
      return;
    }
    this.appeler(this.service.versement(lireMontant(this.montant()), this.origineFonds()!, this.precision().trim()), '');
  }

  protected envoyerCode(): void {
    this.code.set('');
    this.appeler(this.service.envoyerCode(), 'Un code vous a été envoyé par SMS.');
  }

  protected signer(event: Event): void {
    event.preventDefault();
    this.appeler(this.service.signer(this.code()), 'Votre PER est souscrit et votre premier versement est enregistré.');
  }

  private appeler(appel: Observable<EtatSouscription>, texte: string): void {
    this.envoi.set(true);
    this.erreur.set('');
    this.message.set('');
    appel.subscribe({
      next: (donnees) => {
        this.afficher(donnees);
        this.envoi.set(false);
        this.message.set(texte);
      },
      error: (refus: HttpErrorResponse) => {
        this.envoi.set(false);
        this.erreur.set(refus.error?.message ?? 'Votre demande n’a pas pu être enregistrée. Réessayez.');
      },
    });
  }

  private afficher(donnees: EtatSouscription): void {
    this.donnees.set(donnees);
    this.modification.set(false);
    if (donnees.montant !== null) {
      this.montant.set(String(donnees.montant).replace('.', ','));
      this.origineFonds.set(donnees.origineFonds);
      this.precision.set(donnees.originePrecision ?? '');
    }
  }
}
