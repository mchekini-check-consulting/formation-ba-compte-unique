import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { EtatSouscription, Produit } from './souscription';
import { SouscriptionPage, lireMontant } from './souscription-page';

const URL = '/api/backend/souscriptions';

function etat(modif: Partial<EtatSouscription> = {}): EtatSouscription {
  return {
    produit: 'PER',
    profil: 'VALIDE',
    etape: 'VERSEMENT',
    minimum: 100,
    montant: null,
    origineFonds: null,
    originePrecision: null,
    beneficiaires: [],
    codeEnvoyeLe: null,
    bloqueeJusquAu: null,
    numeroContrat: null,
    documentsLus: true,
    ...modif,
  };
}

const enSignature = (modif: Partial<EtatSouscription> = {}) =>
  etat({ etape: 'SIGNATURE', montant: 500, origineFonds: 'REVENUS', ...modif });

async function ouvrirPage(reponse: EtatSouscription | null, produit: Produit = 'PER') {
  TestBed.configureTestingModule({
    imports: [SouscriptionPage],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ produit }) } } },
    ],
  });
  const fixture = TestBed.createComponent(SouscriptionPage);
  const http = TestBed.inject(HttpTestingController);
  const base = `${URL}/${produit.toLowerCase()}`;
  const demande = http.expectOne(base);
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
  return { http, page, rafraichir, base };
}

function saisir(champ: HTMLInputElement, valeur: string) {
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

const bouton = (page: HTMLElement, test: string) => page.querySelector<HTMLButtonElement>(`[data-test="${test}"]`)!;
const champ = (page: HTMLElement, test: string) => page.querySelector<HTMLInputElement>(`[data-test="${test}"]`)!;

describe('lireMontant', () => {
  it('lit les montants en euros (virgule, espaces) et refuse le reste', () => {
    expect(lireMontant('1 000,50')).toBe(1000.5);
    expect(lireMontant('100')).toBe(100);
    expect(lireMontant('12,345')).toBeNaN();
    expect(lireMontant('abc')).toBeNaN();
  });
});

describe('SouscriptionPage (CU-312, CU-330)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('backend indisponible : message compréhensible', async () => {
    const { page } = await ouvrirPage(null);
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('momentanément indisponible');
  });

  it('profil à revalider (adresse tapée) : redirigée vers le questionnaire (CU-10), sans formulaire', async () => {
    const navigate = vi.spyOn(Router.prototype, 'navigate').mockResolvedValue(true);
    const { page } = await ouvrirPage(etat({ profil: 'A_REVALIDER', documentsLus: false }));
    expect(navigate).toHaveBeenCalledWith(['/profil'], { replaceUrl: true });
    expect(page.querySelector('[data-test="versement"]')).toBeNull();
    navigate.mockRestore();
  });

  it('documents pas lus (adresse tapée) : retour à Découvrir du même produit (PER-02, AV-02)', async () => {
    const navigate = vi.spyOn(Router.prototype, 'navigate').mockResolvedValue(true);
    const { page } = await ouvrirPage(etat({ produit: 'AV', documentsLus: false }), 'AV');
    expect(navigate).toHaveBeenCalledWith(['/decouvrir'], { queryParams: { produit: 'AV' }, replaceUrl: true });
    expect(page.querySelector('[data-test="versement"]')).toBeNull();
    navigate.mockRestore();
  });

  it('contrat déjà signé : affiché même si les documents ne sont plus à lire', async () => {
    const navigate = vi.spyOn(Router.prototype, 'navigate');
    const { page } = await ouvrirPage(etat({ etape: 'SIGNEE', numeroContrat: 'PER-1', documentsLus: false }));
    expect(navigate).not.toHaveBeenCalled();
    expect(page.querySelector('[data-test="signee"]')).not.toBeNull();
    navigate.mockRestore();
  });

  it('PER : « Continuer » désactivé sous 100 € (avec le message), sans origine, ou « autre » sans précision ; pas de clause', async () => {
    const { http, page, rafraichir, base } = await ouvrirPage(etat());
    expect(page.querySelector('h1')?.textContent).toBe('Souscrire mon PER');
    expect(page.querySelector('[data-test="clause"]')).toBeNull();
    saisir(champ(page, 'montant'), '99,99');
    await rafraichir();
    expect(page.querySelector('[data-test="minimum"]')?.textContent).toBe('Le premier versement minimum est de 100 €');
    expect(bouton(page, 'continuer').disabled).toBe(true);

    saisir(champ(page, 'montant'), '500');
    await rafraichir();
    expect(page.querySelector('[data-test="minimum"]')).toBeNull();
    expect(bouton(page, 'continuer').disabled).toBe(true);

    page.querySelector<HTMLInputElement>('input[name="origine"][value="AUTRE"]')!.click();
    await rafraichir();
    expect(bouton(page, 'continuer').disabled).toBe(true);
    saisir(champ(page, 'precision'), 'Prime');
    await rafraichir();
    expect(bouton(page, 'continuer').disabled).toBe(false);

    page.querySelector<HTMLFormElement>('[data-test="versement"]')!.dispatchEvent(new Event('submit'));
    const envoi = http.expectOne(`${base}/versement`);
    expect(envoi.request.body).toEqual({ montant: 500, origineFonds: 'AUTRE', originePrecision: 'Prime', beneficiaires: [] });
    envoi.flush(enSignature({ origineFonds: 'AUTRE', originePrecision: 'Prime' }));
    await rafraichir();
    expect(page.querySelector('[data-test="signature"]')?.textContent).toContain('Autre : Prime');
  });

  it('AV : message du versement initial sous 100 €, quotes-parts à 100 % obligatoires, clause envoyée', async () => {
    const { http, page, rafraichir, base } = await ouvrirPage(etat({ produit: 'AV' }), 'AV');
    expect(page.querySelector('h1')?.textContent).toBe('Souscrire mon assurance vie');
    expect(page.querySelector('[data-test="clause"]')?.textContent).toContain('entrera dans votre succession');
    saisir(champ(page, 'montant'), '50');
    await rafraichir();
    expect(page.querySelector('[data-test="minimum"]')?.textContent).toBe('Le versement initial minimum est de 100 €');
    saisir(champ(page, 'montant'), '1 000');
    page.querySelector<HTMLInputElement>('input[name="origine"][value="EPARGNE"]')!.click();

    bouton(page, 'ajouter').click();
    bouton(page, 'ajouter').click();
    await rafraichir();
    const lignes = page.querySelectorAll<HTMLElement>('[data-test="beneficiaire"]');
    const remplir = (ligne: HTMLElement, nom: string, date: string, part: string) => {
      const [n, p, d, q] = Array.from(ligne.querySelectorAll<HTMLInputElement>('input'));
      saisir(n, nom);
      saisir(p, 'Léa');
      saisir(d, date);
      saisir(q, part);
    };
    remplir(lignes[0], 'Martin', '2010-05-01', '60');
    remplir(lignes[1], 'Durand', '2012-03-09', '30');
    await rafraichir();
    expect(page.querySelector('[data-test="quotes-parts"]')?.textContent).toBe('Les quotes-parts doivent totaliser 100 %');
    expect(bouton(page, 'continuer').disabled).toBe(true);

    saisir(lignes[1].querySelectorAll<HTMLInputElement>('input')[3], '40');
    await rafraichir();
    expect(page.querySelector('[data-test="quotes-parts"]')).toBeNull();
    expect(bouton(page, 'continuer').disabled).toBe(false);

    page.querySelector<HTMLFormElement>('[data-test="versement"]')!.dispatchEvent(new Event('submit'));
    const envoi = http.expectOne(`${base}/versement`);
    expect(envoi.request.body.montant).toBe(1000);
    expect(envoi.request.body.beneficiaires).toEqual([
      { nom: 'Martin', prenom: 'Léa', dateNaissance: '2010-05-01', quotePartPct: 60 },
      { nom: 'Durand', prenom: 'Léa', dateNaissance: '2012-03-09', quotePartPct: 40 },
    ]);
    envoi.flush(enSignature({ produit: 'AV', montant: 1000, beneficiaires: envoi.request.body.beneficiaires }));
    await rafraichir();
    expect(page.querySelector('[data-test="recap-clause"]')?.textContent).toContain('Léa Martin (60 %)');
  });

  it('AV : ligne retirée ; clause vide rappelée au moment de signer', async () => {
    const { page, rafraichir } = await ouvrirPage(etat({ produit: 'AV' }), 'AV');
    bouton(page, 'ajouter').click();
    await rafraichir();
    page.querySelector<HTMLButtonElement>('[data-test="beneficiaire"] button')!.click();
    await rafraichir();
    expect(page.querySelectorAll('[data-test="beneficiaire"]').length).toBe(0);
    TestBed.resetTestingModule();

    const signature = await ouvrirPage(enSignature({ produit: 'AV' }), 'AV');
    expect(signature.page.querySelector('[data-test="recap-clause"]')?.textContent)
      .toContain('Aucun bénéficiaire désigné');
  });

  it('code reçu puis signé : le contrat est ouvert, avec un lien vers la gestion du produit', async () => {
    const { http, page, rafraichir, base } = await ouvrirPage(enSignature({ produit: 'AV' }), 'AV');
    expect(page.querySelector('[data-test="formulaire-code"]')).toBeNull();
    bouton(page, 'code').click();
    http.expectOne(`${base}/code`).flush(enSignature({ produit: 'AV', codeEnvoyeLe: '2026-11-27T10:00:00+01:00' }));
    await rafraichir();
    expect(page.querySelector('.succes')?.textContent).toContain('envoyé par SMS');
    expect(bouton(page, 'code').textContent).toContain('nouveau code');

    saisir(page.querySelector<HTMLInputElement>('[data-test="formulaire-code"] input')!, '123456');
    await rafraichir();
    page.querySelector<HTMLFormElement>('[data-test="formulaire-code"]')!.dispatchEvent(new Event('submit'));
    const signature = http.expectOne(`${base}/signature`);
    expect(signature.request.body).toEqual({ code: '123456' });
    signature.flush(etat({ produit: 'AV', etape: 'SIGNEE', numeroContrat: 'AV-1A2B3C4D' }));
    await rafraichir();
    expect(page.querySelector('[data-test="signee"]')?.textContent).toContain('AV-1A2B3C4D');
    expect(page.querySelector('[data-test="signee"] a')?.getAttribute('href')).toBe('/gestion?produit=AV');
  });

  it('code erroné, expiré ou signature bloquée : le message du backend est affiché', async () => {
    const { http, page, rafraichir, base } = await ouvrirPage(enSignature({ codeEnvoyeLe: '2026-11-27T10:00:00+01:00' }));
    saisir(page.querySelector<HTMLInputElement>('[data-test="formulaire-code"] input')!, '000000');
    await rafraichir();
    page.querySelector<HTMLFormElement>('[data-test="formulaire-code"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${base}/signature`).flush(
      { message: 'Trois codes erronés : la signature est bloquée jusqu\'à 10 h 30.' },
      { status: 423, statusText: 'Locked' },
    );
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('bloquée jusqu\'à 10 h 30');
  });

  it('« Modifier » revient au versement, pré-rempli avec la clause ; refus sans message : texte par défaut', async () => {
    const { http, page, rafraichir, base } = await ouvrirPage(enSignature({
      produit: 'AV',
      beneficiaires: [{ nom: 'Martin', prenom: 'Léa', dateNaissance: '2010-05-01', quotePartPct: 100 }],
    }), 'AV');
    bouton(page, 'modifier').click();
    await rafraichir();
    expect(champ(page, 'montant').value).toBe('500');
    expect(page.querySelectorAll('[data-test="beneficiaire"]').length).toBe(1);
    page.querySelector<HTMLFormElement>('[data-test="versement"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${base}/versement`).flush(null, { status: 500, statusText: 'Erreur' });
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('n’a pas pu être enregistrée');
  });

  it('contrat déjà souscrit : numéro du contrat affiché', async () => {
    const { page } = await ouvrirPage(etat({ etape: 'SIGNEE', numeroContrat: 'PER-TEST-1' }));
    expect(page.querySelector('[data-test="signee"]')?.textContent).toContain('PER-TEST-1');
  });
});
