import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ConnexionPage } from './connexion-page';

describe('ConnexionPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConnexionPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function soumettre(page: HTMLElement): void {
    page.querySelector<HTMLInputElement>('input[name=identifiant]')!.value = ' ABC ';
    page.querySelector<HTMLInputElement>('input[name=motDePasse]')!.value = 'secret';
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  it("envoie les identifiants avec le cookie et redirige vers l'espace", async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    soumettre(fixture.nativeElement);
    const requete = http.expectOne({ method: 'POST', url: '/api/backend/connexion' });
    expect(requete.request.body).toEqual({ identifiant: 'ABC', motDePasse: 'secret' });
    expect(requete.request.withCredentials).toBe(true);
    requete.flush({ identifiant: 'ABC' });
    expect(navigation).toHaveBeenCalledWith('/espace');
  });

  it('affiche le message du refus et vide le mot de passe', async () => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    soumettre(page);
    http
      .expectOne('/api/backend/connexion')
      .flush({ message: 'Identifiant ou mot de passe incorrect', invitation: null }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    const alerte = page.querySelectorAll('[role=alert] p');
    expect(alerte.length).toBe(1);
    expect(alerte[0].textContent).toBe('Identifiant ou mot de passe incorrect');
    expect(page.querySelector<HTMLInputElement>('input[name=motDePasse]')!.value).toBe('');
  });

  it("ajoute l'invitation à utiliser le lien reçu par SMS", async () => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    soumettre(page);
    http
      .expectOne('/api/backend/connexion')
      .flush(
        { message: 'Identifiant ou mot de passe incorrect', invitation: 'Utilisez le lien reçu.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();
    const alerte = page.querySelectorAll('[role=alert] p');
    expect(alerte[0].textContent).toBe('Identifiant ou mot de passe incorrect');
    expect(alerte[1].textContent).toBe('Utilisez le lien reçu.');
  });

  it('signale une panne sans parler des identifiants', async () => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    soumettre(page);
    http.expectOne('/api/backend/connexion').flush('', { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('réessayez plus tard');
  });

  it('affiche la maquette : titre, arguments et liens utiles', async () => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.card h2')?.textContent).toBe('Me connecter');
    expect(page.querySelectorAll('.points li').length).toBe(3);
    expect(page.querySelector('.row a')?.getAttribute('href')).toBe('/mot-de-passe-oublie');
    expect(page.querySelector('.newacc a')?.getAttribute('href')).toBe('/');
  });

  it('affiche puis masque le mot de passe sans rien envoyer', async () => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const champ = page.querySelector<HTMLInputElement>('input[name=motDePasse]')!;
    const oeil = page.querySelector<HTMLButtonElement>('button.eye')!;
    expect(champ.type).toBe('password');
    oeil.click();
    await fixture.whenStable();
    expect(champ.type).toBe('text');
    expect(oeil.textContent).toContain('Masquer');
    expect(oeil.getAttribute('aria-pressed')).toBe('true');
    oeil.click();
    await fixture.whenStable();
    expect(champ.type).toBe('password');
    expect(oeil.textContent).toContain('Afficher');
  });
});
