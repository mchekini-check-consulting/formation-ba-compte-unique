import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ComptesService, RubriqueVersements as Rubrique } from '../comptes/comptes';
import { RubriqueVersements } from './rubrique-versements';
import { ResultatSimulation, ResultatVirementSortant, VirementsTestService } from './virements-test';

const RUBRIQUE: Rubrique = {
  iban: 'FR76 9999 9000 0199 0000 0000 105',
  mouvements: [
    {
      date: '2026-11-09T09:30:00Z',
      libelle: 'Premier versement',
      emetteur: 'Emetteur Test',
      montantCentimes: 1250,
      sortant: false,
      beneficiaire: null,
      simule: false,
    },
  ],
};

const VIREMENT_SORTANT_SIMULE = {
  date: '2026-11-10T09:30:00Z',
  libelle: 'Remboursement',
  emetteur: null,
  montantCentimes: 3000,
  sortant: true,
  beneficiaire: 'Beneficiaire Test',
  simule: true,
};

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

describe('RubriqueVersements', () => {
  let versements: ReturnType<typeof vi.fn<(id: string) => Observable<Rubrique>>>;
  let disponible: ReturnType<typeof vi.fn<() => Observable<boolean>>>;
  let simuler: ReturnType<typeof vi.fn<() => Observable<ResultatSimulation>>>;
  let simulerSortant: ReturnType<typeof vi.fn<() => Observable<ResultatVirementSortant>>>;

  beforeEach(() => {
    versements = vi.fn(() => of(RUBRIQUE));
    disponible = vi.fn(() => of(true));
    simuler = vi.fn(() => of<ResultatSimulation>({ resultat: 'CREDITE', message: 'Virement reçu' }));
    simulerSortant = vi.fn(() => of<ResultatVirementSortant>({ message: 'Virement émis', soldeCentimes: 7000 }));
    TestBed.configureTestingModule({
      providers: [
        { provide: ComptesService, useValue: { versements } },
        { provide: VirementsTestService, useValue: { disponible, simuler, simulerSortant } },
      ],
    });
  });

  async function remplirEtSimulerSortant(
    fixture: { whenStable(): Promise<unknown> },
    page: HTMLElement,
    montant: string,
  ) {
    const bouton = [...page.querySelectorAll('.test button')].find((b) =>
      b.textContent?.includes('virement sortant'),
    ) as HTMLButtonElement;
    bouton.click();
    await fixture.whenStable();
    const saisir = (id: string, valeur: string) => {
      (page.querySelector(`#${id}`) as HTMLInputElement).value = valeur;
    };
    saisir('sor-montant', montant);
    saisir('sor-beneficiaire', 'Beneficiaire Test');
    saisir('sor-iban-beneficiaire', 'DE89 3704 0044 0532 0130 00');
    saisir('sor-libelle', 'Remboursement');
    page.querySelector('form.sortant')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  async function afficher() {
    const fixture = TestBed.createComponent(RubriqueVersements);
    fixture.componentRef.setInput('compteId', '5');
    const credite = vi.fn();
    fixture.componentInstance.credite.subscribe(credite);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    return { fixture, page, credite };
  }

  async function remplirEtSimuler(fixture: { whenStable(): Promise<unknown> }, page: HTMLElement, montant: string) {
    (page.querySelector('.test button') as HTMLButtonElement).click();
    await fixture.whenStable();
    const saisir = (id: string, valeur: string) => {
      (page.querySelector(`#${id}`) as HTMLInputElement).value = valeur;
    };
    saisir('sim-montant', montant);
    saisir('sim-emetteur', 'Emetteur Test');
    saisir('sim-libelle', 'Premier versement');
    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it("affiche l'IBAN à communiquer et les mouvements du compte", async () => {
    const { page } = await afficher();
    expect(versements).toHaveBeenCalledWith('5');
    expect(page.querySelector('.iban')?.textContent).toBe('FR76 9999 9000 0199 0000 0000 105');
    const mouvement = page.querySelector('.mouvements li')?.textContent ?? '';
    expect(mouvement).toContain('09/11/2026');
    expect(mouvement).toContain('Premier versement');
    expect(mouvement).toContain('Emetteur Test');
    expect(page.querySelector('.montant')?.textContent).toContain('+12,50');
  });

  it('affiche un virement sortant simulé en débit, avec son bénéficiaire et la mention « simulé »', async () => {
    versements.mockReturnValue(of({ ...RUBRIQUE, mouvements: [VIREMENT_SORTANT_SIMULE, ...RUBRIQUE.mouvements] }));
    const { page } = await afficher();
    const [sortant, entrant] = [...page.querySelectorAll('.mouvements li')];
    expect(sortant.textContent).toContain('Remboursement');
    expect(sortant.textContent).toContain('Vers Beneficiaire Test');
    expect(sortant.querySelector('.simule')?.textContent).toBe('simulé');
    expect(sortant.querySelector('.montant')?.textContent).toContain('−30,00');
    expect(sortant.querySelector('.montant')?.classList).toContain('debit');
    expect(entrant.querySelector('.simule')).toBeNull();
    expect(entrant.querySelector('.montant')?.textContent).toContain('+12,50');
  });

  it("simule un virement sortant depuis le compte affiché, puis recharge le solde et les mouvements", async () => {
    const { fixture, page, credite } = await afficher();
    await remplirEtSimulerSortant(fixture, page, '30');
    expect(simulerSortant).toHaveBeenCalledWith({
      iban: RUBRIQUE.iban,
      montantCentimes: 3000,
      beneficiaire: 'Beneficiaire Test',
      ibanBeneficiaire: 'DE89 3704 0044 0532 0130 00',
      libelle: 'Remboursement',
    });
    expect(page.querySelector('[role=status]')?.textContent).toBe('Virement émis');
    expect(versements).toHaveBeenCalledTimes(2);
    expect(credite).toHaveBeenCalledOnce();
  });

  it('refuse un virement sortant de 0 € avec « Montant invalide » sans appeler le backend', async () => {
    const { fixture, page } = await afficher();
    await remplirEtSimulerSortant(fixture, page, '0');
    expect(simulerSortant).not.toHaveBeenCalled();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montant invalide');
  });

  it.each(['Solde insuffisant', 'IBAN invalide', 'Compte clôturé'])(
    'affiche le refus « %s » du backend sans recharger le solde',
    async (refusBackend) => {
      simulerSortant.mockReturnValue(refus(409, { message: refusBackend }));
      const { fixture, page, credite } = await afficher();
      await remplirEtSimulerSortant(fixture, page, '30');
      expect(page.querySelector('[role=alert]')?.textContent).toBe(refusBackend);
      expect(credite).not.toHaveBeenCalled();
      expect(versements).toHaveBeenCalledOnce();
    },
  );

  it('referme le formulaire ouvert par un second clic sur le même bouton', async () => {
    const { fixture, page } = await afficher();
    const sortant = [...page.querySelectorAll('.test button')][1] as HTMLButtonElement;
    sortant.click();
    await fixture.whenStable();
    expect(page.querySelector('form.sortant')).not.toBeNull();
    sortant.click();
    await fixture.whenStable();
    expect(page.querySelector('form')).toBeNull();
  });

  it("n'affiche pas d'IBAN pour un compte clôturé et indique l'absence de mouvement", async () => {
    versements.mockReturnValue(of({ iban: null, mouvements: [] }));
    const { page } = await afficher();
    expect(page.querySelector('.iban')).toBeNull();
    expect(page.textContent).toContain('Aucun mouvement pour le moment.');
  });

  it('affiche un message compréhensible si les versements ne sont pas lus', async () => {
    versements.mockReturnValue(refus(500, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Vos versements sont momentanément indisponibles.');
  });

  it("laisse l'intercepteur gérer une session fermée (401)", async () => {
    versements.mockReturnValue(refus(401, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('masque le bouton de simulation hors environnement de test', async () => {
    disponible.mockReturnValue(of(false));
    const { page } = await afficher();
    expect(page.textContent).not.toContain('Simuler un virement entrant');
  });

  it("simule un virement prérempli avec l'IBAN du compte, puis recharge les versements", async () => {
    const { fixture, page, credite } = await afficher();
    await remplirEtSimuler(fixture, page, '12,50');
    expect(simuler).toHaveBeenCalledWith({
      iban: RUBRIQUE.iban,
      montantCentimes: 1250,
      emetteur: 'Emetteur Test',
      libelle: 'Premier versement',
    });
    expect(page.querySelector('[role=status]')?.textContent).toBe('Virement reçu');
    expect(versements).toHaveBeenCalledTimes(2);
    expect(credite).toHaveBeenCalledOnce();
  });

  it('refuse un montant nul ou négatif avec « Montant invalide » sans appeler le backend', async () => {
    const { fixture, page } = await afficher();
    await remplirEtSimuler(fixture, page, '-5');
    expect(simuler).not.toHaveBeenCalled();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montant invalide');
  });

  it('affiche le renvoi à l’émetteur pour un compte clôturé, sans recharger le solde', async () => {
    simuler.mockReturnValue(of<ResultatSimulation>({ resultat: 'RENVOYE', message: 'Virement renvoyé (AC04)' }));
    const { fixture, page, credite } = await afficher();
    await remplirEtSimuler(fixture, page, '10');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Virement renvoyé (AC04)');
    expect(credite).not.toHaveBeenCalled();
  });

  it('affiche le message du backend (« Compte introuvable ») ou un message générique', async () => {
    simuler.mockReturnValue(refus(404, { message: 'Compte introuvable' }));
    const { fixture, page } = await afficher();
    await remplirEtSimuler(fixture, page, '10');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Compte introuvable');

    simuler.mockReturnValue(refus(0, null));
    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('La simulation n’a pas pu aboutir. Veuillez réessayer.');
  });
});
