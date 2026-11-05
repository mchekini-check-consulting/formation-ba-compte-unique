import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Espace } from './espace';

const SESSION = '/api/backend/connexion/session';
const COMPTES = '/api/backend/comptes';
const COMPTE = {
  id: 1,
  ibanMasque: 'FR76 •••• •••• 0105',
  statut: 'ACTIF',
  soldeCentimes: 124050,
  soldeMisAJourLe: '2026-11-05T15:10:00Z',
  premierVersementAttendu: false,
};

describe('Espace', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Espace],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir(reponse: unknown, options?: { status: number; statusText: string }) {
    const fixture = TestBed.createComponent(Espace);
    const session = http.expectOne(SESSION);
    expect(session.request.withCredentials).toBe(true);
    session.flush({ identifiant: 'ABC' });
    const comptes = http.expectOne(COMPTES);
    expect(comptes.request.withCredentials).toBe(true);
    comptes.flush(reponse as never, options);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it("affiche l'espace et le solde du compte quand la session est valide", async () => {
    const page = await ouvrir([COMPTE]);
    expect(page.textContent).toContain('ABC');
    expect(page.querySelector('a[href="/comptes"]')).not.toBeNull();
    expect(page.querySelector('.solde')?.textContent).toBe('1 240,50 €');
    expect(page.querySelector('.noacct')).toBeNull();
  });

  it("invite à ouvrir un compte, sans solde, quand la cliente n'en a pas", async () => {
    const page = await ouvrir([]);
    expect(page.querySelector('.solde')).toBeNull();
    expect(page.querySelector('.noacct')?.textContent).toContain('Ouvrir mon compte');
    expect(page.querySelector('.noacct a')?.getAttribute('href')).toBe('/');
  });

  it('affiche seulement le message si la lecture échoue', async () => {
    const page = await ouvrir('', { status: 500, statusText: 'Erreur' });
    expect(page.querySelector('.solde.err')?.textContent).toBe('Votre solde est momentanément indisponible');
    expect(page.querySelector('.acct')?.textContent).not.toContain('€');
    expect(page.querySelector('.noacct')).toBeNull();
  });

  it('renvoie vers la connexion si la session expire avant la lecture du solde', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await ouvrir('', { status: 401, statusText: 'Unauthorized' });
    expect(navigation).toHaveBeenCalledWith('/connexion');
  });

  it('renvoie vers la connexion sans rien afficher si la session est absente', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Espace);
    http.expectOne(SESSION).flush('', { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(navigation).toHaveBeenCalledWith('/connexion');
    expect((fixture.nativeElement as HTMLElement).querySelector('main')).toBeNull();
    http.expectNone(COMPTES);
  });
});
