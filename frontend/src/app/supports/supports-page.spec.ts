import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SupportEligible, SupportsDisponibles } from './supports';
import { SupportsPage } from './supports-page';

const URL = '/api/backend/catalogue/supports';

function support(nom: string, categorie: string, niveauRisque: number, autres: Partial<SupportEligible> = {}): SupportEligible {
  return {
    codeIsin: `QZ${nom.length}${niveauRisque}`,
    nom,
    categorie,
    niveauRisque,
    fraisSupportPct: 0.8,
    valeurLiquidative: 102.35,
    dateVl: '2026-11-20',
    nonCote: false,
    label: null,
    ...autres,
  };
}

const SCPI = support('SCPI Bureaux Europe', 'SCPI', 3, {
  fraisSupportPct: 1.6,
  valeurLiquidative: 773.32,
  dateVl: '2026-11-01',
  nonCote: true,
});
const MONETAIRE = support('Monétaire Euro', 'Monétaire euro', 1, { label: 'ISR' });
const ACTIONS = support('Actions Europe', 'Actions Europe', 3);

function reponse(supports: SupportEligible[], autres: Partial<SupportsDisponibles> = {}): SupportsDisponibles {
  return {
    produit: 'AV',
    profilValide: true,
    profilInvestisseur: 'PRUDENT',
    fraisContratPct: 0.65,
    supports,
    message: null,
    ...autres,
  };
}

function creer(produit: string | null = null) {
  TestBed.configureTestingModule({
    imports: [SupportsPage],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(produit ? { produit } : {}) } } },
    ],
  });
  return TestBed.createComponent(SupportsPage);
}

async function pagePrete(donnees: SupportsDisponibles, produit: string | null = null) {
  const fixture = creer(produit);
  const http = TestBed.inject(HttpTestingController);
  const requete = http.expectOne(`${URL}?produit=${donnees.produit}`);
  // CU-417 : sans le cookie de session, le backend répond 401 et l'application revient sur l'espace.
  expect(requete.request.withCredentials).toBe(true);
  requete.flush(donnees);
  await fixture.whenStable();
  return { fixture, http, page: fixture.nativeElement as HTMLElement };
}

function cartes(page: HTMLElement): HTMLElement[] {
  return Array.from(page.querySelectorAll<HTMLElement>('[data-test="support"]'));
}

function choisir(page: HTMLElement, id: string, valeur: string) {
  const champ = page.querySelector<HTMLSelectElement>(`#${id}`)!;
  champ.value = valeur;
  champ.dispatchEvent(new Event('change'));
}

describe('SupportsPage (CU-412)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche les supports dans l’ordre du service, avec leurs caractéristiques et les frais séparés', async () => {
    const { page } = await pagePrete(reponse([MONETAIRE, SCPI]));

    expect(cartes(page).length).toBe(2);
    const scpi = cartes(page)[1].textContent!;
    expect(scpi).toContain('SCPI Bureaux Europe');
    expect(scpi).toContain('SCPI');
    expect(scpi).toContain('Risque 3/7');
    expect(scpi).toContain('Non coté');
    expect(scpi).toContain('773,32 €');
    expect(scpi).toContain('01/11/2026');
    expect(page.querySelector('[data-test="frais-contrat"]')!.textContent).toContain('0,65 % par an');
    expect(cartes(page)[1].querySelector('[data-test="frais-support"]')!.textContent).toContain('1,60 % par an');
    expect(cartes(page)[0].textContent).toContain('ISR');
    expect(cartes(page)[0].textContent).not.toContain('Non coté');
  });

  it('affiche toujours le fonds en euros', async () => {
    const { page } = await pagePrete(reponse([SCPI]));
    expect(page.querySelector('[data-test="fonds-euros"]')!.textContent).toContain('Fonds en euros');
  });

  it('Critère 10 : filtre sur la catégorie « SCPI » : seuls les supports de cette catégorie sont affichés', async () => {
    const { fixture, page } = await pagePrete(reponse([MONETAIRE, SCPI, ACTIONS]));

    choisir(page, 'categorie', 'SCPI');
    await fixture.whenStable();

    expect(cartes(page).map((c) => c.querySelector('h3')!.textContent)).toEqual(['SCPI Bureaux Europe']);
    choisir(page, 'categorie', '');
    choisir(page, 'risque', '1');
    await fixture.whenStable();
    expect(cartes(page).map((c) => c.querySelector('h3')!.textContent)).toEqual(['Monétaire Euro']);
    choisir(page, 'categorie', 'SCPI');
    await fixture.whenStable();
    expect(page.textContent).toContain('Aucun support ne correspond à ces filtres.');
  });

  it('propose les catégories présentes, sans doublon, triées', async () => {
    const { page } = await pagePrete(reponse([SCPI, MONETAIRE, support('SCPI 2', 'SCPI', 2)]));
    const options = Array.from(page.querySelectorAll('#categorie option')).map((o) => o.textContent!.trim());
    expect(options).toEqual(['Toutes', 'Monétaire euro', 'SCPI']);
  });

  it('Critère 9 : profil à revalider : aucune UC, message avec le lien vers le questionnaire, fonds en euros affiché', async () => {
    const { page } = await pagePrete(
      reponse([], {
        profilValide: false,
        message: 'Votre profil doit être validé pour investir en unités de compte',
      }),
    );

    expect(cartes(page).length).toBe(0);
    const avis = page.querySelector('[data-test="profil-a-valider"]')!;
    expect(avis.textContent).toContain('Votre profil doit être validé pour investir en unités de compte');
    expect(avis.querySelector('a')!.getAttribute('href')).toBe('/profil');
    expect(page.querySelector('[data-test="fonds-euros"]')).not.toBeNull();
    expect(page.querySelector('#categorie')).toBeNull();
  });

  it('Critère 11 : service indisponible : message, aucune liste incomplète', async () => {
    const fixture = creer();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${URL}?produit=AV`).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('[role="alert"]')!.textContent).toContain(
      'La liste des supports est momentanément indisponible',
    );
    expect(cartes(page).length).toBe(0);
  });

  it('ouvre les supports du PER depuis l’adresse, et passe de l’un à l’autre', async () => {
    const { fixture, http, page } = await pagePrete(reponse([SCPI], { produit: 'PER', fraisContratPct: 0.5 }), 'PER');
    expect(page.querySelector('[data-test="frais-contrat"]')!.textContent).toContain('0,50 % par an');
    expect(page.querySelector('h1')!.textContent).toContain('PER');

    page.querySelector<HTMLButtonElement>('[data-test="produit-AV"]')!.click();
    http.expectOne(`${URL}?produit=AV`).flush(reponse([]));
    await fixture.whenStable();
    expect(page.querySelector('h1')!.textContent).toContain('assurance vie');
    expect(page.textContent).toContain('Aucun support disponible pour votre profil.');
  });
});
