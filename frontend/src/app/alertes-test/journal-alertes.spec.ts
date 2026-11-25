import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AlerteEquipe, JournalAlertes } from './journal-alertes';

const URL = '/api/backend/test/alertes';
const BATCH: AlerteEquipe = {
  dateMetier: '2026-11-24T02:00:00+01:00',
  origine: 'Batch catalogue',
  message: 'Batch catalogue en échec : fichier absent',
  emailEnvoye: true,
};

function creer() {
  TestBed.configureTestingModule({
    imports: [JournalAlertes],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  return TestBed.createComponent(JournalAlertes);
}

describe('JournalAlertes (CU-382)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('Critère 11 : affiche la ligne « Batch catalogue en échec : fichier absent » avec sa date métier et l’e-mail envoyé', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush([BATCH, { ...BATCH, origine: 'Topic d’erreurs', message: 'Message en erreur', emailEnvoye: false }]);
    await fixture.whenStable();
    const lignes = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-test="alertes"] tbody tr');

    expect(lignes.length).toBe(2);
    expect(lignes[0].textContent).toContain('Batch catalogue en échec : fichier absent');
    expect(lignes[0].textContent).toContain('24 novembre 2026');
    expect(lignes[0].textContent).toContain('Envoyé');
    expect(lignes[1].textContent).toContain('Non envoyé');
  });

  it('actualise le journal à la demande, indique un journal vide ou indisponible', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush([]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Aucune alerte.');

    page.querySelector<HTMLButtonElement>('[data-test="actualiser-alertes"]')!.click();
    http.expectOne(URL).error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(page.textContent).toContain('Journal des alertes indisponible pour le moment.');
  });
});
