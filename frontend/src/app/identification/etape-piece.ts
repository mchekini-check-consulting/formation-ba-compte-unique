import { Component, inject, input, output, signal } from '@angular/core';
import { BouchonsIdentiteService } from './bouchons-identite';
import {
  Identite,
  MESSAGE_ANNULATION,
  MESSAGE_INDISPONIBLE,
  refusFichier,
  tailleLisible,
  versIso,
} from './regles';

interface FichierChoisi {
  nom: string;
  taille: string;
  refus: string | null;
}

/** Pièces de test du bouchon Stripe Identity (bouchons-identite/README.md). */
export const SCENARIOS_PIECE = [
  { code: 'PIECE_CONFORME_A_LA_SAISIE', libelle: 'Pièce valide, conforme à votre saisie' },
  { code: 'PIECE_VALIDE', libelle: 'Pièce valide (Dupont Marie Claire, 14/05/1990, expire le 30/06/2031)' },
  { code: 'PIECE_VALIDE_AUTRE_PERSONNE', libelle: "Pièce valide d'une autre personne" },
  { code: 'PIECE_EXPIREE', libelle: 'Pièce expirée' },
  { code: 'IMAGE_ILLISIBLE', libelle: 'Image illisible' },
  { code: 'ERREUR_TECHNIQUE', libelle: 'Erreur technique' },
  { code: 'SERVICE_INDISPONIBLE', libelle: 'Service indisponible' },
];

// Écran « Ma pièce d'identité » (CUB-9, maquette écran 1) : le fichier est contrôlé dans
// le navigateur (format, taille), puis la vérification est demandée au bouchon Stripe
// Identity. Le bouchon ne reçoit pas l'image : le scénario de test choisi la remplace.
@Component({
  selector: 'app-etape-piece',
  templateUrl: './etape-piece.html',
})
export class EtapePiece {
  private readonly bouchons = inject(BouchonsIdentiteService);

  readonly dossierId = input.required<string>();
  readonly identite = input.required<Identite>();
  readonly valide = output<void>();
  readonly annule = output<string>();
  readonly retour = output<void>();

  protected readonly scenarios = SCENARIOS_PIECE;
  protected readonly scenario = signal(SCENARIOS_PIECE[0].code);
  protected readonly fichiers = signal<FichierChoisi[]>([]);
  protected readonly message = signal<{ type: 'err' | 'warn'; texte: string } | null>(null);
  protected readonly enCours = signal(false);
  protected readonly survol = signal(false);

  protected fichierValide(): boolean {
    const dernier = this.fichiers().at(-1);
    return !!dernier && dernier.refus === null;
  }

  protected choisir(event: Event): void {
    const champ = event.target as HTMLInputElement;
    this.ajouter(champ.files?.[0]);
    champ.value = '';
  }

  protected deposer(event: DragEvent): void {
    event.preventDefault();
    this.survol.set(false);
    this.ajouter(event.dataTransfer?.files[0]);
  }

  protected survoler(event: DragEvent, actif: boolean): void {
    event.preventDefault();
    this.survol.set(actif);
  }

  protected changerScenario(event: Event): void {
    this.scenario.set((event.target as HTMLSelectElement).value);
  }

  private ajouter(fichier: File | undefined): void {
    if (!fichier) {
      return;
    }
    this.message.set(null);
    this.fichiers.update((liste) => [
      ...liste,
      { nom: fichier.name, taille: tailleLisible(fichier.size), refus: refusFichier(fichier) },
    ]);
  }

  protected envoyer(): void {
    if (!this.fichierValide()) {
      this.message.set({ type: 'err', texte: "Choisissez d'abord un fichier accepté." });
      return;
    }
    const identite = this.identite();
    this.enCours.set(true);
    this.message.set(null);
    this.bouchons
      .verifierDocument(this.dossierId(), this.scenario(), {
        nomNaissance: identite.nomNaissance.trim(),
        prenoms: identite.prenoms.trim(),
        dateNaissance: versIso(identite.dateNaissance),
        dateExpiration: versIso(identite.dateExpiration),
      })
      .subscribe({
        next: (reponse) => {
          this.enCours.set(false);
          switch (reponse.suite) {
            case 'POURSUIVRE':
              this.valide.emit();
              break;
            case 'ANNULER':
              this.annule.emit(
                (reponse.motif ? `Motif : ${reponse.motif}. ` : '') + (reponse.message ?? MESSAGE_ANNULATION),
              );
              break;
            default:
              this.message.set({
                type: 'warn',
                texte: reponse.message ?? reponse.motif ?? MESSAGE_INDISPONIBLE,
              });
          }
        },
        error: () => {
          this.enCours.set(false);
          this.message.set({ type: 'warn', texte: MESSAGE_INDISPONIBLE });
        },
      });
  }
}
