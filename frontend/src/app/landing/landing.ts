import { Component, Injector, afterNextRender, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Controles } from '../identification/dossier';
import { EtapeIdentite } from '../identification/etape-identite';
import { EtapePiece } from '../identification/etape-piece';
import { EtapeQuestions } from '../identification/etape-questions';
import { EtapeSelfie } from '../identification/etape-selfie';
import { Identite } from '../identification/regles';

const DERNIERE_ETAPE = 6;

// Landing page Hi-Tech Bank et parcours d'ouverture de compte. Les étapes 1 à 4
// (« M'identifier », CUB-8 à CUB-12) sont branchées sur le backend bouchons-identite ;
// les étapes 5 et 6 restent une maquette sans backend.
// Les styles sont dans src/landing.scss et src/identification.scss : ils dépassent le
// budget de taille d'un composant.
@Component({
  selector: 'app-landing',
  imports: [RouterLink, EtapeIdentite, EtapePiece, EtapeSelfie, EtapeQuestions],
  templateUrl: './landing.html',
})
export class Landing {
  private readonly injector = inject(Injector);
  protected readonly parcoursOuvert = signal(false);
  protected readonly etape = signal(1);
  protected readonly totalEtapes = DERNIERE_ETAPE;
  /** Identifiant de la demande, transmis aux bouchons de vérification. */
  protected dossierId = '';
  protected readonly identite = signal<Identite | null>(null);
  /** Message d'annulation : la demande en ligne ne peut plus continuer. */
  protected readonly annulation = signal<string | null>(null);
  /** Résultat des contrôles sanctions et PEP, quand ce sont eux qui ont annulé la demande. */
  protected readonly controlesAnnulation = signal<Controles | null>(null);
  // Position de défilement de la landing au moment d'ouvrir le parcours (CUB-7 : le retour
  // au site ramène le visiteur là où il était).
  private positionDefilement = 0;

  protected ouvrirParcours(event: Event): void {
    event.preventDefault();
    this.positionDefilement = window.scrollY;
    this.dossierId = crypto.randomUUID();
    this.identite.set(null);
    this.annulation.set(null);
    this.controlesAnnulation.set(null);
    this.parcoursOuvert.set(true);
    this.allerA(1);
  }

  protected fermerParcours(event?: Event): void {
    event?.preventDefault();
    this.parcoursOuvert.set(false);
    const position = this.positionDefilement;
    // La landing doit être réaffichée avant de pouvoir y redéfiler.
    afterNextRender(() => window.scrollTo(0, position), { injector: this.injector });
  }

  protected identiteValidee(identite: Identite): void {
    this.identite.set(identite);
    this.suivant();
  }

  protected annuler(message: string, controles: Controles | null = null): void {
    this.annulation.set(message);
    this.controlesAnnulation.set(controles);
    window.scrollTo(0, 0);
  }

  protected suivant(): void {
    if (this.etape() < DERNIERE_ETAPE) {
      this.allerA(this.etape() + 1);
    } else {
      this.fermerParcours();
    }
  }

  protected precedent(): void {
    if (this.etape() > 1) {
      this.allerA(this.etape() - 1);
    } else {
      this.fermerParcours();
    }
  }

  private allerA(etape: number): void {
    this.etape.set(etape);
    window.scrollTo(0, 0);
  }
}
