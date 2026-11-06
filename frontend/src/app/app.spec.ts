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

});

// CU-170 : refus par défaut. Toute page absente de PAGES_PUBLIQUES exige une session ; une page
// ajoutée sans protection, ou rendue publique par erreur, fait échouer ce test.
describe('Routes protégées par défaut', () => {
  const publiques = new Set(PAGES_PUBLIQUES.map((route) => route.path));
  const protegees = routes.filter((route) => !publiques.has(route.path) && route.path !== '**');

  it('chaque page non publique exige une session', () => {
    expect(protegees.map((route) => route.path)).toEqual(expect.arrayContaining(['espace', 'comptes', 'comptes/:id']));
    for (const route of protegees) {
      expect(route.canActivate, `/${route.path}`).toContain(espaceProtege);
    }
  });

  it("une adresse inconnue mène vers une page protégée", () => {
    const inconnue = routes.find((route) => route.path === '**');
    const cible = routes.find((route) => route.path === inconnue?.redirectTo);
    expect(cible?.canActivate).toContain(espaceProtege);
  });

  it("la page /items n'existe plus", () => {
    expect(routes.some((route) => route.path?.startsWith('items'))).toBe(false);
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
        expect(TestBed.inject(Router).url).toBe('/connexion');
      },
    );
  });
});
