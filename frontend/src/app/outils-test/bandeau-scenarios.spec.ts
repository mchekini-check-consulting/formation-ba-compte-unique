import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BandeauScenarios } from './bandeau-scenarios';
import { choisirScenario, revenirAuCasNominal } from './scenarios';

// PO 28/11/2026 : bandeau des scénarios de test actifs, avec « Revenir au cas nominal ».
describe('BandeauScenarios', () => {
  beforeEach(() => revenirAuCasNominal());
  afterEach(() => revenirAuCasNominal());

  async function bandeau() {
    TestBed.configureTestingModule({ imports: [BandeauScenarios], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(BandeauScenarios);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('sans choix : cas nominal partout, pas de bouton', async () => {
    const { page } = await bandeau();
    expect(page.textContent).toContain('cas nominal');
    expect(page.querySelector('[data-test="cas-nominal"]')).toBeNull();
  });

  it('un bouchon simulé : affiché ; « Revenir au cas nominal » remet tous les bouchons au premier cas', async () => {
    choisirScenario('livret-a.fiscal', 'DETENU_AILLEURS');
    const { fixture, page } = await bandeau();
    expect(page.querySelector('li')?.textContent).toContain('Ouverture d’un Livret A');
    page.querySelector<HTMLButtonElement>('[data-test="cas-nominal"]')!.click();
    await fixture.whenStable();
    expect(page.querySelector('li')).toBeNull();
    expect(page.textContent).toContain('cas nominal');
  });
});
