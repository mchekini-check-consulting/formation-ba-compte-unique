import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EtatSouscription } from './souscription';
import { SouscriptionPage, lireMontant } from './souscription-page';

const URL = '/api/backend/souscriptions/per';

function etat(modif: Partial<EtatSouscription> = {}): EtatSouscription {
  return {
    profil: 'VALIDE',
    etape: 'VERSEMENT',
    minimum: 100,
    montant: null,
    origineFonds: null,
    originePrecision: null,
    codeEnvoyeLe: null,
    bloqueeJusquAu: null,
    numeroContrat: null,
    ...modif,
  };
}

const enSignature = (modif: Partial<EtatSouscription> = {}) =>
  etat({ etape: 'SIGNATURE', montant: 500, origineFonds: 'REVENUS', ...modif });

async function ouvrirPage(reponse: EtatSouscription | null) {
  TestBed.configureTestingModule({
    imports: [SouscriptionPage],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(SouscriptionPage);
  const http = TestBed.inject(HttpTestingController);
  const demande = http.expectOne(URL);
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
  return { http, page, rafraichir };
}

function saisir(page: HTMLElement, selecteur: string, valeur: string) {
  const champ = page.querySelector<HTMLInputElement>(selecteur)!;
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

const bouton = (page: HTMLElement, test: string) => page.querySelector<HTMLButtonElement>(`[data-test="${test}"]`)!;

describe('lireMontant', () => {
  it('lit les montants en euros (virgule, espaces) et refuse le reste', () => {
    expect(lireMontant('1 000,50')).toBe(1000.5);
    expect(lireMontant('100')).toBe(100);
    expect(lireMontant('12,345')).toBeNaN();
    expect(lireMontant('abc')).toBeNaN();
  });
});

describe('SouscriptionPage (CU-312)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('backend indisponible : message compréhensible', async () => {
    const { page } = await ouvrirPage(null);
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('momentanément indisponible');
  });

  it('critère 2 : profil à revalider : pas de formulaire, lien vers le questionnaire', async () => {
    const { page } = await ouvrirPage(etat({ profil: 'A_REVALIDER' }));
    expect(page.querySelector('[data-test="profil-requis"] a')?.getAttribute('href')).toBe('/profil');
    expect(page.querySelector('[data-test="versement"]')).toBeNull();
  });

  it('critères 4 et 5 : « Continuer » désactivé sous 100 € (avec le message), sans origine, ou « autre » sans précision', async () => {
    const { http, page, rafraichir } = await ouvrirPage(etat());
    saisir(page, '[data-test="versement"] input[inputmode="decimal"]', '99,99');
    await rafraichir();
    expect(page.querySelector('[data-test="minimum"]')?.textContent).toBe('Le premier versement minimum est de 100 €');
    expect(bouton(page, 'continuer').disabled).toBe(true);

    saisir(page, '[data-test="versement"] input[inputmode="decimal"]', '500');
    await rafraichir();
    expect(page.querySelector('[data-test="minimum"]')).toBeNull();
    expect(bouton(page, 'continuer').disabled).toBe(true);

    page.querySelector<HTMLInputElement>('input[name="origine"][value="AUTRE"]')!.click();
    await rafraichir();
    expect(bouton(page, 'continuer').disabled).toBe(true);
    saisir(page, 'input[maxlength="200"]', 'Prime');
    await rafraichir();
    expect(bouton(page, 'continuer').disabled).toBe(false);

    page.querySelector<HTMLFormElement>('[data-test="versement"]')!.dispatchEvent(new Event('submit'));
    const envoi = http.expectOne(`${URL}/versement`);
    expect(envoi.request.body).toEqual({ montant: 500, origineFonds: 'AUTRE', originePrecision: 'Prime' });
    envoi.flush(enSignature({ origineFonds: 'AUTRE', originePrecision: 'Prime' }));
    await rafraichir();
    expect(page.querySelector('[data-test="signature"]')?.textContent).toContain('Autre : Prime');
  });

  it('critère 1 : code reçu puis signé : le PER est ouvert, avec un lien vers la gestion', async () => {
    const { http, page, rafraichir } = await ouvrirPage(enSignature());
    expect(page.querySelector('[data-test="formulaire-code"]')).toBeNull();
    bouton(page, 'code').click();
    http.expectOne(`${URL}/code`).flush(enSignature({ codeEnvoyeLe: '2026-11-27T10:00:00+01:00' }));
    await rafraichir();
    expect(page.querySelector('.succes')?.textContent).toContain('envoyé par SMS');
    expect(bouton(page, 'code').textContent).toContain('nouveau code');

    saisir(page, '[data-test="formulaire-code"] input', '123456');
    await rafraichir();
    page.querySelector<HTMLFormElement>('[data-test="formulaire-code"]')!.dispatchEvent(new Event('submit'));
    const signature = http.expectOne(`${URL}/signature`);
    expect(signature.request.body).toEqual({ code: '123456' });
    signature.flush(etat({ etape: 'SIGNEE', numeroContrat: 'PER-1A2B3C4D' }));
    await rafraichir();
    expect(page.querySelector('[data-test="signee"]')?.textContent).toContain('PER-1A2B3C4D');
    expect(page.querySelector('[data-test="signee"] a')?.getAttribute('href')).toBe('/gestion?produit=PER');
  });

  it('critères 6 à 8 : code erroné, expiré ou signature bloquée : le message du backend est affiché', async () => {
    const { http, page, rafraichir } = await ouvrirPage(enSignature({ codeEnvoyeLe: '2026-11-27T10:00:00+01:00' }));
    saisir(page, '[data-test="formulaire-code"] input', '000000');
    await rafraichir();
    page.querySelector<HTMLFormElement>('[data-test="formulaire-code"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${URL}/signature`).flush(
      { message: 'Trois codes erronés : la signature est bloquée jusqu\'à 10 h 30.' },
      { status: 423, statusText: 'Locked' },
    );
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('bloquée jusqu\'à 10 h 30');
  });

  it('« Modifier » revient au versement, pré-rempli ; refus sans message : texte par défaut', async () => {
    const { http, page, rafraichir } = await ouvrirPage(enSignature());
    bouton(page, 'modifier').click();
    await rafraichir();
    expect(page.querySelector<HTMLInputElement>('[data-test="versement"] input[inputmode="decimal"]')!.value).toBe('500');
    page.querySelector<HTMLFormElement>('[data-test="versement"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${URL}/versement`).flush(null, { status: 500, statusText: 'Erreur' });
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('n’a pas pu être enregistrée');
  });

  it('PER déjà souscrit : numéro du contrat affiché', async () => {
    const { page } = await ouvrirPage(etat({ etape: 'SIGNEE', numeroContrat: 'PER-TEST-1' }));
    expect(page.querySelector('[data-test="signee"]')?.textContent).toContain('PER-TEST-1');
  });
});
