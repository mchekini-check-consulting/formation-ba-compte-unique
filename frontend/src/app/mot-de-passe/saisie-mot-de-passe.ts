import { Component, computed, input, output, signal } from '@angular/core';

export const MESSAGE_TROP_COURT = 'Votre mot de passe doit contenir au moins 12 caractères';
export const MESSAGE_CONFIRMATION_VIDE = 'Veuillez confirmer votre mot de passe';
export const MESSAGE_CONFIRMATION_DIFFERENTE = 'Les deux mots de passe ne correspondent pas';

export interface SaisieMotDePasse {
  motDePasse: string;
  confirmation: string;
}

// Règles vérifiables à l'écran, avec les messages du serveur (PolitiqueMotDePasse). Les autres
// (mot de passe courant, e-mail ou nom de la cliente) ne sont contrôlées que par le serveur.
const REGLES: [(valeur: string) => boolean, string][] = [
  [(v) => v.length >= 12, MESSAGE_TROP_COURT],
  [(v) => v !== v.toLowerCase(), 'Au moins une majuscule'],
  [(v) => v !== v.toUpperCase(), 'Au moins une minuscule'],
  [(v) => /\d/.test(v), 'Au moins un chiffre'],
  [(v) => /[^\p{L}\p{N}]/u.test(v), 'Au moins un caractère spécial'],
];

/** Premier message à afficher : les règles d'abord, quelle que soit la confirmation. */
export function erreurDeSaisie({ motDePasse, confirmation }: SaisieMotDePasse): string {
  const regle = REGLES.find(([respectee]) => !respectee(motDePasse));
  if (regle) {
    return regle[1];
  }
  if (!confirmation) {
    return MESSAGE_CONFIRMATION_VIDE;
  }
  return confirmation === motDePasse ? '' : MESSAGE_CONFIRMATION_DIFFERENTE;
}

// Complément de l'US-09 : le nouveau mot de passe est saisi deux fois (caractères masqués, collage
// autorisé pour les gestionnaires de mots de passe). Le serveur refait les mêmes contrôles.
@Component({
  selector: 'app-saisie-mot-de-passe',
  template: `
    <form (submit)="soumettre($event, motDePasse, confirmation)">
      @if (erreur()) {
        <div role="alert" class="msg err"><p>{{ erreur() }}</p></div>
      }
      <div class="field">
        <label for="motDePasse">Nouveau mot de passe</label>
        <div class="input">
          <input #motDePasse id="motDePasse" name="motDePasse" [type]="visible().motDePasse ? 'text' : 'password'"
            autocomplete="new-password" placeholder="Votre nouveau mot de passe" aria-describedby="regles" required />
          <button class="eye" type="button" aria-controls="motDePasse" [attr.aria-pressed]="visible().motDePasse"
            (click)="basculer('motDePasse')">{{ visible().motDePasse ? 'Masquer' : 'Afficher' }}</button>
        </div>
      </div>
      <div class="field">
        <label for="confirmation">Confirmer le mot de passe</label>
        <div class="input">
          <input #confirmation id="confirmation" name="confirmation" [type]="visible().confirmation ? 'text' : 'password'"
            autocomplete="new-password" placeholder="Le même mot de passe" required />
          <button class="eye" type="button" aria-controls="confirmation" [attr.aria-pressed]="visible().confirmation"
            (click)="basculer('confirmation')">{{ visible().confirmation ? 'Masquer' : 'Afficher' }}</button>
        </div>
      </div>
      <ul class="regles" id="regles">
        <li>Au moins 12 caractères, dont une majuscule, une minuscule, un chiffre et un caractère spécial</li>
        <li>Pas un mot de passe courant</li>
        <li>Ni votre adresse e-mail, ni votre nom</li>
      </ul>
      <button class="btn" type="submit" [disabled]="enCours()">{{ libelle() }}</button>
    </form>
  `,
})
export class SaisieMotDePasseComponent {
  readonly libelle = input.required<string>();
  readonly enCours = input(false);
  /** Message renvoyé par le serveur, affiché tant qu'aucune erreur de saisie n'est détectée. */
  readonly erreurServeur = input('');
  readonly valider = output<SaisieMotDePasse>();

  /** Bouton « Afficher / Masquer » de chaque champ (maquette) : caractères masqués par défaut. */
  protected readonly visible = signal<Record<keyof SaisieMotDePasse, boolean>>({ motDePasse: false, confirmation: false });

  protected basculer(champ: keyof SaisieMotDePasse): void {
    this.visible.update((v) => ({ ...v, [champ]: !v[champ] }));
  }

  private readonly erreurLocale = signal('');
  protected readonly erreur = computed(() => this.erreurLocale() || this.erreurServeur());

  protected soumettre(event: Event, motDePasse: HTMLInputElement, confirmation: HTMLInputElement): void {
    event.preventDefault();
    const saisie = { motDePasse: motDePasse.value, confirmation: confirmation.value };
    // Les deux saisies restent dans les champs : la cliente peut corriger sans tout retaper.
    this.erreurLocale.set(erreurDeSaisie(saisie));
    if (!this.erreurLocale()) {
      this.valider.emit(saisie);
    }
  }
}
