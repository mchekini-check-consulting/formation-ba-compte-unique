import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Observable, of, throwError } from 'rxjs';
import { ConnexionService, SessionEnCours } from '../connexion';
import { connexionPublique, destinationApresConnexion, espaceProtege } from './garde-session';

@Component({ template: '<h1>Mon espace</h1><p class="solde">1 240,50 €</p>' })
class EspaceFactice {}

@Component({ template: '<h1>Me connecter</h1>' })
class ConnexionFactice {}

@Component({ template: '<h1>Mot de passe oublié</h1>' })
class PagePubliqueFactice {}

@Component({ template: '<h1>Ouvrir mon Livret A</h1>' })
class LivretAFactice {}

const SESSION: SessionEnCours = { identifiant: '12345678', expireDansSecondes: 600 };
const refus = (error: unknown = null) =>
  throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized', error }));

// CU-18 : pages protégées et pages publiques.
describe('Garde de session', () => {
  let reponse: Observable<SessionEnCours>;
  let lectures: number;

  beforeEach(() => {
    lectures = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'espace', component: EspaceFactice, canActivate: [espaceProtege] },
          { path: 'connexion', component: ConnexionFactice, canActivate: [connexionPublique] },
          { path: 'mot-de-passe-oublie', component: PagePubliqueFactice },
          { path: 'livret-a', component: LivretAFactice, canActivate: [espaceProtege] },
        ]),
        {
          provide: ConnexionService,
          useValue: {
            session: () => {
              lectures++;
              return reponse;
            },
          },
        },
      ],
    });
  });

  const ouvrir = async (adresse: string) => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(adresse);
    return { harness, url: TestBed.inject(Router).url };
  };

  it("sans session, une page protégée renvoie à la connexion sans rien afficher de l'espace", async () => {
    reponse = refus();
    const { harness, url } = await ouvrir('/espace');
    expect(url).toBe('/connexion');
    expect(harness.routeNativeElement?.textContent).not.toContain('1 240,50');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Me connecter');
  });

  it('avec une session expirée ou révoquée, la connexion affiche « Session expirée »', async () => {
    reponse = refus({ motif: 'EXPIREE' });
    const { url } = await ouvrir('/espace');
    expect(url).toBe('/connexion?motif=expiree');
  });

  it("si le backend ne répond pas, l'espace n'est pas affiché", async () => {
    reponse = throwError(() => new HttpErrorResponse({ status: 0 }));
    const { url } = await ouvrir('/espace');
    expect(url).toBe('/connexion');
  });

  it('avec une session valide, la page protégée est affichée', async () => {
    reponse = of(SESSION);
    const { harness, url } = await ouvrir('/espace');
    expect(url).toBe('/espace');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Mon espace');
  });

  it('déjà connectée, la page de connexion renvoie vers mon espace', async () => {
    reponse = of(SESSION);
    const { url } = await ouvrir('/connexion');
    expect(url).toBe('/espace');
  });

  it('CU-27 : sans session, une autre page protégée est transmise à la connexion', async () => {
    reponse = refus();
    const { url } = await ouvrir('/livret-a');
    expect(url).toBe('/connexion?suite=%2Flivret-a');
  });

  it('CU-27 : session expirée, le motif et la page demandée sont transmis', async () => {
    reponse = refus({ motif: 'EXPIREE' });
    const { url } = await ouvrir('/livret-a');
    expect(url).toBe('/connexion?motif=expiree&suite=%2Flivret-a');
  });

  it('CU-27 : déjà connectée, la connexion renvoie vers la page demandée', async () => {
    reponse = of(SESSION);
    const { harness, url } = await ouvrir('/connexion?suite=%2Flivret-a');
    expect(url).toBe('/livret-a');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Ouvrir mon Livret A');
  });

  it.each([
    [null, '/espace'],
    ['/livret-a', '/livret-a'],
    ['/comptes/5?onglet=2', '/comptes/5?onglet=2'],
    ['https://exemple.com', '/espace'],
    ['//exemple.com', '/espace'],
    ['/\\exemple.com', '/espace'],
    ['/connexion', '/espace'],
    ['', '/espace'],
  ])('CU-27 : après la connexion, « %s » mène à %s', (suite, destination) => {
    expect(destinationApresConnexion(suite)).toBe(destination);
  });

  it("une page publique s'affiche sans session et sans vérification", async () => {
    reponse = refus();
    const { harness, url } = await ouvrir('/mot-de-passe-oublie');
    expect(url).toBe('/mot-de-passe-oublie');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Mot de passe oublié');
    expect(lectures).toBe(0);
  });
});
