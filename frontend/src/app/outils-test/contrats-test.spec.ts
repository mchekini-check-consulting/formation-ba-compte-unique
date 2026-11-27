import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ContratsTest } from './contrats-test';

const URL = '/api/backend/gestion/contrats';

async function ouvrirPage() {
  TestBed.configureTestingModule({
    imports: [ContratsTest],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(ContratsTest);
  fixture.detectChanges();
  await fixture.whenStable();
  const page = fixture.nativeElement as HTMLElement;
  const rafraichir = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };
  return { http: TestBed.inject(HttpTestingController), page, rafraichir };
}

describe('ContratsTest (/test/contrats)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('crée un contrat de test assurance vie pour la cliente connectée', async () => {
    const { http, page, rafraichir } = await ouvrirPage();
    page.querySelector<HTMLButtonElement>('[data-test="creer-AV"]')!.click();
    const creation = http.expectOne(URL);
    expect(creation.request.body).toEqual({ produit: 'AV' });
    expect(creation.request.withCredentials).toBe(true);
    creation.flush({ contrat: { numeroContrat: 'AV-TEST-1' } });
    await rafraichir();
    expect(page.querySelector('.succes')?.textContent).toContain('AV-TEST-1 créé, en gestion pilotée');
  });

  it('refus du backend : son message, sinon un texte par défaut', async () => {
    const { http, page, rafraichir } = await ouvrirPage();
    page.querySelector<HTMLButtonElement>('[data-test="creer-PER"]')!.click();
    http.expectOne(URL).flush({ message: 'Vous avez déjà un PER.' }, { status: 409, statusText: 'Conflict' });
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Vous avez déjà un PER.');
    page.querySelector<HTMLButtonElement>('[data-test="creer-PER"]')!.click();
    http.expectOne(URL).flush(null, { status: 404, statusText: 'Not Found' });
    await rafraichir();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('connectez-vous d’abord');
  });
});
