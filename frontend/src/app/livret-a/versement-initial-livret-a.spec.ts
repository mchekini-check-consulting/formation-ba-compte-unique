import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Compte, ComptesService } from '../comptes/comptes';
import { DemandeLivretA, LivretAService } from './livret-a';
import {
  MINIMUM,
  MONTANT_INVALIDE,
  PLAFOND,
  SANS_COMPTE_COURANT,
  SOLDE_INSUFFISANT,
  VersementInitialLivretA,
  lireMontant,
} from './versement-initial-livret-a';

const VALIDEE: DemandeLivretA = {
  reference: 'ref-1',
  statut: 'OUVERTURE_VALIDEE',
  message: 'Ouverture validée.',
  attestationLe: '2026-11-26T09:00:00Z',
  partageDetailAutorise: true,
  choixPartageLe: '2026-11-26T09:00:00Z',
  interrogationLe: '2026-11-26T09:00:00Z',
  reponseFiscale: 'AUCUN_LIVRET_A',
  retard: false,
  delai: '2 jours ouvrés',
  transmission: null,
  versementInitialCentimes: null,
};
const AVEC_VERSEMENT: DemandeLivretA = { ...VALIDEE, versementInitialCentimes: 15050, versementSaisiLe: '2026-11-26T10:00:00Z' };
const COMPTE: Compte = {
  id: 1,
  ibanMasque: '•••• 0105',
  statut: 'ACTIF',
  soldeCentimes: 124050,
  soldeMisAJourLe: null,
  premierVersementAttendu: false,
};

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));
/** Montants affichés avec des espaces insécables : ramenés à des espaces simples pour la comparaison. */
const espaces = (texte: string) => texte.replace(/[  ]/g, ' ');

// Cas de test de CU-33 (US-24) : contrôles du montant, mêmes règles que le backend.
describe('lireMontant (CU-33)', () => {
  it.each([
    ['10', 1000],
    ['22950', 2295000],
    ['150,50', 15050],
    ['150.5', 15050],
    [' 1 240,50 ', 124050],
  ])('accepte %s', (saisie, centimes) => {
    expect(lireMontant(saisie, null)).toEqual({ centimes });
  });

  it.each([
    ['', MINIMUM],
    ['9,99', MINIMUM],
    ['0', MINIMUM],
    ['22950,01', PLAFOND],
    ['9999999999', PLAFOND],
    ['-50', MONTANT_INVALIDE],
    ['abc', MONTANT_INVALIDE],
    ['150,505', MONTANT_INVALIDE],
  ])('refuse %s', (saisie, erreur) => {
    expect(lireMontant(saisie, null)).toEqual({ erreur });
  });

  it('refuse un montant supérieur au solde du compte courant', () => {
    expect(lireMontant('150', 14999)).toEqual({ erreur: SOLDE_INSUFFISANT });
    expect(lireMontant('149,99', 14999)).toEqual({ centimes: 14999 });
  });
});

describe('VersementInitialLivretA', () => {
  const service = { choisirVersementInitial: vi.fn() };
  const comptes = { mesComptes: vi.fn() };
  let fixture: ComponentFixture<VersementInitialLivretA>;
  let page: HTMLElement;
  let emises: DemandeLivretA[];

  beforeEach(() => {
    service.choisirVersementInitial.mockReset();
    comptes.mesComptes.mockReset();
    comptes.mesComptes.mockReturnValue(of([{ ...COMPTE, id: 2, statut: 'CLOTURE' }, COMPTE]));
    service.choisirVersementInitial.mockReturnValue(of(AVEC_VERSEMENT));
    TestBed.configureTestingModule({
      imports: [VersementInitialLivretA],
      providers: [
        { provide: LivretAService, useValue: service },
        { provide: ComptesService, useValue: comptes },
      ],
    });
  });

  async function afficher(demande: DemandeLivretA = VALIDEE) {
    fixture = TestBed.createComponent(VersementInitialLivretA);
    fixture.componentRef.setInput('demande', demande);
    emises = [];
    fixture.componentInstance.demandeChange.subscribe((d) => emises.push(d));
    page = fixture.nativeElement;
    await fixture.whenStable();
  }

  const saisir = async (valeur: string) => {
    const champ = page.querySelector<HTMLInputElement>('input[name=montant]')!;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };
  const valider = async () => {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  it('propose seulement le compte courant actif, avec son solde', async () => {
    await afficher();
    const source = espaces(page.querySelector('fieldset')!.textContent!);
    expect(source).toContain('Compte courant Hi-Tech Bank •••• 0105');
    expect(source).toContain('1 240,50 €');
    expect(page.querySelectorAll('input[name=source]').length).toBe(1);
  });

  it('montant refusé avant envoi : message, rien n’est envoyé', async () => {
    await afficher();
    await saisir('9,99');
    await valider();
    expect(page.querySelector('#err-montant')?.textContent).toBe(MINIMUM);
    await saisir('1240,51');
    await valider();
    expect(page.querySelector('#err-montant')?.textContent).toBe(SOLDE_INSUFFISANT);
    expect(service.choisirVersementInitial).not.toHaveBeenCalled();
  });

  it('montant accepté : envoyé au backend, demande mise à jour émise', async () => {
    await afficher();
    await saisir(' 150,50 ');
    await valider();
    expect(service.choisirVersementInitial).toHaveBeenCalledWith('ref-1', '150,50');
    expect(emises).toEqual([AVEC_VERSEMENT]);
  });

  it('récapitulatif avant signature : montant, compte, taux, intérêts estimés ; montant modifiable', async () => {
    await afficher(AVEC_VERSEMENT);
    const recap = espaces(page.querySelector('.recap')!.textContent!);
    expect(recap).toContain('150,50 €');
    expect(recap).toContain('•••• 0105');
    expect(recap).toContain('1,70 %');
    expect(recap).toContain('2,56 €');
    expect(recap).toContain('Prochaine étape : la signature de votre convention.');
    page.querySelector<HTMLButtonElement>('button.secondaire')!.click();
    await fixture.whenStable();
    expect(page.querySelector('input[name=montant]')).not.toBeNull();
  });

  it('refus du backend : son message est affiché ; 401 sans message', async () => {
    service.choisirVersementInitial.mockReturnValue(refus(400, { message: SOLDE_INSUFFISANT }));
    await afficher();
    await saisir('150');
    await valider();
    expect(page.querySelector('[role=alert]')?.textContent).toBe(SOLDE_INSUFFISANT);
    expect(emises).toEqual([]);

    service.choisirVersementInitial.mockReturnValue(refus(401, null));
    fixture.componentInstance['erreur'].set(null);
    await valider();
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('compte courant illisible ou absent : message, pas de formulaire', async () => {
    comptes.mesComptes.mockReturnValue(refus(500, null));
    await afficher();
    expect(page.querySelector('form')).toBeNull();
    expect(page.textContent).toContain(SANS_COMPTE_COURANT);
  });
});
