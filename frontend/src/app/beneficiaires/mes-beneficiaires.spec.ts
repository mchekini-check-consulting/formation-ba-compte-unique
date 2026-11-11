import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SuiviSession } from '../session/suivi-session';
import { AjoutEnAttente, Beneficiaire, BeneficiairesService } from './beneficiaires';
import { MesBeneficiaires } from './mes-beneficiaires';

const PAPA: Beneficiaire = {
  id: 5,
  libelle: 'Papa',
  nomVerifie: 'Test Cliente-B',
  ibanMasque: '•••• 0202',
  ajouteLe: '2026-11-11T09:00:00Z',
  verification: 'vérifié',
};

const ATTENTE: AjoutEnAttente = { ajoutId: 11, nomVerifie: 'Test Cliente-B', message: 'Un code vous a été envoyé.' };

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

describe('MesBeneficiaires (CU-146)', () => {
  const service = {
    lister: vi.fn(),
    ajouter: vi.fn(),
    confirmer: vi.fn(),
    renommer: vi.fn(),
    supprimer: vi.fn(),
  };
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
    service.lister.mockReturnValue(of([PAPA]));
    service.ajouter.mockReturnValue(of(ATTENTE));
    service.confirmer.mockReturnValue(of(PAPA));
    service.renommer.mockReturnValue(of({ ...PAPA, libelle: 'Maman' }));
    service.supprimer.mockReturnValue(of(undefined));
    await TestBed.configureTestingModule({
      imports: [MesBeneficiaires],
      providers: [
        provideRouter([]),
        { provide: BeneficiairesService, useValue: service },
        { provide: SuiviSession, useValue: suivi },
      ],
    }).compileComponents();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(MesBeneficiaires);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const stable = () => fixture.whenStable();
    const cliquer = async (texte: string) => {
      const bouton = [...page.querySelectorAll('button')].find((b) => b.textContent?.includes(texte));
      (bouton as HTMLButtonElement).click();
      await stable();
    };
    const saisir = (id: string, valeur: string) => {
      (page.querySelector(`#${id}`) as HTMLInputElement).value = valeur;
    };
    const envoyer = async (selecteur: string) => {
      page.querySelector(selecteur)?.dispatchEvent(new Event('submit'));
      await stable();
    };
    return { fixture, page, cliquer, saisir, envoyer };
  }

  it('critère 11 : liste libellé, nom vérifié, IBAN masqué, date d’ajout et « vérifié », dans le menu', async () => {
    const { page } = await afficher();
    expect(page.querySelector('h1')?.textContent).toBe('Mes bénéficiaires');
    expect(page.querySelector('aside a[aria-current="page"]')?.textContent).toContain('Mes bénéficiaires');
    const ligne = page.querySelector('.liste li')?.textContent ?? '';
    expect(ligne).toContain('Papa');
    expect(ligne).toContain('Test Cliente-B · •••• 0202');
    expect(ligne).toContain('Ajouté le 11/11/2026');
    expect(page.querySelector('.verifie')?.textContent).toBe('vérifié');
    expect(page.querySelector('a[href="/versement"]')).not.toBeNull();
  });

  it('indique l’absence de bénéficiaire, ou une liste indisponible', async () => {
    service.lister.mockReturnValue(of([]));
    let { page } = await afficher();
    expect(page.querySelector('.vide')?.textContent).toBe('Aucun bénéficiaire pour le moment.');

    service.lister.mockReturnValue(refus(500, null));
    ({ page } = await afficher());
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Vos bénéficiaires sont momentanément indisponibles.');

    service.lister.mockReturnValue(refus(401, null));
    ({ page } = await afficher());
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('critères 1 et 8 : ajoute un bénéficiaire avec le code reçu par SMS, puis relit la liste', async () => {
    const { page, cliquer, saisir, envoyer } = await afficher();
    await cliquer('Ajouter un bénéficiaire');
    saisir('benef-iban', 'FR76 9999 9000 0199 0000 0000 202');
    saisir('benef-nom', 'Test Cliente-B');
    saisir('benef-libelle', 'Papa');
    await envoyer('form.ajout');
    expect(service.ajouter).toHaveBeenCalledWith({
      iban: 'FR76 9999 9000 0199 0000 0000 202',
      nom: 'Test Cliente-B',
      libelle: 'Papa',
    });
    expect(page.querySelector('form.code h3')?.textContent).toContain('Test Cliente-B');

    saisir('code-sms', ' 123456 ');
    await envoyer('form.code');
    expect(service.confirmer).toHaveBeenCalledWith(11, '123456');
    expect(page.querySelector('[role=status]')?.textContent).toBe('Bénéficiaire « Papa » ajouté.');
    expect(page.querySelector('form.code')).toBeNull();
    expect(service.lister).toHaveBeenCalledTimes(2);
  });

  it('critère 3 : un nom vide est refusé sans appeler le backend', async () => {
    const { page, cliquer, saisir, envoyer } = await afficher();
    await cliquer('Ajouter un bénéficiaire');
    saisir('benef-iban', 'FR76');
    await envoyer('form.ajout');
    expect(service.ajouter).not.toHaveBeenCalled();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Indiquez un nom pour ce bénéficiaire');
  });

  it.each([
    'IBAN invalide',
    'Le nom ne correspond pas à ce compte. Vérifiez la saisie.',
    'Ce compte n’a pas pu être trouvé. Vérifiez l’IBAN.',
    'La vérification est momentanément indisponible. Réessayez plus tard.',
    'Ce bénéficiaire existe déjà',
  ])('critères 2 à 7 : affiche le refus « %s » du backend', async (message) => {
    service.ajouter.mockReturnValue(refus(422, { message, termine: false }));
    const { page, cliquer, saisir, envoyer } = await afficher();
    await cliquer('Ajouter un bénéficiaire');
    saisir('benef-nom', 'Test');
    await envoyer('form.ajout');
    expect(page.querySelector('[role=alert]')?.textContent).toBe(message);
    expect(page.querySelector('form.code')).toBeNull();
  });

  it('critères 9 et 10 : un code incorrect reste sur la saisie du code ; un refus définitif y met fin', async () => {
    const { page, cliquer, saisir, envoyer } = await afficher();
    await cliquer('Ajouter un bénéficiaire');
    saisir('benef-nom', 'Test');
    await envoyer('form.ajout');

    service.confirmer.mockReturnValue(refus(400, { message: 'Code incorrect', termine: false }));
    saisir('code-sms', '000000');
    await envoyer('form.code');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Code incorrect');
    expect(page.querySelector('form.code')).not.toBeNull();

    service.confirmer.mockReturnValue(refus(410, { message: 'Code expiré', termine: true }));
    await envoyer('form.code');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Code expiré');
    expect(page.querySelector('form.code')).toBeNull();
  });

  it('annule un ajout en cours ou le formulaire', async () => {
    const { page, cliquer, saisir, envoyer } = await afficher();
    await cliquer('Ajouter un bénéficiaire');
    await cliquer('Annuler');
    expect(page.querySelector('form.ajout')).toBeNull();
    await cliquer('Ajouter un bénéficiaire');
    saisir('benef-nom', 'Test');
    await envoyer('form.ajout');
    await cliquer('Annuler');
    expect(page.querySelector('form.code')).toBeNull();
    expect(page.querySelector('form.ajout')).toBeNull();
  });

  it('critère 13 : modifie le libellé', async () => {
    const { page, cliquer, saisir, envoyer } = await afficher();
    await cliquer('Modifier le libellé');
    expect((page.querySelector('#libelle-5') as HTMLInputElement).value).toBe('Papa');
    saisir('libelle-5', 'Maman');
    await envoyer('form.renommer');
    expect(service.renommer).toHaveBeenCalledWith(5, 'Maman');
    expect(page.querySelector('[role=status]')?.textContent).toBe('Libellé modifié.');

    service.renommer.mockReturnValue(refus(400, { message: 'Libellé trop long' }));
    await cliquer('Modifier le libellé');
    await envoyer('form.renommer');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Libellé trop long');
    await cliquer('Annuler');
    expect(page.querySelector('form.renommer')).toBeNull();
  });

  it('critères 15 et 16 : supprime après confirmation, et affiche « Bénéficiaire introuvable »', async () => {
    const { page, cliquer } = await afficher();
    await cliquer('Supprimer');
    await cliquer('Non');
    expect(service.supprimer).not.toHaveBeenCalled();
    await cliquer('Supprimer');
    await cliquer('Oui, supprimer');
    expect(service.supprimer).toHaveBeenCalledWith(5);
    expect(page.querySelector('[role=status]')?.textContent).toBe('Bénéficiaire « Papa » supprimé.');

    service.supprimer.mockReturnValue(refus(404, { message: 'Bénéficiaire introuvable' }));
    await cliquer('Supprimer');
    await cliquer('Oui, supprimer');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Bénéficiaire introuvable');
  });

  it('affiche un message compréhensible si le backend ne répond pas, et rien sur une session fermée', async () => {
    service.ajouter.mockReturnValue(refus(0, null));
    const { page, cliquer, saisir, envoyer } = await afficher();
    await cliquer('Ajouter un bénéficiaire');
    saisir('benef-nom', 'Test');
    await envoyer('form.ajout');
    expect(page.querySelector('[role=alert]')?.textContent).toBe(
      'Le service est momentanément indisponible. Veuillez réessayer plus tard.',
    );

    service.ajouter.mockReturnValue(refus(401, null));
    await envoyer('form.ajout');
    expect(page.querySelector('[role=alert]')).toBeNull();
  });
});
