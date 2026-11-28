import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ContratGestion, EtatGestion } from './gestion';
import { GestionPage } from './gestion-page';

const URL = '/api/backend/gestion';

function contrat(modif: Partial<ContratGestion> = {}): ContratGestion {
  return {
    numeroContrat: 'AV-TEST-1A2B3C4D',
    mode: 'PILOTEE',
    profil: 'EQUILIBRE',
    changeLe: '2026-11-26T15:30:00+01:00',
    encours: 1000,
    encoursNonCote: 0,
    contraintes: { partMinNonCotePct: 4, partMinFaibleRisquePct: 0, horizonAnnees: null, grilleAValider: false },
    allocation: [
      { codeIsin: 'NC', nom: 'Non coté', categorie: 'SCPI', niveauRisque: 6, nonCote: true, partPct: 4 },
      { codeIsin: 'AC', nom: 'Actions', categorie: 'Actions', niveauRisque: 5, nonCote: false, partPct: 96 },
    ],
    allocationIndisponible: null,
    historique: [{ mode: 'PILOTEE', profil: 'EQUILIBRE', horizonAnnees: null, frais: 0, changeLe: '2026-11-26T15:30:00+01:00' }],
    reequilibrages: [],
    ...modif,
  };
}

function etat(modif: Partial<EtatGestion> = {}): EtatGestion {
  return {
    produit: 'AV',
    profilInvestisseur: 'EQUILIBRE',
    contratsDeTest: true,
    mandat: { tauxToutComprisPct: 1.5, montantMinimum: 50000 },
    contrat: contrat(),
    ...modif,
  };
}

async function ouvrirPage(reponse: EtatGestion | null, produit = 'AV') {
  TestBed.configureTestingModule({
    imports: [GestionPage],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ produit }) } } },
    ],
  });
  const fixture = TestBed.createComponent(GestionPage);
  const http = TestBed.inject(HttpTestingController);
  const demande = http.expectOne((r) => r.url === URL);
  expect(demande.request.params.get('produit')).toBe(produit);
  expect(demande.request.withCredentials).toBe(true);
  if (reponse) {
    demande.flush(reponse);
  } else {
    demande.flush(null, { status: 503, statusText: 'Indisponible' });
  }
  fixture.detectChanges();
  await fixture.whenStable();
  const page = fixture.nativeElement as HTMLElement;
  const rafraichir = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };
  return { fixture, http, page, rafraichir };
}

const choisirMode = (page: HTMLElement, mode: string) => {
  const radio = page.querySelector<HTMLInputElement>(`input[name="mode"][value="${mode}"]`)!;
  radio.click();
};

describe('GestionPage (CU-314, CU-338)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('backend indisponible : message compréhensible', async () => {
    const { page } = await ouvrirPage(null);
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('momentanément indisponible');
  });

  it('sans contrat, même en environnement de test : la page mène à la souscription, sans contrat de test (AV-01)', async () => {
    const { page } = await ouvrirPage(etat({ contrat: null, contratsDeTest: true }));
    expect(page.querySelector('[data-test="creer"]')).toBeNull();
    expect(page.textContent).not.toContain('contrat de test');
    const souscrire = page.querySelector<HTMLAnchorElement>('[data-test="souscrire"]')!;
    expect(souscrire.textContent).toContain('Souscrire mon assurance vie');
    expect(souscrire.getAttribute('href')).toBe('/produit?produit=AV');
  });

  it('PER sans contrat ni profil validé : lien vers le profil et vers la souscription du PER', async () => {
    const { page } = await ouvrirPage(etat({ produit: 'PER', contrat: null, profilInvestisseur: null }), 'PER');
    expect(page.querySelector('[data-test="profil-requis"] a')?.getAttribute('href')).toBe('/profil');
    expect(page.querySelector('[data-test="souscrire"]')?.getAttribute('href')).toBe('/produit?produit=PER');
  });

  it('contrat de test : aucun lien vers les pages de test', async () => {
    const { page } = await ouvrirPage(etat());
    expect(page.querySelector('a[href^="/test/"]')).toBeNull();
  });

  it('gestion sous mandat sélectionnée : taux tout compris et montant minimum affichés avant validation', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat());
    expect(page.querySelector('[data-test="conditions-mandat"]')).toBeNull();
    choisirMode(page, 'MANDAT');
    await rafraichir();
    const conditions = page.querySelector('[data-test="conditions-mandat"]')?.textContent;
    expect(conditions).toContain('1,5 % par an');
    expect(conditions).toContain('50 000 €');
    expect(conditions).toContain('sans préavis');
    http.expectNone(`${URL}/choix`);
  });

  it('refus du backend (encours sous le minimum, profil trop risqué) : le message est affiché', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat());
    choisirMode(page, 'MANDAT');
    await rafraichir();
    page.querySelector<HTMLButtonElement>('[data-test="valider"]')!.click();
    const choix = http.expectOne(`${URL}/choix`);
    expect(choix.request.body).toEqual({ produit: 'AV', mode: 'MANDAT', profil: 'EQUILIBRE' });
    choix.flush({ message: 'La gestion sous mandat demande un encours d’au moins 50 000,00 €' }, { status: 422, statusText: 'Refus' });
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('au moins 50 000,00 €');
  });

  it('choix inchangé : « Valider » est désactivé ; profil de gestion plafonné rappelé (AV et PER)', async () => {
    const { page, rafraichir } = await ouvrirPage(etat());
    expect(page.querySelector<HTMLButtonElement>('[data-test="valider"]')!.disabled).toBe(true);
    expect(page.textContent).toContain('ne peut pas le dépasser');
    const select = page.querySelector<HTMLSelectElement>('select[name="profil"]')!;
    select.value = 'DYNAMIQUE';
    select.dispatchEvent(new Event('change'));
    await rafraichir();
    expect(page.querySelector<HTMLButtonElement>('[data-test="valider"]')!.disabled).toBe(false);
  });

  it('passage de libre à pilotée : choix enregistré sans frais, rééquilibrage annoncé et affiché', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat({ contrat: contrat({ mode: 'LIBRE' }) }));
    expect(page.querySelector('[data-test="allocation"]')).toBeNull();
    choisirMode(page, 'PILOTEE');
    await rafraichir();
    page.querySelector<HTMLButtonElement>('[data-test="valider"]')!.click();
    http.expectOne(`${URL}/choix`).flush(
      etat({
        contrat: contrat({
          reequilibrages: [{ demandeLe: '2026-11-26T15:30:00+01:00', montantArbitre: 520, arbitrages: [
            { ordreId: 'o1', supportDepart: 'AC', supportArrivee: 'NC', montant: 40 },
          ] }],
        }),
      }),
    );
    await rafraichir();
    expect(page.querySelector('.succes')?.textContent).toContain('rééquilibrées au prochain cours, sans frais');
    expect(page.querySelector('[data-test="reequilibrage"]')?.textContent).toContain('1 arbitrage(s) gratuit(s)');
    expect(page.querySelector('[data-test="historique"]')?.textContent).toContain('frais 0,00 €');
  });

  it('AV-07 : cliente dynamique avec 2 000 € de SCPI et 1 500 € de capital-investissement, passage en pilotée prudente : message avec 3 500 € avant validation', async () => {
    const { http, page, rafraichir } = await ouvrirPage(
      etat({ profilInvestisseur: 'DYNAMIQUE', contrat: contrat({ mode: 'LIBRE', encoursNonCote: 3500 }) }),
    );
    expect(page.querySelector('[data-test="non-cotes"]')).toBeNull();
    choisirMode(page, 'PILOTEE');
    await rafraichir();
    const select = page.querySelector<HTMLSelectElement>('select[name="profil"]')!;
    select.value = 'PRUDENT';
    select.dispatchEvent(new Event('change'));
    await rafraichir();
    expect(page.querySelector('[data-test="non-cotes"]')?.textContent).toBe(
      'Vos supports non cotés (3 500 €) restent dans votre contrat et ne sont pas gérés par la gestion pilotée.',
    );
    http.expectNone(`${URL}/choix`);
    page.querySelector<HTMLButtonElement>('[data-test="valider"]')!.click();
    expect(http.expectOne(`${URL}/choix`).request.body).toEqual({ produit: 'AV', mode: 'PILOTEE', profil: 'PRUDENT' });
  });

  it('AV-07 : sans non coté, ou déjà en pilotée, pas de message', async () => {
    const { page, rafraichir } = await ouvrirPage(etat({ contrat: contrat({ mode: 'LIBRE' }) }));
    choisirMode(page, 'PILOTEE');
    await rafraichir();
    expect(page.querySelector('[data-test="non-cotes"]')).toBeNull();
  });

  it('sortie du mandat : enregistrée sans préavis', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat({ contrat: contrat({ mode: 'MANDAT' }) }));
    choisirMode(page, 'LIBRE');
    await rafraichir();
    expect(page.querySelector('select[name="profil"]')).toBeNull();
    page.querySelector<HTMLButtonElement>('[data-test="valider"]')!.click();
    http.expectOne(`${URL}/choix`).flush(etat({ contrat: contrat({ mode: 'LIBRE' }) }));
    await rafraichir();
    expect(page.querySelector('.succes')?.textContent).toContain('sortie du mandat est enregistrée, sans préavis');
  });

  it('PER piloté : parts minimales de la grille (exemple à valider) et répartition affichées', async () => {
    const { page } = await ouvrirPage(
      etat({
        produit: 'PER',
        contrat: contrat({ contraintes: { partMinNonCotePct: 8, partMinFaibleRisquePct: 10, horizonAnnees: 15, grilleAValider: true } }),
      }),
      'PER',
    );
    const allocation = page.querySelector('[data-test="allocation"]')?.textContent;
    expect(allocation).toContain('À 15 an(s) de votre retraite');
    expect(allocation).toContain('10 % d’actifs à faible risque');
    expect(allocation).toContain('8 % de non coté');
    expect(page.querySelector('[data-test="a-valider"]')?.textContent).toBe('Pourcentages d’exemple, à valider par la conformité.');
    expect(page.querySelector('.badge')?.textContent).toBe('non coté');
    // Lot 6 : répartition par catégorie (de la plus grande part à la plus petite), détail par support repliable.
    const categories = [...page.querySelectorAll('[data-test="par-categorie"] .rp-ligne')].map((l) => `${l.querySelector('span')?.textContent} ${l.querySelector('strong')?.textContent}`);
    expect(categories).toEqual(['Actions 96 %', 'SCPI 4 %']);
    expect(page.querySelector('[data-test="detail-supports"]')?.tagName).toBe('DETAILS');
  });

  it('allocation impossible : le motif est affiché à la place de la répartition', async () => {
    const { page } = await ouvrirPage(etat({ contrat: contrat({ allocationIndisponible: 'Aucun support non coté' }) }));
    expect(page.querySelector('[data-test="allocation"] .erreur')?.textContent).toContain('Aucun support non coté');
    expect(page.querySelector('[data-test="versement"]')).toBeNull();
  });

  it('versement en gestion pilotée : réparti par le backend, répartition affichée ; montant invalide refusé', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat());
    const champ = page.querySelector<HTMLInputElement>('[data-test="versement"] input')!;
    const formulaire = page.querySelector<HTMLFormElement>('[data-test="versement"]')!;
    champ.value = 'abc';
    champ.dispatchEvent(new Event('change'));
    formulaire.dispatchEvent(new Event('submit'));
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('montant positif');

    champ.value = '1 000,00';
    champ.dispatchEvent(new Event('change'));
    formulaire.dispatchEvent(new Event('submit'));
    const versement = http.expectOne(`${URL}/versements`);
    expect(versement.request.body.montant).toBe(1000);
    expect(versement.request.body.produit).toBe('AV');
    expect(versement.request.body.id).toMatch(/^[0-9a-f-]{36}$/);
    versement.flush({ id: 'x', montant: 1000, repartition: [{ codeIsin: 'NC', montant: 80 }, { codeIsin: 'AC', montant: 920 }] });
    await rafraichir();
    expect(page.querySelector('[data-test="repartition"]')?.textContent).toContain('NC : 80,00 €');
    expect(page.querySelector('.succes')?.textContent).toContain('1 000,00 €');
  });

  it('versement refusé sans message : texte par défaut', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat());
    const champ = page.querySelector<HTMLInputElement>('[data-test="versement"] input')!;
    champ.value = '10';
    champ.dispatchEvent(new Event('change'));
    page.querySelector<HTMLFormElement>('[data-test="versement"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${URL}/versements`).flush(null, { status: 500, statusText: 'Erreur' });
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('n’a pas pu être enregistré');
  });

  it('CU-349 : versement vers une UC devenue inadaptée au profil : le refus du backend est affiché', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat());
    const champ = page.querySelector<HTMLInputElement>('[data-test="versement"] input')!;
    champ.value = '100';
    champ.dispatchEvent(new Event('change'));
    page.querySelector<HTMLFormElement>('[data-test="versement"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${URL}/versements`).flush({ message: 'Ce support ne correspond pas à votre profil' }, { status: 422, statusText: 'Refus' });
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Ce support ne correspond pas à votre profil');
  });
});
