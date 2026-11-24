import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FichierDepose, JournalBatch } from './catalogue';
import { CatalogueTest } from './catalogue-test';

const URL = '/api/backend/test/catalogue';
const DATE = '/api/backend/test/date-metier';
const ETAT_DATE = { dateMetier: '2026-11-23T02:00:00+01:00', simulee: true, modifiable: true };
const DEPOT: FichierDepose = {
  nomFichier: 'catalogue_uc_2026-11-23.csv',
  dateFichier: '2026-11-23',
  deposeLe: '2026-11-23T02:00:00+01:00',
};
const TERMINE: JournalBatch = {
  dateMetier: '2026-11-23',
  declenchement: 'MANUEL',
  nomFichier: 'catalogue_uc_2026-11-23.csv',
  debut: '2026-11-23T02:00:00+01:00',
  fin: '2026-11-23T02:00:01+01:00',
  ajoutes: 101,
  modifies: 0,
  retires: 0,
  rejetees: 1,
  statut: 'TERMINE',
  message: null,
  lignesRejetees: [{ numeroLigne: 104, codeIsin: null, motif: 'Code ISIN vide' }],
};
const ECHEC: JournalBatch = {
  ...TERMINE,
  declenchement: 'AUTOMATIQUE',
  nomFichier: 'catalogue_uc_2026-11-24.csv',
  ajoutes: 0,
  rejetees: 0,
  statut: 'ECHEC',
  message: 'Fichier absent : catalogue_uc_2026-11-24.csv',
  lignesRejetees: [],
};

function creer() {
  TestBed.configureTestingModule({
    imports: [CatalogueTest],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return TestBed.createComponent(CatalogueTest);
}

function repondreChargement(http: HttpTestingController, fichiers: FichierDepose[], journal: JournalBatch[]) {
  http.expectOne(DATE).flush(ETAT_DATE);
  http.expectOne(`${URL}/fichiers`).flush(fichiers);
  http.expectOne(`${URL}/journal`).flush(journal);
}

function choisirFichier(page: HTMLElement, nom: string, contenu: string): HTMLInputElement {
  const champ = page.querySelector<HTMLInputElement>('#fichier')!;
  const fichier = { name: nom, text: () => Promise.resolve(contenu) };
  Object.defineProperty(champ, 'files', { value: [fichier], configurable: true });
  champ.dispatchEvent(new Event('change'));
  return champ;
}

describe('CatalogueTest (CU-381)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche la date métier, les dépôts et le journal avec les lignes rejetées et les échecs', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [DEPOT], [ECHEC, TERMINE]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('[data-test="date-metier"]')!.textContent).toContain('lundi 23 novembre 2026');
    expect(page.textContent).toContain('catalogue_uc_2026-11-23.csv');
    expect(page.textContent).toContain('Fichier absent : catalogue_uc_2026-11-24.csv');
    expect(page.querySelector('[data-test="compteurs"]')!.textContent).toContain('101 ajoutés');
    expect(page.querySelector('tbody tr')!.textContent).toContain('Code ISIN vide');
    expect(page.querySelector('tbody tr')!.textContent).toContain('—');
  });

  it('dépose le fichier choisi avec son nom et son contenu', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [], []);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Aucune exécution.');

    choisirFichier(page, 'catalogue_uc_2026-11-23.csv', 'code_isin;nom');
    await new Promise((fin) => setTimeout(fin));
    const depot = http.expectOne(`${URL}/fichiers`);
    expect(depot.request.method).toBe('POST');
    expect(depot.request.body).toEqual({ nomFichier: 'catalogue_uc_2026-11-23.csv', contenu: 'code_isin;nom' });
    depot.flush(DEPOT);
    repondreChargement(http, [DEPOT], []);
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')!.textContent).toContain('Fichier catalogue_uc_2026-11-23.csv déposé.');
  });

  it('affiche le refus du backend pour un fichier mal nommé', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [], []);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    choisirFichier(page, 'catalogue.csv', 'x');
    await new Promise((fin) => setTimeout(fin));
    http
      .expectOne(`${URL}/fichiers`)
      .flush({ message: 'Le fichier doit s’appeler catalogue_uc_AAAA-MM-JJ.csv.' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('catalogue_uc_AAAA-MM-JJ.csv');
  });

  it('ne fait rien si aucun fichier n’est choisi', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [], []);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const champ = page.querySelector<HTMLInputElement>('#fichier')!;
    Object.defineProperty(champ, 'files', { value: [], configurable: true });
    champ.dispatchEvent(new Event('change'));
    http.expectNone(`${URL}/fichiers`);
  });

  it('lance le batch et affiche son résultat', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [DEPOT], []);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button')!.click();
    const lancement = http.expectOne(`${URL}/batch`);
    expect(lancement.request.method).toBe('POST');
    lancement.flush(TERMINE);
    repondreChargement(http, [DEPOT], [TERMINE]);
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')!.textContent).toContain('Batch terminé : 101 ajoutés, 1 lignes rejetées.');
  });

  it('signale un batch en échec (fichier absent)', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [], []);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button')!.click();
    http.expectOne(`${URL}/batch`).flush(ECHEC);
    repondreChargement(http, [], [ECHEC]);
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Batch en échec : Fichier absent');
  });

  it('Étant donné un samedi en date métier, quand la testeuse lance le batch, alors le refus est affiché', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [], []);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button')!.click();
    http
      .expectOne(`${URL}/batch`)
      .flush({ message: 'La date métier n’est pas un jour ouvré : le batch ne s’exécute pas.' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('pas un jour ouvré');
  });

  it('affiche un message générique si le backend ne répond pas', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    repondreChargement(http, [], []);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button')!.click();
    http.expectOne(`${URL}/batch`).error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Le batch n’a pas pu être lancé');

    choisirFichier(page, 'catalogue_uc_2026-11-23.csv', 'x');
    await new Promise((fin) => setTimeout(fin));
    http.expectOne(`${URL}/fichiers`).error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain('Le fichier n’a pas pu être déposé');
  });

  it('indique que la page est indisponible en preprod et prod (404)', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(DATE).flush(ETAT_DATE);
    http.expectOne(`${URL}/fichiers`).flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne(`${URL}/journal`);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Page de test indisponible');
  });
});
