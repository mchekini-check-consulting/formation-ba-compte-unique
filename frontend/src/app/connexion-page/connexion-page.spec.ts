import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ConnexionPage, messageDeRefus, reessayerDans } from './connexion-page';

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

  it.each([
    [429, 'Trop de tentatives. Réessayez dans 5 minutes.'],
    [429, 'Trop de tentatives depuis votre connexion. Réessayez plus tard.'],
    [503, 'Connexion momentanément impossible. Réessayez dans quelques minutes.'],
  ])('CU-16 / CU-48 : affiche le message du refus %i « %s »', async (status, message) => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    soumettre(page);
    http.expectOne('/api/backend/connexion').flush({ message, invitation: null }, { status, statusText: 'Refus' });
    await fixture.whenStable();
    const alerte = page.querySelectorAll('[role=alert] p');
    expect(alerte.length).toBe(1);
    expect(alerte[0].textContent).toBe(message);
    expect(page.querySelector<HTMLInputElement>('input[name=motDePasse]')!.value).toBe('');
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

  it('affiche « Session expirée » après 10 minutes sans activité (CU-17)', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ConnexionPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ motif: 'expiree' }) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const message = (fixture.nativeElement as HTMLElement).querySelector('[role=status]');
    expect(message?.textContent).toContain('Session expirée');
  });

  it("n'affiche pas « Session expirée » sur une connexion ordinaire", async () => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Session expirée');
  });

  async function arriverAvecLeMotif(motif: string) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ConnexionPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ motif }) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  // CU-169 : session révoquée ou expirée côté serveur pendant la navigation.
  it('affiche « Session expirée » quand le serveur a fermé la session (CU-169)', async () => {
    const message = (await arriverAvecLeMotif('session_expiree')).querySelector('[role=status]');
    expect(message?.querySelector('b')?.textContent).toBe('Session expirée');
    expect(message?.textContent).toContain('Votre session a été fermée. Reconnectez-vous pour continuer.');
    expect(message?.textContent).not.toContain('10 minutes');
  });

  it("n'affiche pas « Session expirée » avec un motif inconnu", async () => {
    expect((await arriverAvecLeMotif('autre')).textContent).not.toContain('Session expirée');
  });

  // CU-171 : la durée restante du blocage vient du backend (reessayerDansSecondes).
  it.each([
    ['Trop de tentatives depuis votre connexion. Réessayez plus tard.', 754, 'Trop de tentatives depuis votre connexion. Réessayez dans 13 minutes.'],
    ['Trop de tentatives. Réessayez dans 5 minutes.', 170, 'Trop de tentatives. Réessayez dans 3 minutes.'],
    ['Trop de tentatives. Réessayez dans 5 minutes.', 30, 'Trop de tentatives. Réessayez dans 1 minute.'],
  ])('CU-171 : « %s » avec %i secondes restantes devient « %s »', async (message, secondes, attendu) => {
    const fixture = TestBed.createComponent(ConnexionPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    soumettre(page);
    http
      .expectOne('/api/backend/connexion')
      .flush({ message, invitation: null, reessayerDansSecondes: secondes }, { status: 429, statusText: 'Too Many Requests' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert] p')?.textContent).toBe(attendu);
  });
});

describe('messageDeRefus (CU-171)', () => {
  it.each([null, undefined, 0, -5, Number.NaN])('sans durée exploitable (%s), garde le message du backend', (secondes) => {
    expect(messageDeRefus({ message: 'Trop de tentatives. Réessayez plus tard.', invitation: null, reessayerDansSecondes: secondes })).toBe(
      'Trop de tentatives. Réessayez plus tard.',
    );
  });

  it('ajoute la consigne à un message qui n’en a pas', () => {
    expect(messageDeRefus({ message: 'Trop de tentatives.', invitation: null, reessayerDansSecondes: 900 })).toBe(
      'Trop de tentatives. Réessayez dans 15 minutes.',
    );
  });

  it('arrondit à la minute supérieure, au moins 1', () => {
    expect(reessayerDans(1)).toBe('Réessayez dans 1 minute.');
    expect(reessayerDans(61)).toBe('Réessayez dans 2 minutes.');
  });
});
