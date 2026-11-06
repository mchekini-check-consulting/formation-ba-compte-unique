import { Component, Injector, afterNextRender, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Controles, DossierService, RepriseOuverture } from '../identification/dossier';
import { EtapeIdentite } from '../identification/etape-identite';
import { EtapePiece } from '../identification/etape-piece';
import { EtapeQuestions } from '../identification/etape-questions';
import { EtapeSelfie } from '../identification/etape-selfie';
import { Identite } from '../identification/regles';
import { EtapeCompteOuvert } from '../ouverture/etape-compte-ouvert';
import { EtapeSignature } from '../ouverture/etape-signature';
import { OuvertureService, dossierEnSignature, memoriserDossierEnSignature } from '../ouverture/ouverture';

const DERNIERE_ETAPE = 6;
const ETAPE_SIGNATURE = 5;

// Landing page Hi-Tech Bank et parcours d'ouverture de compte. Les étapes 1 à 4
// (« M'identifier », CUB-8 à CUB-12) sont branchées sur le backend bouchons-identite ;
// l'étape 5 signe la convention (CU-12, bouchon Docusign CU-89) et l'étape 6 confirme le
// compte ouvert (CU-13).
// Les styles sont dans src/landing.scss et src/identification.scss : ils dépassent le
// budget de taille d'un composant.
@Component({
  selector: 'app-landing',
  imports: [RouterLink, EtapeIdentite, EtapePiece, EtapeSelfie, EtapeQuestions, EtapeSignature, EtapeCompteOuvert],
  templateUrl: './landing.html',
})
export class Landing {
  private readonly injector = inject(Injector);
  private readonly ouvertures = inject(OuvertureService);
  private readonly dossiers = inject(DossierService);
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
    this.identite.set(null);
    this.annulation.set(null);
    this.controlesAnnulation.set(null);
    this.parcoursOuvert.set(true);
    const enSignature = dossierEnSignature();
    if (enSignature) {
      this.reprendreLaSignature(enSignature);
    } else {
      this.nouvelleDemande();
    }
  }

  private nouvelleDemande(): void {
    this.dossierId = crypto.randomUUID();
    this.allerA(1);
  }

  /** CU-12 : après une fermeture de l'espace, la signature reprend au même endroit. */
  private reprendreLaSignature(reference: string): void {
    this.ouvertures.signature(reference).subscribe({
      next: (signature) => {
        if (signature.etat === 'ENVOYEE' || signature.etat === 'EXPIREE') {
          this.dossierId = reference;
          this.allerA(ETAPE_SIGNATURE);
        } else {
          memoriserDossierEnSignature(null);
          this.nouvelleDemande();
        }
      },
      error: () => {
        memoriserDossierEnSignature(null);
        this.nouvelleDemande();
      },
    });
  }

  /**
   * CU-83 : pendant le parcours, les rubriques du site ne sont pas affichées. Un lien du menu
   * (« #compte »...) ramène donc au site, puis à la rubrique, au lieu de ne rien faire.
   */
  protected allerALaRubrique(event: MouseEvent): void {
    const lien = (event.target as Element | null)?.closest?.('a[href^="#"]');
    if (!lien || !this.parcoursOuvert()) {
      return;
    }
    event.preventDefault();
    const rubrique = lien.getAttribute('href')!.slice(1);
    this.parcoursOuvert.set(false);
    afterNextRender(() => document.getElementById(rubrique)?.scrollIntoView?.(), { injector: this.injector });
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

  /**
   * CU-9 : une pièce, un selfie ou une réponse refusés annulent la demande « par contrôle » ; le
   * dossier est marqué pour qu'une nouvelle ouverture en ligne ne soit pas possible. Le message
   * s'affiche même si l'enregistrement échoue.
   */
  protected annulerParControle(message: string): void {
    this.dossiers.annuler(this.dossierId, 'CONTROLE_AUTOMATIQUE').subscribe({ error: () => undefined });
    this.annuler(message);
  }

  /**
   * CU-9 critère 4 : reprise du dossier en cours de la même personne. S'il attend encore la
   * cliente, l'étape « Mon identité » enregistre la saisie sur ce dossier et le parcours continue ;
   * s'il attend la banque (convention signée...), le parcours passe au suivi de l'ouverture.
   */
  protected reprendreLeDossier(reprise: RepriseOuverture): void {
    this.dossierId = reprise.reference;
    if (reprise.etape === 'OUVERTURE') {
      this.allerA(DERNIERE_ETAPE);
    }
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
