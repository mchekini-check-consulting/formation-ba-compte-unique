import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OrdreTest, PositionTest } from './ordres';
import { OrdresTest } from './ordres-test';

const URL = '/api/backend/test/ordres';
const ORDRE: OrdreTest = {
  id: '11111111-1111-4111-8111-111111111111',
  numeroContrat: 'AV-TEST-0001',
  type: 'RACHAT',
  supportDepart: 'QZ0000000017',
  supportArrivee: null,
  montant: 1000,
  demandeLe: '2026-12-01T11:59:00+01:00',
  dateExecutionPrevue: '2026-12-01',
  statut: 'DEMANDE',
  montantDisponible: 2000,
  evenement: { id: 'e1', statut: 'EN_ATTENTE', publieLe: null, derniereErreur: 'Kafka indisponible : TimeoutException' },
};
const POSITION: PositionTest = {
  numeroContrat: 'AV-TEST-0001',
  codeIsin: 'QZ0000000017',
  montant: 3000,
  nombreParts: null,
  enregistreeLe: '2026-12-01T10:00:00+01:00',
};

function creer() {
  TestBed.configureTestingModule({
    imports: [OrdresTest],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return TestBed.createComponent(OrdresTest);
}

function repondreChargement(http: HttpTestingController, ordres: OrdreTest[], positions: PositionTest[]) {
  http.expectOne((r) => r.method === 'GET' && r.url === URL).flush(ordres);
  http.expectOne(`${URL}/positions`).flush(positions);
}

async function pagePrete(ordres: OrdreTest[] = [ORDRE], positions: PositionTest[] = [POSITION]) {
  const fixture = creer();
  const http = TestBed.inject(HttpTestingController);
  repondreChargement(http, ordres, positions);
  await fixture.whenStable();
  return { fixture, http, page: fixture.nativeElement as HTMLElement };
}

function saisir(page: HTMLElement, id: string, valeur: string) {
  const champ = page.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
  champ.value = valeur;
  champ.dispatchEvent(new Event('change'));
}

function soumettre(page: HTMLElement, formulaire: string) {
  page.querySelector<HTMLFormElement>(`[data-test="${formulaire}"]`)!.dispatchEvent(new Event('submit'));
}

const envoiOrdre = (r: { method: string; url: string }) => r.method === 'POST' && r.url === URL;

describe('OrdresTest (CU-376)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche les ordres avec statut, date d’exécution prévue, montant disponible et événement, et les positions', async () => {
    const { page } = await pagePrete();

    const ligne = page.querySelector('[data-test="ordres"] tbody tr')!.textContent!;
    expect(ligne).toContain('AV-TEST-0001');
    expect(ligne).toContain('Rachat');
    expect(ligne).toContain('Demandé');
    expect(ligne).toContain('01/12/2026');
    expect(ligne).toContain('2 000,00');
    expect(ligne).toContain('En attente');
    expect(ligne).toContain('Kafka indisponible');
    expect(page.querySelector('[data-test="positions"] tbody tr')!.textContent).toContain('3 000,00');
  });

  it('passe un ordre avec un identifiant créé à la validation, puis le renvoie à l’identique sans doublon', async () => {
    const { fixture, http, page } = await pagePrete([], []);
    saisir(page, 'type', 'RACHAT');
    saisir(page, 'contrat', ' AV-TEST-0001 ');
    saisir(page, 'depart', 'qz0000000017');
    saisir(page, 'montant', '1000');

    soumettre(page, 'ordre');
    const envoi = http.expectOne(envoiOrdre);
    const corps = envoi.request.body;
    expect(corps).toEqual(
      expect.objectContaining({
        numeroContrat: 'AV-TEST-0001',
        type: 'RACHAT',
        supportDepart: 'QZ0000000017',
        supportArrivee: null,
        montant: 1000,
      }),
    );
    expect(corps.id).toMatch(/^[0-9a-f-]{36}$/);
    envoi.flush(ORDRE, { status: 201, statusText: 'Created' });
    repondreChargement(http, [ORDRE], []);
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')!.textContent).toContain('Ordre enregistré');

    page.querySelector<HTMLButtonElement>('[data-test="renvoyer"]')!.click();
    const renvoi = http.expectOne(envoiOrdre);
    expect(renvoi.request.body.id).toBe(corps.id);
    renvoi.flush(ORDRE, { status: 200, statusText: 'OK' });
    repondreChargement(http, [ORDRE], []);
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')!.textContent).toContain('Ordre déjà enregistré : aucun doublon');
  });

  it('affiche le refus avec le montant disponible quand il est insuffisant', async () => {
    const { fixture, http, page } = await pagePrete([], []);
    saisir(page, 'type', 'RACHAT');
    saisir(page, 'contrat', 'AV-TEST-0001');
    saisir(page, 'depart', 'QZ0000000017');
    saisir(page, 'montant', '2500');

    soumettre(page, 'ordre');
    http.expectOne(envoiOrdre).flush(
      { message: 'Montant disponible insuffisant : 2 000,00 € disponibles.', disponible: 2000 },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')!.textContent).toContain('2 000,00 € disponibles');
  });

  it('envoie les supports selon le type : versement → arrivée seule, arbitrage → les deux', async () => {
    const { fixture, http, page } = await pagePrete([], []);
    saisir(page, 'contrat', 'PER-TEST-0001');
    saisir(page, 'depart', 'QZ0000000017');
    saisir(page, 'type', 'VERSEMENT');
    await fixture.whenStable();
    expect(page.querySelector('#depart')).toBeNull();
    saisir(page, 'arrivee', 'US0378331005');
    saisir(page, 'montant', '50,5');
    soumettre(page, 'ordre');
    const versement = http.expectOne(envoiOrdre);
    expect(versement.request.body).toEqual(
      expect.objectContaining({ supportDepart: null, supportArrivee: 'US0378331005', montant: 50.5 }),
    );
    versement.flush({ message: 'Support inconnu : US0378331005.' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Support inconnu');

    saisir(page, 'type', 'ARBITRAGE');
    soumettre(page, 'ordre');
    const arbitrage = http.expectOne(envoiOrdre);
    expect(arbitrage.request.body).toEqual(
      expect.objectContaining({ supportDepart: 'QZ0000000017', supportArrivee: 'US0378331005' }),
    );
    arbitrage.error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('L’ordre n’a pas pu être enregistré');
  });

  it('refuse un montant illisible sans appeler le backend', async () => {
    const { fixture, http, page } = await pagePrete([], []);
    saisir(page, 'contrat', 'AV-TEST-0001');
    saisir(page, 'montant', 'mille');
    soumettre(page, 'ordre');
    http.expectNone(envoiOrdre);
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Montant invalide');
  });

  it('enregistre une position de test de 3 000 € et affiche le montant disponible', async () => {
    const { fixture, http, page } = await pagePrete([], []);
    saisir(page, 'position-contrat', 'AV-TEST-0001');
    saisir(page, 'position-isin', 'qz0000000017');
    saisir(page, 'position-valeur', '3000');

    soumettre(page, 'position');
    const envoi = http.expectOne(`${URL}/positions`);
    expect(envoi.request.body).toEqual({
      numeroContrat: 'AV-TEST-0001',
      codeIsin: 'QZ0000000017',
      montant: 3000,
      nombreParts: null,
    });
    envoi.flush({ numeroContrat: 'AV-TEST-0001', codeIsin: 'QZ0000000017', disponible: 3000 });
    repondreChargement(http, [], [POSITION]);
    await fixture.whenStable();

    expect(page.querySelector('[role="status"]')!.textContent).toContain(
      'Montant disponible sur QZ0000000017 : 3 000,00 €',
    );
  });

  it('enregistre une position en nombre de parts, et affiche un refus du backend', async () => {
    const { fixture, http, page } = await pagePrete([], []);
    saisir(page, 'position-contrat', 'AV-TEST-0001');
    saisir(page, 'position-isin', 'QZ0000000017');
    saisir(page, 'position-unite', 'parts');
    saisir(page, 'position-valeur', '4');
    soumettre(page, 'position');
    const envoi = http.expectOne(`${URL}/positions`);
    expect(envoi.request.body).toEqual(expect.objectContaining({ montant: null, nombreParts: 4 }));
    envoi.flush({ message: 'Le code ISIN est obligatoire.' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Le code ISIN est obligatoire.');
  });

  it('vide les ordres et positions de test après confirmation, ou annule', async () => {
    const { fixture, http, page } = await pagePrete();

    page.querySelector<HTMLButtonElement>('[data-test="vider"]')!.click();
    await fixture.whenStable();
    page.querySelector<HTMLButtonElement>('[data-test="annuler-vider"]')!.click();
    await fixture.whenStable();
    http.expectNone((r) => r.method === 'DELETE');

    page.querySelector<HTMLButtonElement>('[data-test="vider"]')!.click();
    await fixture.whenStable();
    page.querySelector<HTMLButtonElement>('[data-test="confirmer-vider"]')!.click();
    const vidage = http.expectOne((r) => r.method === 'DELETE' && r.url === URL);
    vidage.flush(null, { status: 204, statusText: 'No Content' });
    repondreChargement(http, [], []);
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')!.textContent).toContain('Ordres et positions de test effacés');
  });

  it('indique que la page est indisponible en preprod et prod (404)', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(URL).flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne(`${URL}/positions`);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Page de test indisponible');
  });
});
