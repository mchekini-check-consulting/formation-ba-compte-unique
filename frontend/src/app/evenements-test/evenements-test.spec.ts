import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EtatEvenements } from './evenements';
import { EvenementsTest } from './evenements-test';

const URL = '/api/backend/test/evenements';
const VIDE: EtatEvenements = { aDistribuer: [], recus: [], enErreur: [] };
const REMPLI: EtatEvenements = {
  aDistribuer: [
    {
      id: 'e1',
      type: 'StatutProfilModifie',
      topic: 'cu-epargne.profil-client',
      cle: 'cliente-1',
      dateMetier: '2026-12-31',
      creeLe: '2026-12-31T11:00:00+01:00',
      publieLe: '2026-12-31T11:00:02+01:00',
      essais: 0,
      derniereErreur: null,
    },
    {
      id: 'e2',
      type: 'CatalogueMisAJour',
      topic: 'cu-epargne.catalogue',
      cle: '2026-12-31',
      dateMetier: '2026-12-31',
      creeLe: '2026-12-31T11:00:00+01:00',
      publieLe: null,
      essais: 2,
      derniereErreur: 'Kafka indisponible : ExecutionException',
    },
  ],
  recus: [
    {
      id: 'e1',
      type: 'StatutProfilModifie',
      topic: 'cu-epargne.profil-client',
      cle: 'cliente-1',
      dateMetier: '2026-12-31',
      recuLe: '2026-12-31T11:00:03+01:00',
    },
  ],
  enErreur: [
    {
      topic: 'cu-epargne.profil-client',
      cle: null,
      groupe: 'cu-epargne-journal',
      motif: 'message illisible (JSON attendu)',
      essais: 3,
      enErreurLe: '2026-12-31T11:00:05+01:00',
    },
  ],
};

function creer() {
  TestBed.configureTestingModule({
    imports: [EvenementsTest],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return TestBed.createComponent(EvenementsTest);
}

describe('EvenementsTest (CU-382)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche la boîte d’envoi (publié ou en attente), les événements reçus et les messages en erreur', async () => {
    const fixture = creer();
    TestBed.inject(HttpTestingController).expectOne(URL).flush(REMPLI);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    const boite = page.querySelector('[data-test="a-distribuer"]')!.textContent!;
    expect(boite).toContain('Publié le');
    expect(boite).toContain('En attente (2 essai(s) : Kafka indisponible : ExecutionException)');
    expect(page.querySelector('[data-test="recus"]')!.textContent).toContain('cliente-1');
    const erreurs = page.querySelector('[data-test="en-erreur"]')!.textContent!;
    expect(erreurs).toContain('message illisible (JSON attendu)');
    expect(erreurs).toContain('—');
  });

  it('indique quand il n’y a encore rien, et actualise à la demande', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(VIDE);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Aucun événement enregistré.');
    expect(page.textContent).toContain('Aucun événement reçu.');
    expect(page.textContent).toContain('Aucun message en erreur.');

    page.querySelector<HTMLButtonElement>('button')!.click();
    http.expectOne(URL).flush(REMPLI);
    await fixture.whenStable();
    expect(page.textContent).toContain('cliente-1');
  });

  it('envoie un message invalide et explique la suite', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(VIDE);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button.secondaire')!.click();
    const envoi = http.expectOne(`${URL}/message-invalide`);
    expect(envoi.request.method).toBe('POST');
    envoi.flush(null, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')!.textContent).toContain('après 3 essais');
  });

  it('affiche une erreur compréhensible si l’envoi échoue', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(VIDE);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button.secondaire')!.click();
    http.expectOne(`${URL}/message-invalide`).error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('n’a pas pu être envoyé');
  });

  it('indique que la page est indisponible en preprod et prod (404)', async () => {
    const fixture = creer();
    TestBed.inject(HttpTestingController).expectOne(URL).flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Page de test indisponible');
  });
});
