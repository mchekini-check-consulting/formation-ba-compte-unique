import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { throwError } from 'rxjs';
import { PAGES_PUBLIQUES, routes } from './app.routes';
import { ConnexionService } from './connexion';
import { espaceProtege } from './session/garde-session';

describe('Routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it("affiche la landing page à l'accueil", async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain(
      'Tout votre argent',
    );
  });

  it.each([
    ['/mentions-legales', 'Mentions légales'],
    ['/protection-des-donnees', 'Politique de protection des données'],
    ['/accessibilite', 'Accessibilité'],
  ])('affiche la page %s', async (adresse, titre) => {
    const harness = await RouterTestingHarness.create(adresse);
    const ecran = harness.routeNativeElement!;
    expect(ecran.querySelector('h1')?.textContent).toBe(titre);
    expect(ecran.querySelector('a')?.getAttribute('href')).toBe('/');
  });

  it('CU-27 : la page Livret A est publique', async () => {
    const route = PAGES_PUBLIQUES.find((r) => r.path === 'epargne/livret-a');
    expect(route?.canActivate).toBeUndefined();
    const harness = await RouterTestingHarness.create('/epargne/livret-a');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Livret A');
  });
});

// CU-170 : refus par défaut. Toute page absente de PAGES_PUBLIQUES exige une session ; une page
// ajoutée sans protection, ou rendue publique par erreur, fait échouer ce test.
describe('Routes protégées par défaut', () => {
  const publiques = new Set(PAGES_PUBLIQUES.map((route) => route.path));
  // Lot 2 : les pages de l'espace sont les enfants du gabarit unique ; chacune reste protégée.
  const pages = routes.flatMap((route) => route.children ?? [route]);
  const protegees = pages.filter((route) => !publiques.has(route.path) && route.path !== '**');

  it('chaque page non publique exige une session', () => {
    expect(protegees.map((route) => route.path)).toEqual(expect.arrayContaining(['espace', 'comptes', 'comptes/:id']));
    for (const route of protegees) {
      expect(route.canActivate, `/${route.path}`).toContain(espaceProtege);
    }
  });

  it("une adresse inconnue mène vers une page protégée", () => {
    const inconnue = routes.find((route) => route.path === '**');
    const cible = pages.find((route) => route.path === inconnue?.redirectTo);
    expect(cible?.canActivate).toContain(espaceProtege);
  });

  it('le gabarit des pages de l’espace exige lui aussi une session', () => {
    expect(routes.find((route) => route.children)?.canActivate).toContain(espaceProtege);
  });

  it("la page /items n'existe plus", () => {
    expect(pages.some((route) => route.path?.startsWith('items'))).toBe(false);
  });

  describe('sans session', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter(routes),
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: ConnexionService,
            useValue: {
              session: () => throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' })),
            },
          },
        ],
      });
    });

    it.each(['/espace', '/comptes', '/comptes/5', '/items', '/une-adresse-inconnue'])(
      '%s renvoie vers la connexion',
      async (adresse) => {
        const harness = await RouterTestingHarness.create();
        await harness.navigateByUrl(adresse);
        // CU-27 : la page demandée est transmise à la connexion (paramètre « suite »).
        expect(TestBed.inject(Router).url.split('?')[0]).toBe('/connexion');
      },
    );
  });
});
