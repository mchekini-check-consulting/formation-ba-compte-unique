import { Component, Injector, afterNextRender, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ConnexionService } from '../connexion';
import {
  DETAIL_TAUX_LIVRET_A,
  LIVRET_A,
  Simulation,
  TAUX_LIVRET_A,
  dateLongue,
  euros,
  eurosAuCentime,
  simuler,
} from './parametres';
import { memoriserPosition, reprendrePosition } from './position-page';

/** Montant proposé à l'arrivée sur le simulateur. */
export const MONTANT_INITIAL = 5000;

/** CU-27 : ligne du tableau comparatif (seule celle du Livret A vient des paramètres). */
interface Comparaison {
  compte: string;
  taux: string;
  plafond: string;
  fiscalite: string;
  disponibilite: string;
}

// CU-27 (US-18) : page publique de présentation du Livret A, d'après la maquette « livret A.pdf »
// (en-tête, bandeau, fiche, simulateur, comparatif, questions, appel à l'action, bas de page),
// centrée sur le Livret A. « Ouvrir mon Livret A » mène au parcours de CU-31 (/livret-a), réservé aux
// clientes connectées : sans session, la garde passe par la connexion puis y revient (critère 7).
// Styles dans src/livret-a.scss (budget de taille d'un composant).
@Component({
  selector: 'app-presentation-livret-a',
  imports: [RouterLink],
  templateUrl: './presentation-livret-a.html',
})
export class PresentationLivretA {
  private readonly router = inject(Router);

  protected readonly taux = TAUX_LIVRET_A;
  protected readonly detailTaux = DETAIL_TAUX_LIVRET_A;
  protected readonly dateEffet = dateLongue(LIVRET_A.dateEffet);
  protected readonly plafond = euros(LIVRET_A.plafond);
  protected readonly minimum = euros(LIVRET_A.versementMinimum);
  protected readonly plafondNombre = LIVRET_A.plafond;
  protected readonly minimumNombre = LIVRET_A.versementMinimum;
  protected readonly euros = euros;
  protected readonly eurosAuCentime = eurosAuCentime;

  /** null tant que la session n'est pas connue : « Me connecter » s'affiche seulement sans session. */
  protected readonly connectee = signal<boolean | null>(null);
  protected readonly montant = signal(MONTANT_INITIAL);
  protected readonly simulation = signal<Simulation | null>(simuler(MONTANT_INITIAL));

  protected readonly comparatif: Comparaison[] = [
    { compte: 'Livret A', taux: `${TAUX_LIVRET_A} net`, plafond: euros(LIVRET_A.plafond), fiscalite: 'Exonéré', disponibilite: 'Immédiate' },
    { compte: 'LDDS', taux: '1,70 % net', plafond: '12 000 €', fiscalite: 'Exonéré', disponibilite: 'Immédiate' },
    { compte: 'LEP', taux: '2,50 % net', plafond: '10 000 €', fiscalite: 'Exonéré', disponibilite: 'Immédiate' },
    { compte: 'CEL', taux: '1,25 % brut', plafond: '15 300 €', fiscalite: 'Prélèvement forfaitaire', disponibilite: 'Immédiate' },
    { compte: 'PEL', taux: '2,00 % brut', plafond: '61 200 €', fiscalite: 'Prélèvement forfaitaire', disponibilite: 'Bloqué 4 ans min.' },
  ];

  constructor() {
    // La lecture de la session ne la prolonge pas (CU-17).
    inject(ConnexionService)
      .session()
      .subscribe({
        next: () => this.connectee.set(true),
        error: () => this.connectee.set(false),
      });
    // Critère 8 : retour du parcours à l'endroit où la cliente l'avait quitté (haut de page sinon).
    const position = reprendrePosition();
    afterNextRender(() => window.scrollTo(0, position), { injector: inject(Injector) });
  }

  /** Critère 6 et 7 : ouverture du parcours ; la position est gardée pour « Retour au site ». */
  protected ouvrir(evenement: Event): void {
    evenement.preventDefault();
    this.garderPosition();
    void this.router.navigateByUrl('/livret-a');
  }

  /** Critère 7 et 8 : « Me connecter » mène aussi au parcours, avec retour possible au même endroit. */
  protected garderPosition(): void {
    memoriserPosition(window.scrollY);
  }

  /** Liens internes à la page (« #simulateur »...) : défilement sans quitter la page. */
  protected allerA(evenement: Event, rubrique: string): void {
    evenement.preventDefault();
    document.getElementById(rubrique)?.scrollIntoView?.({ behavior: 'smooth' });
  }

  protected saisir(valeur: string): void {
    const montant = valeur.trim() === '' ? Number.NaN : Number(valeur.replace(',', '.'));
    // Champ vidé ou invalide : les deux champs gardent leur dernière valeur valable, sans résultat.
    if (Number.isFinite(montant) && montant > 0) {
      this.montant.set(montant);
    }
    this.simulation.set(simuler(montant));
  }
}
