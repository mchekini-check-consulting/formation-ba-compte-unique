import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MESSAGE_TROP_COURT } from '../mot-de-passe/saisie-mot-de-passe';
import { ActivationPage } from './activation-page';

describe('ActivationPage (CU-14)', () => {
  let fixture: ComponentFixture<ActivationPage>;
  let page: HTMLElement;
  let http: HttpTestingController;

  const creer = async (jeton: string | null) => {
    TestBed.configureTestingModule({
      imports: [ActivationPage],
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
    fixture = TestBed.createComponent(ActivationPage);
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  };
  const ouvrir = async (etat: string) => {
    const requete = http.expectOne('/api/backend/activation/etat');
    expect(requete.request.body).toEqual({ jeton: 'j1' });
    requete.flush({ etat });
    await fixture.whenStable();
  };
  const valider = async (motDePasse: string, confirmation = motDePasse) => {
    page.querySelector<HTMLInputElement>('input[name="motDePasse"]')!.value = motDePasse;
    page.querySelector<HTMLInputElement>('input[name="confirmation"]')!.value = confirmation;
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  afterEach(() => http.verify());

  it("affiche la page « Activer mon espace » sans consommer le lien, puis l'active", async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    expect(page.querySelector('.card h2')?.textContent).toContain('Activer mon espace');
    await valider('Abcdefghij1!');
    const activation = http.expectOne({ method: 'POST', url: '/api/backend/activation' });
    expect(activation.request.body).toEqual({ jeton: 'j1', motDePasse: 'Abcdefghij1!', confirmation: 'Abcdefghij1!' });
    activation.flush({ etat: 'ACTIVE' });
    await fixture.whenStable();
    expect(page.querySelector('.card h2')?.textContent).toContain('Votre espace est activé');
    expect(page.querySelector('a.bouton')?.getAttribute('href')).toBe('/connexion');
  });

  it('refuse un mot de passe de moins de 12 caractères sans appeler le serveur', async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    await valider('Court1!');
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(MESSAGE_TROP_COURT);
  });

  it('affiche le message du serveur quand le mot de passe est refusé, le lien restant valide', async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    await valider('Motdepasse123!');
    http
      .expectOne('/api/backend/activation')
      .flush({ message: 'Ce mot de passe est trop courant', regles: [] }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Ce mot de passe est trop courant');
    expect(page.querySelector('form')).not.toBeNull();
  });

  it('affiche « Lien expiré » et envoie un nouveau lien', async () => {
    await creer('j1');
    await ouvrir('EXPIRE');
    expect(page.querySelector('.card h2')?.textContent).toContain('Lien expiré');
    page.querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();
    const demande = http.expectOne('/api/backend/activation/nouveau-lien');
    expect(demande.request.body).toEqual({ jeton: 'j1' });
    demande.flush({ message: 'Un nouveau lien vous a été envoyé par SMS.', regles: [] }, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')?.textContent).toContain('nouveau lien');
  });

  it('affiche « Votre espace est déjà activé » avec un lien vers la connexion', async () => {
    await creer('j1');
    await ouvrir('DEJA_ACTIVE');
    expect(page.querySelector('.card h2')?.textContent).toContain('Votre espace est déjà activé');
    expect(page.querySelector('a.bouton')?.getAttribute('href')).toBe('/connexion');
  });

  it('bascule vers « Lien expiré » ou « déjà activé » si le serveur le signale à la validation', async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    await valider('Abcdefghij1!');
    http.expectOne('/api/backend/activation').flush({ message: 'Lien expiré' }, { status: 410, statusText: 'Gone' });
    await fixture.whenStable();
    expect(page.querySelector('.card h2')?.textContent).toContain('Lien expiré');
  });

  it('signale un espace déjà activé ou un service indisponible à la validation', async () => {
    await creer('j1');
    await ouvrir('VALIDE');
    await valider('Abcdefghij1!');
    http.expectOne('/api/backend/activation').flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('réessayez plus tard');
    await valider('Abcdefghij1!');
    http.expectOne('/api/backend/activation').flush({}, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(page.querySelector('.card h2')?.textContent).toContain('déjà activé');
  });

  it('considère un lien sans jeton comme expiré, et signale un service indisponible', async () => {
    await creer(null);
    expect(page.querySelector('.card h2')?.textContent).toContain('Lien expiré');
    page.querySelector<HTMLButtonElement>('button')!.click();
    http.expectOne('/api/backend/activation/nouveau-lien').flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')?.textContent).toContain('réessayez plus tard');
  });

  it('signale une vérification du lien impossible', async () => {
    await creer('j1');
    http.expectOne('/api/backend/activation/etat').flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('réessayez plus tard');
  });
});
