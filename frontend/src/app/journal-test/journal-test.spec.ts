import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { JournalTest, LigneJournal } from './journal-test';

const ECHEC: LigneJournal = {
  id: 12,
  survenuLe: '2026-11-06T10:00:00Z',
  evenement: 'CONNEXION_ECHOUEE',
  resultat: 'ECHEC',
  motif: 'MOT_DE_PASSE_INCORRECT',
  identifiantSaisi: '12345678',
  identifiantConnexionId: 7,
  adresseIp: '203.0.113.7',
  compteDemande: null,
};

function creer() {
  TestBed.configureTestingModule({
    imports: [JournalTest],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return TestBed.createComponent(JournalTest);
}

describe('JournalTest (CU-167)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche les derniers événements du journal', async () => {
    const fixture = creer();
    TestBed.inject(HttpTestingController).expectOne('/api/backend/test/journal-securite').flush([ECHEC]);
    await fixture.whenStable();
    const ligne = (fixture.nativeElement as HTMLElement).querySelector('tbody tr')!;
    expect(ligne.textContent).toContain('CONNEXION_ECHOUEE');
    expect(ligne.textContent).toContain('MOT_DE_PASSE_INCORRECT');
    expect(ligne.textContent).toContain('203.0.113.7');
  });

  it("filtre sur l'identifiant saisi", async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/backend/test/journal-securite').flush([]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Aucun événement.');

    page.querySelector<HTMLInputElement>('#identifiant')!.value = ' 12345678 ';
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    http.expectOne('/api/backend/test/journal-securite?identifiant=12345678').flush([ECHEC]);
    await fixture.whenStable();
    expect(page.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('hors environnement de test (404), indique que le journal est non consultable', async () => {
    const fixture = creer();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/backend/test/journal-securite')
      .flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Journal non consultable');
  });
});
