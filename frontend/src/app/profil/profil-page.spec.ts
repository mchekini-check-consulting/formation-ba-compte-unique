import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SuiviSession } from '../session/suivi-session';
import { EtatProfil, Profil, ProfilService, Reponses } from './profil';
import { ProfilPage } from './profil-page';

const REPONSES: Reponses = {
  statutMatrimonial: 'VEUF',
  situationProfessionnelle: 'RETRAITE',
  patrimoine: 'PLUS_100K',
  produitsDetenus: 'AV_EUROS',
  objectif: 'TRANSMETTRE',
  horizon: 'PLUS_8_ANS',
  reactionBaisse: 'RETIRER',
  perteAcceptable: 'AUCUNE',
  financeDurable: 'AUCUNE',
  plafondRetraiteEuros: 52000,
  plafondRetraiteAnnee: 2026,
  tmi: 30,
  anneeDepartRetraite: 2030,
};

const PROFIL: Profil = {
  version: 1,
  valideLe: '2025-11-01T09:00:00Z',
  revaliderLe: '2026-11-01T09:00:00Z',
  reponses: REPONSES,
  profilInvestisseur: 'PRUDENT',
  scoreInvestisseur: 1,
};

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

describe('ProfilPage (CU-10)', () => {
  const service = { lire: vi.fn(), valider: vi.fn(), versions: vi.fn() };
  const suivi = {
    ouvrir: vi.fn(() => of({ identifiant: 'ABC', expireDansSecondes: 600 })),
    arreter: vi.fn(),
    restant: signal<number | null>(600),
    deconnecter: vi.fn(),
  };

  beforeEach(async () => {
    for (const appel of Object.values(service)) {
      appel.mockReset();
    }
    service.lire.mockReturnValue(of<EtatProfil>({ statut: 'A_COMPLETER', profil: null }));
    service.versions.mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [ProfilPage],
      providers: [
        provideRouter([]),
        { provide: ProfilService, useValue: service },
        { provide: SuiviSession, useValue: suivi },
      ],
    }).compileComponents();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(ProfilPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const envoyer = async () => {
      page.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    return { page, envoyer };
  }

  it('sans profil : statut « À compléter », questionnaire vide et toutes les questions affichées', async () => {
    const { page } = await afficher();

    expect(page.querySelector('[data-test="statut"]')?.textContent).toContain('À compléter');
    expect(page.querySelectorAll('select, input[type="number"]')).toHaveLength(13);
    expect((page.querySelector('#statutMatrimonial') as HTMLSelectElement).value).toBe('');
    expect(page.querySelector('a[href="/profil"]')?.getAttribute('aria-current')).toBe('page');
  });

  it('profil de plus de 12 mois : « À revalider » et réponses pré-remplies', async () => {
    service.lire.mockReturnValue(of<EtatProfil>({ statut: 'A_REVALIDER', profil: PROFIL }));
    service.versions.mockReturnValue(of([PROFIL]));

    const { page } = await afficher();

    expect(page.querySelector('[data-test="statut"]')?.textContent).toContain('À revalider');
    expect((page.querySelector('#statutMatrimonial') as HTMLSelectElement).value).toBe('VEUF');
    expect((page.querySelector('#plafondRetraiteEuros') as HTMLInputElement).value).toBe('52000');
    expect(page.querySelector('summary')?.textContent).toContain('Version 1 du 01/11/2025');
    expect(page.querySelector('details li')?.textContent).toBe('Statut matrimonial : Veuf(ve)');
  });

  it('valide le questionnaire, affiche le profil calculé et relit le statut', async () => {
    service.valider.mockReturnValue(of({ ...PROFIL, revaliderLe: '2027-11-24T09:00:00Z' }));
    const { page, envoyer } = await afficher();
    (page.querySelector('#statutMatrimonial') as HTMLSelectElement).value = 'MARIE';
    (page.querySelector('#consentement') as HTMLInputElement).checked = true;

    await envoyer();

    const [reponses, consentement] = service.valider.mock.calls[0];
    expect(reponses.statutMatrimonial).toBe('MARIE');
    expect(consentement).toBe(true);
    expect(page.querySelector('.message')?.textContent).toBe('Profil validé : Prudent. À revalider avant le 24/11/2027.');
    expect(service.lire).toHaveBeenCalledTimes(2);
  });

  it('affiche le refus du backend sous chaque question concernée', async () => {
    service.valider.mockReturnValue(
      refus(400, {
        message: 'Votre profil est à compléter',
        champs: { plafondRetraiteEuros: 'Saisissez le montant indiqué sur votre avis d\'imposition', consentement: 'Cochez cette case' },
      }),
    );
    const { page, envoyer } = await afficher();

    await envoyer();

    expect(page.querySelector('[role="alert"]')?.textContent).toBe('Votre profil est à compléter');
    expect(page.querySelector('#plafondRetraiteEuros')?.getAttribute('aria-invalid')).toBe('true');
    expect(page.textContent).toContain('Saisissez le montant indiqué sur votre avis d\'imposition');
    expect(page.textContent).toContain('Cochez cette case');
  });

  it('message compréhensible si le service ne répond pas', async () => {
    service.valider.mockReturnValue(refus(500, null));
    const { page, envoyer } = await afficher();
    await envoyer();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('momentanément indisponible');

    service.lire.mockReturnValue(refus(503, null));
    const { page: indisponible } = await afficher();
    expect(indisponible.querySelector('form')).toBeNull();
    expect(indisponible.textContent).toContain('momentanément indisponible');
  });
});
