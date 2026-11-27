import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { EtatDecouverte, EtatDocument } from './decouverte';
import { DecouvertePage } from './decouverte-page';

const URL = '/api/backend/decouverte';

function doc(code: EtatDocument['code'], ouvert = false, lu = false): EtatDocument {
  return { code, titre: `Titre ${code}`, version: `${code}-V1`, contenu: `Contenu ${code}`, ouvert, lu };
}

function etat(dic: EtatDocument, fiche: EtatDocument, produit: 'PER' | 'AV' = 'PER'): EtatDecouverte {
  return { produit, documents: [dic, fiche], peutContinuer: dic.lu && fiche.lu };
}

async function ouvrirPage(reponse: EtatDecouverte, produit = 'PER') {
  TestBed.configureTestingModule({
    imports: [DecouvertePage],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ produit }) } } },
    ],
  });
  const fixture = TestBed.createComponent(DecouvertePage);
  const http = TestBed.inject(HttpTestingController);
  const demande = http.expectOne((r) => r.url === URL);
  expect(demande.request.params.get('produit')).toBe(produit);
  expect(demande.request.withCredentials).toBe(true);
  demande.flush(reponse);
  fixture.detectChanges();
  await fixture.whenStable();
  return { fixture, http, page: fixture.nativeElement as HTMLElement };
}

const element = <T extends HTMLElement>(page: HTMLElement, code: string, test: string) =>
  page.querySelector<T>(`[data-test="document-${code}"] [data-test="${test}"]`);

describe('DecouvertePage (CU-311, CU-331)', () => {
  it('présente le PER, ses règles de blocage et de fiscalité, et ses deux documents', async () => {
    const { page } = await ouvrirPage(etat(doc('DIC'), doc('FICHE_PRODUIT')));
    expect(page.querySelector('h1')?.textContent).toContain('Découvrir le PER');
    expect(page.textContent).toContain('Blocage jusqu’à la retraite');
    expect(page.textContent).toContain('Fiscalité');
    expect(page.querySelector('[data-test="document-DIC"] h3')?.textContent).toBe('Titre DIC');
    expect(page.querySelector('[data-test="document-FICHE_PRODUIT"] h3')?.textContent).toBe('Titre FICHE_PRODUIT');
  });

  it('présente l’assurance vie et ses règles de rachat', async () => {
    const { page } = await ouvrirPage(etat(doc('DIC'), doc('FICHE_PRODUIT'), 'AV'), 'AV');
    expect(page.querySelector('h1')?.textContent).toContain('Découvrir l’assurance vie');
    expect(page.textContent).toContain('Rachat');
  });

  it('la case d’un document pas encore ouvert reste désactivée, comme « Continuer »', async () => {
    const { page } = await ouvrirPage(etat(doc('DIC'), doc('FICHE_PRODUIT')));
    expect(element<HTMLInputElement>(page, 'DIC', 'case')!.disabled).toBe(true);
    expect(page.querySelector<HTMLButtonElement>('[data-test="continuer"]')!.disabled).toBe(true);
  });

  it('ouvrir un document enregistre l’ouverture, affiche son contenu et active sa case', async () => {
    const { fixture, http, page } = await ouvrirPage(etat(doc('DIC'), doc('FICHE_PRODUIT')));
    element<HTMLButtonElement>(page, 'DIC', 'ouvrir')!.click();
    const envoi = http.expectOne(`${URL}/ouvertures`);
    expect(envoi.request.body).toEqual({ produit: 'PER', document: 'DIC' });
    envoi.flush(etat(doc('DIC', true), doc('FICHE_PRODUIT')));
    fixture.detectChanges();

    expect(element(page, 'DIC', 'contenu')?.textContent).toBe('Contenu DIC');
    expect(element<HTMLInputElement>(page, 'DIC', 'case')!.disabled).toBe(false);
    expect(element<HTMLInputElement>(page, 'FICHE_PRODUIT', 'case')!.disabled).toBe(true);
  });

  it('« Continuer » reste désactivé avec une seule case cochée', async () => {
    const { fixture, http, page } = await ouvrirPage(etat(doc('DIC', true), doc('FICHE_PRODUIT', true)));
    element<HTMLInputElement>(page, 'DIC', 'case')!.click();
    const envoi = http.expectOne(`${URL}/lectures`);
    expect(envoi.request.body).toEqual({ produit: 'PER', document: 'DIC' });
    envoi.flush(etat(doc('DIC', true, true), doc('FICHE_PRODUIT', true)));
    fixture.detectChanges();

    expect(page.querySelector<HTMLButtonElement>('[data-test="continuer"]')!.disabled).toBe(true);
  });

  it('PER, les deux documents ouverts et cochés : « Continuer » mène à la souscription (CU-312)', async () => {
    const { page } = await ouvrirPage(etat(doc('DIC', true, true), doc('FICHE_PRODUIT', true, true)));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const continuer = page.querySelector<HTMLButtonElement>('[data-test="continuer"]')!;
    expect(continuer.disabled).toBe(false);
    continuer.click();
    expect(navigate).toHaveBeenCalledWith(['/souscrire'], { queryParams: { produit: 'PER' } });
  });

  it('assurance vie : « Continuer » mène à la souscription de l’assurance vie (CU-330)', async () => {
    const { page } = await ouvrirPage(etat(doc('DIC', true, true), doc('FICHE_PRODUIT', true, true), 'AV'), 'AV');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    page.querySelector<HTMLButtonElement>('[data-test="continuer"]')!.click();
    expect(navigate).toHaveBeenCalledWith(['/souscrire'], { queryParams: { produit: 'AV' } });
  });

  it('une case refusée par le backend redevient décochée, avec un message', async () => {
    const { fixture, http, page } = await ouvrirPage(etat(doc('DIC', true), doc('FICHE_PRODUIT')));
    const caseDic = element<HTMLInputElement>(page, 'DIC', 'case')!;
    caseDic.click();
    http.expectOne(`${URL}/lectures`).flush({ message: 'refus' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(caseDic.checked).toBe(false);
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('n’a pas pu être enregistrée');
  });

  it('documents indisponibles : message, sans bouton « Continuer »', async () => {
    TestBed.configureTestingModule({
      imports: [DecouvertePage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      ],
    });
    const fixture = TestBed.createComponent(DecouvertePage);
    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url === URL)
      .flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('momentanément indisponibles');
    expect(page.querySelector('[data-test="continuer"]')).toBeNull();
  });
});
