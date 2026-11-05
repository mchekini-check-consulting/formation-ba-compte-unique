import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ReinitialisationPage } from './reinitialisation-page';

const CONFORME = 'Abcdefghij1!';

// CU-46 (critères 4 et 5) et complément de l'US-09.
describe('ReinitialisationPage (CU-46)', () => {
  let fixture: ComponentFixture<ReinitialisationPage>;
  let page: HTMLElement;
  let http: HttpTestingController;

  const creer = async (jeton: string | null) => {
    TestBed.configureTestingModule({
      imports: [ReinitialisationPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(jeton ? { jeton } : {}) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReinitialisationPage);
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  };
  const ouvrir = async (etat: string) => {
    const requete = http.expectOne('/api/backend/reinitialisation/etat');
    expect(requete.request.body).toEqual({ jeton: 'j1' });
    requete.flush({ etat });
    await fixture.whenStable();
  };
  const valider = async (motDePasse: string, confirmation: string) => {
    page.querySelector<HTMLInputElement>('input[name="motDePasse"]')!.value = motDePasse;
    page.querySelector<HTMLInputElement>('input[name="confirmation"]')!.value = confirmation;
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  const titre = () => page.querySelector('.card h2')?.textContent ?? '';

  afterEach(() => http.verify());

  it('affiche « Définir mon mot de passe » et enregistre deux saisies identiques', async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    expect(titre()).toContain('Définir mon mot de passe');
    await valider(CONFORME, CONFORME);
    const requete = http.expectOne({ method: 'POST', url: '/api/backend/reinitialisation' });
    expect(requete.request.body).toEqual({ jeton: 'j1', motDePasse: CONFORME, confirmation: CONFORME });
    requete.flush({ etat: 'REINITIALISE' });
    await fixture.whenStable();
    expect(titre()).toContain('Votre mot de passe est modifié');
    expect(page.querySelector('a.bouton')?.getAttribute('href')).toBe('/connexion');
  });

  it("ne contacte pas le serveur si la confirmation diffère, puis accepte la correction avec le même lien", async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    await valider(CONFORME, 'Abcdefghij1?');
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Les deux mots de passe ne correspondent pas');
    http.expectNone('/api/backend/reinitialisation');
    await valider(CONFORME, CONFORME);
    http.expectOne('/api/backend/reinitialisation').flush({ etat: 'REINITIALISE' });
    await fixture.whenStable();
    expect(titre()).toContain('Votre mot de passe est modifié');
  });

  it('affiche le message du serveur et reste sur la page', async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    await valider('Motdepasse123!', 'Motdepasse123!');
    http
      .expectOne('/api/backend/reinitialisation')
      .flush({ message: 'Ce mot de passe est trop courant', regles: [] }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Ce mot de passe est trop courant');
    expect(page.querySelector('form')).not.toBeNull();
  });

  it('affiche « Lien expiré » et un bouton « Recevoir un nouveau lien »', async () => {
    await creer('j1');
    await ouvrir('EXPIRE');
    expect(titre()).toContain('Lien expiré');
    const bouton = page.querySelector('a.bouton');
    expect(bouton?.textContent).toContain('Recevoir un nouveau lien');
    expect(bouton?.getAttribute('href')).toBe('/mot-de-passe-oublie');
  });

  it('bascule vers « Lien expiré » si le serveur le signale à la validation, ou signale une panne', async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    await valider(CONFORME, CONFORME);
    http.expectOne('/api/backend/reinitialisation').flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('réessayez plus tard');
    await valider(CONFORME, CONFORME);
    http.expectOne('/api/backend/reinitialisation').flush({ message: 'Lien expiré' }, { status: 410, statusText: 'Gone' });
    await fixture.whenStable();
    expect(titre()).toContain('Lien expiré');
  });

  it('considère un lien sans jeton comme expiré', async () => {
    await creer(null);
    expect(titre()).toContain('Lien expiré');
  });

  it('signale une vérification du lien impossible', async () => {
    await creer('j1');
    http.expectOne('/api/backend/reinitialisation/etat').flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('réessayez plus tard');
  });
});
