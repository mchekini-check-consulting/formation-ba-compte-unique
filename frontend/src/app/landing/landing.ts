import { Component, signal } from '@angular/core';

const DERNIERE_ETAPE = 6;

// Landing page Hi-Tech Bank et parcours d'ouverture de compte (maquette sans backend :
// les champs du parcours ne sont ni enregistrés ni envoyés).
// Les styles sont dans src/landing.scss : ils dépassent le budget de taille d'un composant.
@Component({
  selector: 'app-landing',
  templateUrl: './landing.html',
})
export class Landing {
  protected readonly parcoursOuvert = signal(false);
  protected readonly etape = signal(1);
  protected readonly totalEtapes = DERNIERE_ETAPE;

  protected ouvrirParcours(event: Event): void {
    event.preventDefault();
    this.parcoursOuvert.set(true);
    this.allerA(1);
  }

  protected fermerParcours(event?: Event): void {
    event?.preventDefault();
    this.parcoursOuvert.set(false);
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
