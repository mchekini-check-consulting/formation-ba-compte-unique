import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Beneficiaire, BeneficiairesService } from '../beneficiaires/beneficiaires';
import { FormulaireVirement } from './formulaire-virement';
import { Recapitulatif, VirementsService } from './virements';

const PAPA: Beneficiaire = {
  id: 5,
  libelle: 'Papa',
  nomVerifie: 'Test Cliente-B',
  ibanMasque: '•••• 0202',
  ajouteLe: '2026-11-11T09:00:00Z',
  verification: 'vérifié',
};
const MAMAN: Beneficiaire = { ...PAPA, id: 6, libelle: 'Maman', nomVerifie: 'Test Cliente-C', ibanMasque: '•••• 0303' };

const RECAP: Recapitulatif = {
  virementId: 21,
  beneficiaire: 'Maman',
  nomBeneficiaire: 'Test Cliente-C',
  ibanMasque: '•••• 0303',
  montantCentimes: 1250,
  libelle: 'Loyer',
  message: 'Un code à 6 chiffres vous a été envoyé par SMS.',
};

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

describe('FormulaireVirement (CU-147)', () => {
  const beneficiaires = { lister: vi.fn() };
  const virements = { preparer: vi.fn(), signer: vi.fn() };

  beforeEach(async () => {
    beneficiaires.lister.mockReset().mockReturnValue(of([PAPA, MAMAN]));
    virements.preparer.mockReset().mockReturnValue(of(RECAP));
    virements.signer.mockReset().mockReturnValue(of({ message: 'Virement exécuté', soldeCentimes: 7000 }));
    await TestBed.configureTestingModule({
      imports: [FormulaireVirement],
      providers: [
        provideRouter([]),
        { provide: BeneficiairesService, useValue: beneficiaires },
        { provide: VirementsService, useValue: virements },
      ],
    }).compileComponents();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(FormulaireVirement);
    const execute = vi.fn();
    fixture.componentInstance.execute.subscribe(execute);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const saisir = (id: string, valeur: string) => {
      (page.querySelector(`#${id}`) as HTMLInputElement).value = valeur;
    };
    const envoyer = async (selecteur: string) => {
      page.querySelector(selecteur)?.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    const choisir = async (id: string) => {
      const select = page.querySelector('#vir-beneficiaire') as HTMLSelectElement;
      select.value = id;
      select.dispatchEvent(new Event('change'));
      await fixture.whenStable();
    };
    return { fixture, page, execute, saisir, envoyer, choisir };
  }

  it('critères 1 et 2 : choix d’un bénéficiaire de la liste, nom et IBAN masqué affichés sans saisie', async () => {
    const { page, choisir } = await afficher();
    const options = [...page.querySelectorAll('option')].map((o) => o.textContent?.trim());
    expect(options).toEqual(['Papa', 'Maman']);
    expect(page.querySelector('.verifie')?.textContent).toBe('Test Cliente-B · •••• 0202');
    await choisir('6');
    expect(page.querySelector('.verifie')?.textContent).toBe('Test Cliente-C · •••• 0303');
    expect(page.querySelector('#vir-iban')).toBeNull();
  });

  it('critères 9 et 10 : récapitulatif, code SMS, puis virement exécuté', async () => {
    const { page, execute, saisir, envoyer, choisir } = await afficher();
    await choisir('6');
    saisir('vir-montant', '12,50');
    saisir('vir-libelle', 'Loyer');
    await envoyer('form.saisie');
    expect(virements.preparer).toHaveBeenCalledWith({ beneficiaireId: 6, montant: '12,50', libelle: 'Loyer' });
    const recap = page.querySelector('.recap')?.textContent ?? '';
    expect(recap).toContain('Maman (Test Cliente-C)');
    expect(recap).toContain('•••• 0303');
    expect(recap).toContain('12,50');
    expect(recap).toContain('Loyer');

    saisir('vir-code', ' 123456 ');
    await envoyer('form.signature');
    expect(virements.signer).toHaveBeenCalledWith(21, '123456');
    expect(page.querySelector('[role=status]')?.textContent).toBe('Virement exécuté');
    expect(page.querySelector('form.saisie')).not.toBeNull();
    expect(execute).toHaveBeenCalledOnce();
  });

  it.each(['0', '-5', '12,505', 'abc'])('critère 3 : « %s » est un montant invalide, sans appel', async (montant) => {
    const { page, saisir, envoyer } = await afficher();
    saisir('vir-montant', montant);
    await envoyer('form.saisie');
    expect(virements.preparer).not.toHaveBeenCalled();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montant invalide');
  });

  it('critère 7 : un libellé de plus de 140 caractères est refusé, sans appel', async () => {
    const { page, saisir, envoyer } = await afficher();
    saisir('vir-montant', '10');
    saisir('vir-libelle', 'x'.repeat(141));
    await envoyer('form.saisie');
    expect(virements.preparer).not.toHaveBeenCalled();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Libellé trop long');
  });

  it.each([
    'Solde insuffisant',
    'Montant supérieur au plafond de virement',
    'Premier virement vers ce bénéficiaire : montant limité à 500 €',
    'Compte clôturé',
  ])('critères 4 à 8 : affiche le refus « %s » du backend', async (message) => {
    virements.preparer.mockReturnValue(refus(422, { message, termine: false }));
    const { page, saisir, envoyer } = await afficher();
    saisir('vir-montant', '600');
    await envoyer('form.saisie');
    expect(page.querySelector('[role=alert]')?.textContent).toBe(message);
    expect(page.querySelector('.recap')).toBeNull();
  });

  it('critères 11 et 12 : code incorrect -> on reste sur le code ; virement annulé -> retour à la saisie', async () => {
    const { page, saisir, envoyer, execute } = await afficher();
    saisir('vir-montant', '10');
    await envoyer('form.saisie');

    virements.signer.mockReturnValue(refus(400, { message: 'Code incorrect', termine: false }));
    await envoyer('form.signature');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Code incorrect');
    expect(page.querySelector('.recap')).not.toBeNull();

    virements.signer.mockReturnValue(refus(410, { message: 'Code expiré', termine: true }));
    await envoyer('form.signature');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Code expiré');
    expect(page.querySelector('.recap')).toBeNull();
    expect(execute).not.toHaveBeenCalled();
  });

  it('« Annuler » revient à la saisie ; une session fermée (401) ne montre rien', async () => {
    const { fixture, page, saisir, envoyer } = await afficher();
    saisir('vir-montant', '10');
    await envoyer('form.saisie');
    ([...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Annuler')) as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(page.querySelector('form.saisie')).not.toBeNull();

    virements.preparer.mockReturnValue(refus(401, null));
    saisir('vir-montant', '10');
    await envoyer('form.saisie');
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('un backend qui ne répond pas donne un message compréhensible', async () => {
    virements.preparer.mockReturnValue(refus(0, null));
    const { page, saisir, envoyer } = await afficher();
    saisir('vir-montant', '10');
    await envoyer('form.saisie');
    expect(page.querySelector('[role=alert]')?.textContent).toBe(
      'Le virement est momentanément indisponible. Veuillez réessayer plus tard.',
    );
  });

  it('critère 16 : sans bénéficiaire, invite à en ajouter un ; liste indisponible -> message', async () => {
    beneficiaires.lister.mockReturnValue(of([]));
    let { page } = await afficher();
    expect(page.querySelector('.vide')?.textContent).toContain('Aucun bénéficiaire enregistré.');
    expect(page.querySelector('a[href="/beneficiaires"]')).not.toBeNull();

    beneficiaires.lister.mockReturnValue(refus(500, null));
    ({ page } = await afficher());
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Vos bénéficiaires sont momentanément indisponibles.');

    beneficiaires.lister.mockReturnValue(refus(401, null));
    ({ page } = await afficher());
    expect(page.querySelector('[role=alert]')).toBeNull();
  });
});
