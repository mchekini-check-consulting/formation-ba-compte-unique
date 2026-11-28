import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { FonctionnalitesService } from '../fonctionnalites/fonctionnalites';
import { ProduitPage } from '../produit/produit-page';
import { ProfilService } from '../profil/profil';
import { ProfilPage } from '../profil/profil-page';
import { SuiviSession } from '../session/suivi-session';
import { EtatSouscription, SouscriptionService } from '../souscription/souscription';
import { SupportsService } from '../supports/supports';
import { BrouillonSouscription } from './brouillon';
import { CleEtape, ETAPES, Tunnel, pageSuivante } from './tunnel';

const ETAT: EtatSouscription = {
  produit: 'AV',
  profil: 'VALIDE',
  etape: 'SIGNATURE',
  minimum: 100,
  montant: 500,
  origineFonds: 'REVENUS',
  originePrecision: null,
  beneficiaires: [],
  codeEnvoyeLe: null,
  bloqueeJusquAu: null,
  numeroContrat: null,
  documentsLus: true,
  profilGestionParDefaut: 'EQUILIBRE',
};

@Component({
  imports: [Tunnel],
  template: `<app-tunnel produit="AV" [etape]="etape()" [actif]="actif()"><p class="contenu">Contenu</p></app-tunnel>`,
})
class Hote {
  readonly etape = signal<CleEtape>('versement');
  readonly actif = signal(true);
}

const fonctionnalites = { provide: FonctionnalitesService, useValue: { etats: () => of({}) } };

// Lot 5 (PO, 27/11/2026) : tunnel de souscription commun au PER et à l'assurance vie.
describe('Tunnel', () => {
  const service = { etat: vi.fn() };

  async function afficher(actif = true) {
    TestBed.configureTestingModule({
      imports: [Hote],
      providers: [provideRouter([]), fonctionnalites, { provide: SouscriptionService, useValue: service }],
    });
    const fixture = TestBed.createComponent(Hote);
    fixture.componentInstance.actif.set(actif);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => service.etat.mockReset().mockReturnValue(of(ETAT)));

  it('« Étape X sur N » toujours visible, étapes faites et courante, contenu de l’étape, « Contacter mon conseiller » en « Bientôt »', async () => {
    const page = await afficher();
    expect(page.querySelector('[data-test="etape"]')?.textContent).toBe('Étape 3 sur 6 : Mon versement');
    expect(page.querySelectorAll('.tunnel-etapes li.faite')).toHaveLength(2);
    expect(page.querySelector('.tunnel-etapes [aria-current="step"]')?.textContent).toBe('Mon versement');
    expect(page.querySelector('.contenu')).not.toBeNull();
    expect(page.querySelector('[data-fonctionnalite="contact-conseiller"]')?.getAttribute('data-etat')).toBe('bientot');
  });

  it('colonne « Ma souscription » : les choix déjà faits, lus pour le produit', async () => {
    const page = await afficher();
    expect(service.etat).toHaveBeenCalledWith('AV');
    const lignes = [...page.querySelectorAll('[data-test="ma-souscription"] dl > div')].map(
      (d) => `${d.querySelector('dt')?.textContent} ${d.querySelector('dd')?.textContent}`,
    );
    expect(lignes).toEqual([
      'Produit Assurance vie',
      'Mon profil Validé',
      'Mes documents Lus',
      'Mon versement 500,00 € · Salaire ou revenus',
      'Bénéficiaires Aucun (succession)',
      'Gestion au démarrage Pilotée, profil Équilibré',
    ]);
  });

  it('hors tunnel : seul le contenu s’affiche, sans lecture', async () => {
    const page = await afficher(false);
    expect(page.querySelector('.contenu')).not.toBeNull();
    expect(page.querySelector('.tunnel')).toBeNull();
    expect(service.etat).not.toHaveBeenCalled();
  });

  it('brouillon : la saisie est gardée par produit pendant la session, puis oubliée', () => {
    const brouillons = new BrouillonSouscription();
    brouillons.garder('PER', { montant: '250', origineFonds: 'EPARGNE', precision: '', lignes: [] });
    expect(brouillons.lire('PER')?.montant).toBe('250');
    expect(brouillons.lire('AV')).toBeUndefined();
    brouillons.oublier('PER');
    expect(brouillons.lire('PER')).toBeUndefined();
  });
});

describe('ProduitPage (entrée du tunnel)', () => {
  const profils = { lire: vi.fn() };

  async function afficher(produit: 'PER' | 'AV') {
    TestBed.configureTestingModule({
      imports: [ProduitPage],
      providers: [
        provideRouter([]),
        { provide: ProfilService, useValue: profils },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ produit }) } } },
        {
          provide: SupportsService,
          useValue: {
            parametresPublics: () =>
              of({
                fraisUcPct: { PER: 0.5, AV: 0.65 },
                fondsEurosAv: { tauxBrutVisePct: 2.9, dateMiseAJour: '2026-01-01', fraisGestionPct: null },
                fondsEurosPer: { tauxBrutVisePct: 2.9, dateMiseAJour: '2026-01-01', fraisGestionPct: 0.65 },
                mentions: { 'taux-non-garanti': 'Taux non garanti.', 'capital-garanti-fonds-euros': 'Le capital net investi sur le fonds en euros est garanti par l’assureur.' },
              }),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ProduitPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { page: fixture.nativeElement as HTMLElement, navigate: vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) };
  }

  it('assurance vie : présentation, frais des unités de compte et fonds en euros lus dans les paramètres', async () => {
    const { page } = await afficher('AV');
    expect(page.querySelector('h1')?.textContent).toBe('L’assurance vie');
    expect(page.textContent).toContain('Rachat');
    expect(page.querySelector('[data-test="frais"]')?.textContent).toContain('Frais de gestion des unités de compte : 0,65 % par an');
    expect(page.querySelector('[data-test="taux-fonds-euros-AV"]')?.textContent).toContain('Taux brut visé en 2026 : 2,90 %');
    expect(page.querySelector('[data-test="ouvrir"]')?.textContent?.trim()).toBe('Ouvrir mon assurance vie');
  });

  it('PO 28/11 : PER : taux du fonds en euros, net de ses frais (2,25 %), garantie du capital ; perte en capital réservée aux UC', async () => {
    const { page } = await afficher('PER');
    const taux = page.querySelector('[data-test="taux-fonds-euros-PER"]')!;
    expect(taux.querySelector('.tfe-ligne')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Fonds en euros : Taux brut visé en 2026 : 2,90 % (mis à jour le 01/01/2026), soit 2,25 % net de frais de gestion. Taux non garanti.',
    );
    // La garantie du capital sur la ligne suivante, pour ne pas lire « non garanti » comme portant sur le capital.
    expect(taux.querySelector('.tfe-garantie')?.textContent).toBe('Le capital net investi sur le fonds en euros est garanti par l’assureur.');
    expect(taux.textContent).not.toContain('perte en capital');
  });

  it('« Ouvrir mon PER » : profil validé, documents (étape 2) ; sinon questionnaire dans le tunnel (étape 1)', async () => {
    profils.lire.mockReturnValueOnce(of({ statut: 'VALIDE', profil: null })).mockReturnValueOnce(of({ statut: 'A_REVALIDER', profil: null }));
    const { page, navigate } = await afficher('PER');
    expect(page.querySelector('[data-test="fonds-euros"]')).toBeNull();
    page.querySelector<HTMLButtonElement>('[data-test="ouvrir"]')!.click();
    expect(navigate).toHaveBeenLastCalledWith(['/decouvrir'], { queryParams: { produit: 'PER' } });
    page.querySelector<HTMLButtonElement>('[data-test="ouvrir"]')!.click();
    expect(navigate).toHaveBeenLastCalledWith(['/profil'], { queryParams: { produit: 'PER' } });
  });

  it('profil illisible : message, pas de navigation', async () => {
    profils.lire.mockReturnValue(throwError(() => new Error('503')));
    const fixture = await afficher('AV');
    fixture.page.querySelector<HTMLButtonElement>('[data-test="ouvrir"]')!.click();
    TestBed.inject(Router);
    expect(fixture.navigate).not.toHaveBeenCalled();
    await Promise.resolve();
    expect(fixture.page.querySelector('[data-test="ouvrir"]')?.hasAttribute('disabled')).toBe(false);
  });
});

describe('ProfilPage dans le tunnel (étape 1)', () => {
  it('profil validé depuis une souscription : retour automatique aux documents (étape 2)', async () => {
    const service = {
      lire: vi.fn(() => of({ statut: 'A_COMPLETER', profil: null })),
      versions: vi.fn(() => of([])),
      valider: vi.fn(() => of({ profilInvestisseur: 'EQUILIBRE', revaliderLe: '2027-11-24T09:00:00Z' })),
    };
    TestBed.configureTestingModule({
      imports: [ProfilPage],
      providers: [
        provideRouter([]),
        fonctionnalites,
        { provide: ProfilService, useValue: service },
        { provide: SouscriptionService, useValue: { etat: () => of(ETAT) } },
        { provide: SuiviSession, useValue: { ouvrir: () => of({ identifiant: 'ABC', expireDansSecondes: 600 }), arreter: vi.fn(), restant: signal(600), deconnecter: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ produit: 'AV' }) } } },
      ],
    });
    const fixture = TestBed.createComponent(ProfilPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[data-test="etape"]')?.textContent).toBe('Étape 1 sur 6 : Mon profil');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/decouvrir'], { queryParams: { produit: 'AV' } });
  });

  it('PO 28/11 : l’ordre des étapes vient de la liste ETAPES, la page suivante s’en déduit', () => {
    expect(ETAPES.map((e) => e.cle)).toEqual(['profil', 'documents', 'versement', 'signature', 'confirmation', 'gestion']);
    expect(pageSuivante('profil')).toBe('/decouvrir');
    expect(pageSuivante('documents')).toBe('/souscrire');
    expect(pageSuivante('confirmation')).toBe('/gestion');
    expect(pageSuivante('gestion')).toBeNull();
  });
});
