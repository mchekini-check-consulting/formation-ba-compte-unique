import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { DetailCompte } from './detail-compte';

describe('DetailCompte', () => {
  let http: HttpTestingController;

  async function preparer(id: string | null) {
    await TestBed.configureTestingModule({
      imports: [DetailCompte],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id === null ? {} : { id }) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  async function afficher(url: string, reponse: unknown, options?: { status: number; statusText: string }) {
    const fixture = TestBed.createComponent(DetailCompte);
    const requete = http.expectOne(url);
    expect(requete.request.withCredentials).toBe(true);
    requete.flush(reponse as never, options);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche le compte de la cliente', async () => {
    await preparer('5');
    const page = await afficher('/api/backend/comptes/5', {
      id: 5,
      ibanMasque: 'FR76 •••• •••• 0105',
      statut: 'ACTIF',
      soldeCentimes: 124050,
      soldeMisAJourLe: '2026-11-05T15:10:00Z',
      premierVersementAttendu: false,
    });
    expect(page.textContent).toContain('FR76 •••• •••• 0105');
    expect(page.querySelector('.solde')?.textContent).toBe('1 240,50 €');
  });

  it('affiche le message exact et aucune information pour un compte refusé', async () => {
    await preparer('6');
    const page = await afficher('/api/backend/comptes/6', { message: 'refus' }, { status: 404, statusText: 'Not Found' });
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Ce compte n’est pas accessible');
    expect(page.textContent).not.toContain('IBAN');
    expect(page.querySelector('.solde')).toBeNull();
    expect(page.textContent).not.toContain('€');
  });

  it("protège l'identifiant saisi dans l'adresse appelée", async () => {
    await preparer('a/b');
    const page = await afficher('/api/backend/comptes/a%2Fb', '', { status: 400, statusText: 'Bad Request' });
    expect(page.textContent).toContain('Ce compte n’est pas accessible');
  });

  it("traite une adresse sans identifiant comme un compte inaccessible", async () => {
    await preparer(null);
    const page = await afficher('/api/backend/comptes/', '', { status: 404, statusText: 'Not Found' });
    expect(page.textContent).toContain('Ce compte n’est pas accessible');
  });

  it('renvoie vers la connexion si la cliente n’est pas connectée', async () => {
    await preparer('5');
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const page = await afficher('/api/backend/comptes/5', '', { status: 401, statusText: 'Unauthorized' });
    expect(navigation).toHaveBeenCalledWith('/connexion');
    expect(page.textContent).not.toContain('Ce compte n’est pas accessible');
  });
});
