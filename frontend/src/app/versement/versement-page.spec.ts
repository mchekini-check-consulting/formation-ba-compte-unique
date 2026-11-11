import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, Component, input, output } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { sessionFermee } from '../session/session-fermee';
import { FormulaireVirement } from './formulaire-virement';
import { RubriqueVersements } from './rubrique-versements';
import { VersementPage } from './versement-page';

const ACTIVITE = '/api/backend/connexion/session/activite';
const COMPTES = '/api/backend/comptes';
const COMPTE = {
  id: 5,
  ibanMasque: 'FR76 •••• •••• 0105',
  statut: 'ACTIF',
  soldeCentimes: 0,
  soldeMisAJourLe: '2026-11-05T15:10:00Z',
  premierVersementAttendu: true,
};

// La rubrique Versements est testée dans rubrique-versements.spec.ts.
@Component({
  selector: 'app-rubrique-versements',
  template: '<span class="compte-id">{{ compteId() }}</span><button class="credite" (click)="credite.emit()"></button>',
})
class RubriqueVersementsSimulee {
  readonly compteId = input.required<string>();
  readonly actualisation = input(0);
  readonly credite = output<void>();
}

// Le formulaire de virement est testé dans formulaire-virement.spec.ts.
@Component({
  selector: 'app-formulaire-virement',
  template: '<button class="execute" (click)="execute.emit()"></button>',
})
class FormulaireVirementSimule {
  readonly execute = output<void>();
}

describe('VersementPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.overrideComponent(VersementPage, {
      remove: { imports: [RubriqueVersements, FormulaireVirement] },
      add: { imports: [RubriqueVersementsSimulee, FormulaireVirementSimule] },
    });
    await TestBed.configureTestingModule({
      imports: [VersementPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([sessionFermee])), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir(reponse: unknown, options?: { status: number; statusText: string }) {
    const fixture = TestBed.createComponent(VersementPage);
    http.expectOne({ method: 'POST', url: ACTIVITE }).flush({ identifiant: 'ABC', expireDansSecondes: 600 });
    const comptes = http.expectOne(COMPTES);
    expect(comptes.request.withCredentials).toBe(true);
    comptes.flush(reponse as never, options);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche le compte courant et la rubrique Versements de ce compte, dans le menu de l’espace', async () => {
    const page = await ouvrir([COMPTE]);
    expect(page.querySelector('h1')?.textContent).toBe('Faire un virement');
    expect(page.querySelector('.hello')?.textContent).toContain('ABC');
    expect(page.querySelector('aside a[aria-current="page"]')?.textContent).toContain('Faire un virement');
    expect(page.querySelector('a[href="/espace"]')).not.toBeNull();
    expect(page.querySelector('app-carte-compte')).not.toBeNull();
    expect(page.querySelector('.compte-id')?.textContent).toBe('5');
  });

  it('relit le compte après un virement crédité pour afficher le nouveau solde', async () => {
    const page = await ouvrir([COMPTE]);
    (page.querySelector('.credite') as HTMLButtonElement).click();
    http.expectOne(COMPTES).flush([{ ...COMPTE, soldeCentimes: 1250, premierVersementAttendu: false }]);
    await TestBed.inject(ApplicationRef).whenStable();
    expect(page.querySelector('.solde')?.textContent).toContain('12,50');
  });

  it('CU-147 : après un virement exécuté, relit le solde', async () => {
    const page = await ouvrir([{ ...COMPTE, soldeCentimes: 10000, premierVersementAttendu: false }]);
    expect(page.querySelector('app-formulaire-virement')).not.toBeNull();
    (page.querySelector('.execute') as HTMLButtonElement).click();
    http.expectOne(COMPTES).flush([{ ...COMPTE, soldeCentimes: 7000, premierVersementAttendu: false }]);
    await TestBed.inject(ApplicationRef).whenStable();
    expect(page.querySelector('.solde')?.textContent).toContain('70,00');
  });

  it('invite à ouvrir un compte quand la cliente n’en a pas', async () => {
    const page = await ouvrir([]);
    expect(page.querySelector('.noacct')?.textContent).toContain('Ouvrez votre compte courant');
    expect(page.querySelector('app-rubrique-versements')).toBeNull();
  });

  it('affiche un message compréhensible si le compte ne peut pas être lu', async () => {
    const page = await ouvrir('', { status: 500, statusText: 'Server Error' });
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('renvoie vers la connexion si la cliente n’est pas connectée', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const page = await ouvrir({ motif: 'EXPIREE' }, { status: 401, statusText: 'Unauthorized' });
    expect(navigation).toHaveBeenCalledWith(['/connexion'], { queryParams: { motif: 'session_expiree' } });
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('« Me déconnecter » ferme la session', async () => {
    const page = await ouvrir([COMPTE]);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (page.querySelector('.deconnexion') as HTMLButtonElement).click();
    http.expectOne('/api/backend/connexion/deconnexion').flush(null);
  });
});
