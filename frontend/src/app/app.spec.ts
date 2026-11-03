import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

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

  it("garde l'écran Items sur /items", async () => {
    const harness = await RouterTestingHarness.create('/items');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Items');
  });
});
