import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EtatBouchon, VerificationTest } from './verification-test';

const URL = '/api/backend/test/verification-beneficiaire';
const ETAT: EtatBouchon = {
  indisponible: false,
  appels: [{ appeleLe: '2026-11-11T10:00:00Z', iban: 'FR7699999000019900000000202', resultat: 'NE_CORRESPOND_PAS' }],
  comptesExternes: [{ iban: 'DE89370400440532013000', banque: 'Banque allemande', titulaire: 'Jean Martin' }],
};

describe('VerificationTest (CU-145)', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerificationTest],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir(reponse: EtatBouchon | null) {
    const fixture = TestBed.createComponent(VerificationTest);
    const requete = http.expectOne({ method: 'GET', url: URL });
    if (reponse) {
      requete.flush(reponse);
    } else {
      requete.flush(null, { status: 404, statusText: 'Not Found' });
    }
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('critère 7 : affiche le journal de test (date, IBAN, résultat)', async () => {
    const { page } = await ouvrir(ETAT);
    const externe = page.querySelector('table.externes tbody tr')?.textContent ?? '';
    expect(externe).toContain('DE89370400440532013000');
    expect(externe).toContain('Jean Martin');
    expect(externe).toContain('Banque allemande');
    const ligne = page.querySelectorAll('tbody tr')[1]?.textContent ?? '';
    expect(ligne).toContain('FR7699999000019900000000202');
    expect(ligne).toContain('ne correspond pas');
    expect((page.querySelector('input[type=checkbox]') as HTMLInputElement).checked).toBe(false);
  });

  it('critère 6 : hors environnement de test, indique que le bouchon n’est pas disponible', async () => {
    const { page } = await ouvrir(null);
    expect(page.querySelector('[role=status]')?.textContent).toBe('Bouchon non disponible dans cet environnement.');
  });

  it('critère 4 : active le sélecteur « Service indisponible »', async () => {
    const { fixture, page } = await ouvrir({ indisponible: false, appels: [], comptesExternes: [] });
    expect(page.textContent).toContain('Aucun appel.');
    const case_ = page.querySelector('input[type=checkbox]') as HTMLInputElement;
    case_.checked = true;
    case_.dispatchEvent(new Event('change'));
    const put = http.expectOne({ method: 'PUT', url: `${URL}/indisponible` });
    expect(put.request.body).toEqual({ indisponible: true });
    put.flush({ indisponible: true, appels: [] });
    await fixture.whenStable();
    expect(case_.checked).toBe(true);

    case_.dispatchEvent(new Event('change'));
    http.expectOne({ method: 'PUT', url: `${URL}/indisponible` }).flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')?.textContent).toBe('Bouchon non disponible dans cet environnement.');
  });

  it('critères 1 à 3 : vérifie un IBAN et un nom, affiche le résultat et relit le journal', async () => {
    const { fixture, page } = await ouvrir(ETAT);
    (page.querySelector('#verif-iban') as HTMLInputElement).value = 'FR76 9999 9000 0199 0000 0000 202';
    (page.querySelector('#verif-nom') as HTMLInputElement).value = 'Test Cliente-B';
    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    const post = http.expectOne({ method: 'POST', url: URL });
    expect(post.request.body).toEqual({ iban: 'FR76 9999 9000 0199 0000 0000 202', nom: 'Test Cliente-B' });
    post.flush({ resultat: 'CORRESPOND', nomVerifie: 'Test Cliente-B' });
    http.expectOne({ method: 'GET', url: URL }).flush(ETAT);
    await fixture.whenStable();
    expect(page.querySelector('.resultat')?.textContent).toBe('Résultat : correspond : Test Cliente-B');

    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    http.expectOne({ method: 'POST', url: URL }).flush({ resultat: 'INTROUVABLE', nomVerifie: null });
    http.expectOne({ method: 'GET', url: URL }).flush(ETAT);
    await fixture.whenStable();
    expect(page.querySelector('.resultat')?.textContent).toBe('Résultat : introuvable');
  });

  it('affiche « IBAN invalide » ou un message générique quand la vérification est refusée', async () => {
    const { fixture, page } = await ouvrir(ETAT);
    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    http.expectOne({ method: 'POST', url: URL }).flush({ message: 'IBAN invalide' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('.resultat')?.textContent).toBe('IBAN invalide');

    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    http.expectOne({ method: 'POST', url: URL }).flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('.resultat')?.textContent).toBe('La vérification n’a pas pu aboutir.');
  });
});
