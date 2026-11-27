import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ComptesService } from '../comptes/comptes';
import { VirementsService } from '../versement/virements';
import { VirementsTestService } from '../versement/virements-test';
import { MOTIF_REFUS_BANQUE, VirementsTestPage } from './virements-test-page';

const EXECUTE = {
  date: '2026-11-11T09:30:00Z',
  libelle: 'Loyer',
  emetteur: null,
  montantCentimes: 50000,
  sortant: true,
  beneficiaire: 'Test Cliente-B',
  simule: false,
  statut: 'exécuté',
  virementId: 21,
};
const RUBRIQUE = { iban: 'FR76 9999 9000 0199 0000 0000 105', mouvements: [EXECUTE] };
const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

// D4 : simulations de CU-143 (TECH-04) et CU-147 critère 14, sorties de « Faire un virement ».
describe('VirementsTestPage (/test/virements)', () => {
  const comptes = { mesComptes: vi.fn(), versements: vi.fn() };
  const entrants = { simuler: vi.fn() };
  const virements = { simulerRefus: vi.fn() };

  beforeEach(() => {
    [...Object.values(comptes), entrants.simuler, virements.simulerRefus].forEach((f) => f.mockReset());
    comptes.mesComptes.mockReturnValue(of([{ id: 5 }]));
    comptes.versements.mockReturnValue(of(RUBRIQUE));
    TestBed.configureTestingModule({
      imports: [VirementsTestPage],
      providers: [
        provideRouter([]),
        { provide: ComptesService, useValue: comptes },
        { provide: VirementsTestService, useValue: entrants },
        { provide: VirementsService, useValue: virements },
      ],
    });
  });

  async function afficher() {
    const fixture = TestBed.createComponent(VirementsTestPage);
    fixture.detectChanges();
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const rafraichir = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    const simuler = async (montant: string) => {
      const saisir = (id: string, valeur: string) => (page.querySelector<HTMLInputElement>(`#${id}`)!.value = valeur);
      saisir('sim-montant', montant);
      saisir('sim-emetteur', 'Emetteur Test');
      saisir('sim-libelle', 'Premier versement');
      page.querySelector('form')!.dispatchEvent(new Event('submit'));
      await rafraichir();
    };
    return { page, rafraichir, simuler };
  }

  it("simule un virement entrant prérempli avec l'IBAN du compte (CU-143), puis relit les mouvements", async () => {
    entrants.simuler.mockReturnValue(of({ resultat: 'CREDITE', message: 'Virement reçu' }));
    const { page, simuler } = await afficher();
    expect(comptes.versements).toHaveBeenCalledWith('5');
    expect(page.querySelector<HTMLInputElement>('#sim-iban')!.value).toBe(RUBRIQUE.iban);
    await simuler('12,50');
    expect(entrants.simuler).toHaveBeenCalledWith({ iban: RUBRIQUE.iban, montantCentimes: 1250, emetteur: 'Emetteur Test', libelle: 'Premier versement' });
    expect(page.querySelector('[role=status]')?.textContent).toBe('Virement reçu');
    expect(comptes.versements).toHaveBeenCalledTimes(2);
  });

  it('montant invalide sans appel ; renvoi à l’émetteur ; message du backend ou générique', async () => {
    const { page, simuler } = await afficher();
    await simuler('-5');
    expect(entrants.simuler).not.toHaveBeenCalled();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montant invalide');

    entrants.simuler.mockReturnValueOnce(of({ resultat: 'RENVOYE', message: 'Virement renvoyé (AC04)' }));
    await simuler('10');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Virement renvoyé (AC04)');

    entrants.simuler.mockReturnValueOnce(refus(404, { message: 'Compte introuvable' }));
    await simuler('10');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Compte introuvable');

    entrants.simuler.mockReturnValueOnce(refus(0, null));
    await simuler('10');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('La simulation n’a pas pu aboutir. Veuillez réessayer.');
  });

  it('CU-147 critère 14 : refus de la banque simulé sur un virement exécuté, puis relecture ; refus du bouchon', async () => {
    virements.simulerRefus
      .mockReturnValueOnce(of({ message: 'Virement refusé : recrédité', soldeCentimes: 10000 }))
      .mockReturnValueOnce(refus(409, { message: 'Seul un virement exécuté peut être refusé' }));
    const { page, rafraichir } = await afficher();
    const bouton = () => page.querySelector<HTMLButtonElement>('[data-test="executes"] button')!;
    bouton().click();
    await rafraichir();
    expect(virements.simulerRefus).toHaveBeenCalledWith(21, MOTIF_REFUS_BANQUE);
    expect(page.querySelector('[role=status]')?.textContent).toBe('Virement refusé : recrédité');
    bouton().click();
    await rafraichir();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Seul un virement exécuté peut être refusé');
  });

  it('sans compte, sans session ou mouvements illisibles : message', async () => {
    comptes.mesComptes.mockReturnValue(of([]));
    expect((await afficher()).page.querySelector('[role=alert]')?.textContent).toContain('Aucun compte courant');
    comptes.mesComptes.mockReturnValue(refus(401, null));
    expect((await afficher()).page.querySelector('[role=alert]')?.textContent).toContain('Connectez-vous d’abord');
    comptes.mesComptes.mockReturnValue(of([{ id: 5 }]));
    comptes.versements.mockReturnValue(refus(500, null));
    expect((await afficher()).page.querySelector('[role=alert]')?.textContent).toContain('indisponibles');
  });
});
