import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ChangementDateMetier, EtatDateMetier, afficherDate } from './date-metier';
import { DateMetierTest } from './date-metier-test';

const URL = '/api/backend/test/date-metier';
const REELLE: EtatDateMetier = { dateMetier: '2026-11-24T10:00:00+01:00', simulee: false, modifiable: true };
const SIMULEE: EtatDateMetier = { dateMetier: '2026-12-31T11:00:00+01:00', simulee: true, modifiable: true };
const TRACE: ChangementDateMetier = {
  ancienneDate: '2026-11-24T10:00:00+01:00',
  nouvelleDate: '2026-12-31T11:00:00+01:00',
  auteur: 'Alice',
  changeLe: '2026-11-24T10:00:00+01:00',
};

function creer() {
  TestBed.configureTestingModule({
    imports: [DateMetierTest],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return TestBed.createComponent(DateMetierTest);
}

function saisir(page: HTMLElement, date: string, auteur: string) {
  page.querySelector<HTMLInputElement>('#nouvelle-date')!.value = date;
  page.querySelector<HTMLInputElement>('#auteur')!.value = auteur;
}

describe('DateMetierTest (CU-380)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche la date métier et le journal des changements', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(SIMULEE);
    http.expectOne(`${URL}/journal`).flush([TRACE]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[data-test="date-metier"]')!.textContent).toContain('31 décembre 2026');
    expect(page.textContent).toContain('Date simulée');
    expect(page.querySelector('tbody tr')!.textContent).toContain('Alice');
  });

  it('Étant donné l’environnement de test, quand la testeuse avance la date au 31/12, alors la nouvelle date est envoyée avec son nom puis affichée', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(REELLE);
    http.expectOne(`${URL}/journal`).flush([]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    saisir(page, '2026-12-31T11:00', 'Alice');
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    const changement = http.expectOne(URL);
    expect(changement.request.method).toBe('PUT');
    expect(changement.request.body).toEqual({ date: '2026-12-31T11:00', auteur: 'Alice' });
    changement.flush(SIMULEE);
    http.expectOne(URL).flush(SIMULEE);
    http.expectOne(`${URL}/journal`).flush([TRACE]);
    await fixture.whenStable();
    expect(page.textContent).toContain('Date simulée');
  });

  it('revient à la date réelle', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(SIMULEE);
    http.expectOne(`${URL}/journal`).flush([TRACE]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLInputElement>('#auteur')!.value = 'Bruno';
    page.querySelector<HTMLButtonElement>('button[type="button"]')!.click();
    const retour = http.expectOne(`${URL}/date-reelle`);
    expect(retour.request.method).toBe('POST');
    expect(retour.request.body).toEqual({ auteur: 'Bruno' });
    retour.flush(REELLE);
    http.expectOne(URL).flush(REELLE);
    http.expectOne(`${URL}/journal`).flush([]);
    await fixture.whenStable();
    expect(page.textContent).toContain('Date réelle');
  });

  it('affiche le motif quand le backend refuse le changement (nom manquant)', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(REELLE);
    http.expectOne(`${URL}/journal`).flush([]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    saisir(page, '2026-12-31T11:00', '');
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    http
      .expectOne(URL)
      .flush({ message: 'Indiquez votre nom : chaque changement de date est tracé.' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Indiquez votre nom');
  });

  it('demande une date avant d’appeler le backend', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(REELLE);
    http.expectOne(`${URL}/journal`).flush([]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    saisir(page, '', 'Alice');
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Choisissez la nouvelle date');
  });

  it('Étant donné l’environnement de production, alors la date est affichée sans formulaire de changement', async () => {
    const fixture = creer();
    TestBed.inject(HttpTestingController).expectOne(URL).flush({ ...REELLE, modifiable: false });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('form')).toBeNull();
    expect(page.textContent).toContain('ne peut pas être modifiée');
  });

  it('indique quand la date métier est indisponible', async () => {
    const fixture = creer();
    TestBed.inject(HttpTestingController).expectOne(URL).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('indisponible');
  });
});

describe('afficherDate (CU-380)', () => {
  it('affiche les dates à l’heure de Paris', () => {
    expect(afficherDate('2026-12-31T10:00:00Z')).toContain('31 décembre 2026');
    expect(afficherDate('2026-12-31T10:00:00Z')).toContain('11:00');
  });
});

describe('DateMetierTest (CU-415 : tâche de revalidation des profils)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lance la tâche à la main et affiche le nombre de profils passés « à revalider », ou l’échec', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(SIMULEE);
    http.expectOne(`${URL}/journal`).flush([]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('[data-test="revalidation"]')!.click();
    const lancement = http.expectOne('/api/backend/test/profils/revalidation');
    expect(lancement.request.method).toBe('POST');
    lancement.flush({ profilsARevalider: 1 });
    await fixture.whenStable();
    expect(page.querySelector('[data-test="resultat-revalidation"]')!.textContent).toContain(
      'Tâche lancée : 1 profil(s) passé(s) « à revalider ».',
    );

    page.querySelector<HTMLButtonElement>('[data-test="revalidation"]')!.click();
    http.expectOne('/api/backend/test/profils/revalidation').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('La tâche n’a pas pu être lancée');
  });
});
