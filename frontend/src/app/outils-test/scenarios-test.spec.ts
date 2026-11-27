import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CAS_DE_TEST } from '../identification/etape-identite';
import { SCENARIOS_PIECE } from '../identification/etape-piece';
import { choisirScenario, scenarioChoisi } from './scenarios';
import { GROUPES, ScenariosTest } from './scenarios-test';

describe('scenarioChoisi (D4)', () => {
  beforeEach(() => localStorage.clear());

  it('sans choix, ou avec un code qui n’existe plus : le cas nominal (premier de la liste)', () => {
    expect(scenarioChoisi('ouverture.identite', CAS_DE_TEST)).toBe('CONFORME');
    choisirScenario('ouverture.identite', 'INCONNU');
    expect(scenarioChoisi('ouverture.identite', CAS_DE_TEST)).toBe('CONFORME');
  });

  it('le choix est mémorisé par bouchon', () => {
    choisirScenario('ouverture.identite', 'PEP');
    expect(scenarioChoisi('ouverture.identite', CAS_DE_TEST)).toBe('PEP');
    expect(scenarioChoisi('ouverture.piece', SCENARIOS_PIECE)).toBe(SCENARIOS_PIECE[0].code);
  });

  it('stockage indisponible : cas nominal, sans erreur', () => {
    const lire = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqué');
    });
    const ecrire = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqué');
    });
    expect(() => choisirScenario('ouverture.identite', 'PEP')).not.toThrow();
    expect(scenarioChoisi('ouverture.identite', CAS_DE_TEST)).toBe('CONFORME');
    lire.mockRestore();
    ecrire.mockRestore();
  });
});

describe('ScenariosTest (/test/scenarios)', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [ScenariosTest], providers: [provideRouter([])] });
  });

  async function afficher() {
    const fixture = TestBed.createComponent(ScenariosTest);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('propose chaque bouchon des parcours, le cas nominal coché, et les codes Docusign', async () => {
    const page = await afficher();
    for (const groupe of GROUPES.flatMap((p) => p.groupes)) {
      const bloc = page.querySelector(`[data-test="${groupe.cle}"]`)!;
      expect(bloc.querySelectorAll('input[type=radio]')).toHaveLength(groupe.liste.length);
      expect(bloc.querySelector<HTMLInputElement>('input:checked')?.value).toBe(groupe.liste[0].code);
    }
    expect(page.querySelector('[data-test="docusign"]')?.textContent).toContain('111111');
  });

  it('un choix est mémorisé pour le prochain envoi de l’étape', async () => {
    const page = await afficher();
    page.querySelector<HTMLInputElement>('[data-test="livret-a.fiscal"] input[value=DETENU_AILLEURS]')!.click();
    expect(localStorage.getItem('scenario-test.livret-a.fiscal')).toBe('DETENU_AILLEURS');
    const relue = await afficher();
    expect(relue.querySelector<HTMLInputElement>('[data-test="livret-a.fiscal"] input:checked')?.value).toBe('DETENU_AILLEURS');
  });
});
