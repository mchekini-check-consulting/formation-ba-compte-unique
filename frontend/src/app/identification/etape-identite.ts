import { Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { BouchonsIdentiteService } from './bouchons-identite';
import {
  ChampIdentite,
  Erreurs,
  Identite,
  MESSAGE_ANNULATION,
  MESSAGE_INDISPONIBLE,
  sansErreur,
  validerIdentite,
  versIso,
} from './regles';

const VIDE: Identite = {
  civilite: 'Mme',
  nomNaissance: '',
  nomUsage: '',
  prenoms: '',
  dateNaissance: '',
  communeNaissance: '',
  paysNaissance: 'France',
  nationalite: 'Française',
  voie: '',
  complement: '',
  codePostal: '',
  ville: '',
  paysAdresse: 'France',
  email: '',
  mobile: '',
  fixe: '',
  typePiece: "Carte nationale d'identité",
  numeroPiece: '',
  dateExpiration: '',
};

// Écran « Mon identité » (CUB-8, maquette écran 0) : contrôles de saisie, puis contrôle
// sanctions et PEP par le backend bouchons-identite.
@Component({
  selector: 'app-etape-identite',
  templateUrl: './etape-identite.html',
})
export class EtapeIdentite {
  private readonly bouchons = inject(BouchonsIdentiteService);

  /** Saisie déjà validée, reprise quand la cliente revient à cette étape. */
  readonly initiale = input<Identite | null>(null);
  readonly valide = output<Identite>();
  readonly annule = output<string>();
  readonly retour = output<void>();

  protected readonly identite = linkedSignal<Identite>(() => ({ ...(this.initiale() ?? VIDE) }));
  protected readonly erreurs = signal<Erreurs>({});
  protected readonly resultat = signal<string | null>(null);
  protected readonly enCours = signal(false);

  protected maj(champ: ChampIdentite, event: Event): void {
    const valeur = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.identite.update((i) => ({ ...i, [champ]: valeur }));
  }

  protected continuer(event: Event): void {
    event.preventDefault();
    const identite = this.identite();
    const erreurs = validerIdentite(identite);
    this.erreurs.set(erreurs);
    if (!sansErreur(erreurs)) {
      this.resultat.set('Corrigez les champs signalés pour continuer.');
      const premier = Object.keys(erreurs)[0];
      document.getElementById(`id-${premier}`)?.focus();
      return;
    }
    this.resultat.set(null);
    this.enCours.set(true);
    this.bouchons
      .controlerSanctions({
        nomNaissance: identite.nomNaissance.trim(),
        prenoms: identite.prenoms.trim(),
        dateNaissance: versIso(identite.dateNaissance),
        nationalite: identite.nationalite,
      })
      .subscribe({
        next: (reponse) => {
          this.enCours.set(false);
          if (reponse.suite === 'ANNULER') {
            this.annule.emit(reponse.message ?? MESSAGE_ANNULATION);
          } else {
            this.valide.emit(identite);
          }
        },
        error: () => {
          // CUB-8 : pas de relance automatique, le dossier reste « En cours de vérification ».
          this.enCours.set(false);
          this.resultat.set(MESSAGE_INDISPONIBLE);
        },
      });
  }
}
