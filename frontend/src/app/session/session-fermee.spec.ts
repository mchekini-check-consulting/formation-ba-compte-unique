import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { sessionFermee } from './session-fermee';

const NON_CONNECTEE = { status: 401, statusText: 'Unauthorized' };

// CU-169 : un 401 du backend renvoie vers la connexion, avec « Session expirée » si la session
// présentée a été fermée (expirée ou révoquée), sans message après une déconnexion volontaire.
describe('Intercepteur sessionFermee', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let navigation: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([sessionFermee])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  const appeler = (url: string) => {
    let erreur = 0;
    client.get(url).subscribe({ error: (e) => (erreur = e.status) });
    return () => erreur;
  };

  it('session révoquée ou expirée : connexion avec le motif « session_expiree »', () => {
    const statut = appeler('/api/backend/comptes');
    http.expectOne('/api/backend/comptes').flush({ motif: 'EXPIREE' }, NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], { queryParams: { motif: 'session_expiree' } });
    expect(statut()).toBe(401);
  });

  it('sans cookie de session (déconnexion volontaire) : connexion sans message', () => {
    appeler('/api/backend/comptes/5');
    http.expectOne('/api/backend/comptes/5').flush('', NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
  });

  it.each(['/api/backend/connexion', '/api/backend/connexion/session', '/api/backend/connexion/session/activite'])(
    "le 401 de %s reste traité par l'écran (identifiants refusés, gardes, suivi)",
    (url) => {
      appeler(url);
      http.expectOne(url).flush({ motif: 'EXPIREE' }, NON_CONNECTEE);
      expect(navigation).not.toHaveBeenCalled();
    },
  );

  it("les autres erreurs ne renvoient pas vers la connexion", () => {
    appeler('/api/backend/comptes');
    http.expectOne('/api/backend/comptes').flush('', { status: 500, statusText: 'Erreur' });
    expect(navigation).not.toHaveBeenCalled();
  });
});
