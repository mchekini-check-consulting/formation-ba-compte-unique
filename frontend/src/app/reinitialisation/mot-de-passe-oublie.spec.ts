import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MotDePasseOublie } from './mot-de-passe-oublie';

const MESSAGE = 'Si un compte correspond, un lien vient d’être envoyé par SMS';

// CU-46 (critère 1) : même message qu'un compte existe ou non.
describe('MotDePasseOublie (CU-46)', () => {
  let fixture: ComponentFixture<MotDePasseOublie>;
  let page: HTMLElement;
  let http: HttpTestingController;

  const demander = async (identifiant: string) => {
    page.querySelector<HTMLInputElement>('input[name="identifiant"]')!.value = identifiant;
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [MotDePasseOublie],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MotDePasseOublie);
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  it("envoie l'identifiant et affiche le message du serveur", async () => {
    expect(page.querySelector('.card h2')?.textContent).toContain('Mot de passe oublié');
    await demander(' 12345678 ');
    const requete = http.expectOne({ method: 'POST', url: '/api/backend/reinitialisation/demande' });
    expect(requete.request.body).toEqual({ identifiant: '12345678' });
    requete.flush({ message: MESSAGE, regles: [] }, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')?.textContent).toContain(MESSAGE);
    expect(page.querySelector('form')).toBeNull();
  });

  it('signale un service indisponible', async () => {
    await demander('12345678');
    http.expectOne('/api/backend/reinitialisation/demande').flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('réessayez plus tard');
    expect(page.querySelector('form')).not.toBeNull();
  });
});
