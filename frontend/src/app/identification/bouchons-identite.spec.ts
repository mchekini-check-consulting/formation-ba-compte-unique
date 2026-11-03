import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BouchonsIdentiteService, DELAI_SANCTIONS_MS } from './bouchons-identite';

describe('BouchonsIdentiteService', () => {
  let service: BouchonsIdentiteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BouchonsIdentiteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('demande le contrôle sanctions et PEP au backend bouchons-identite', () => {
    const identite = { nomNaissance: 'Test', prenoms: 'Conforme', dateNaissance: '1990-05-14', nationalite: 'Française' };
    let resultat: string | undefined;
    service.controlerSanctions(identite).subscribe((r) => (resultat = r.resultat));
    const requete = http.expectOne({ method: 'POST', url: '/api/bouchons-identite/sanctions-pep/controles' });
    expect(requete.request.body).toEqual(identite);
    requete.flush({ resultat: 'AUCUNE_CORRESPONDANCE', suite: 'POURSUIVRE', statutDossier: 'EN_COURS_DE_VERIFICATION', message: null });
    expect(resultat).toBe('AUCUNE_CORRESPONDANCE');
  });

  it('abandonne le contrôle sanctions après 5 secondes sans réponse', () => {
    vi.useFakeTimers();
    let erreur: unknown;
    service
      .controlerSanctions({ nomNaissance: 'Test', prenoms: 'Indisponible', dateNaissance: '1990-05-14', nationalite: 'Française' })
      .subscribe({ error: (e) => (erreur = e) });
    const requete = http.expectOne('/api/bouchons-identite/sanctions-pep/controles');
    vi.advanceTimersByTime(DELAI_SANCTIONS_MS);
    expect(erreur).toBeDefined();
    expect(requete.cancelled).toBe(true);
  });

  it('envoie le code de la pièce au recto et au verso, avec la saisie', () => {
    const saisie = { nomNaissance: 'Dupont', prenoms: 'Marie Claire', dateNaissance: '1990-05-14', dateExpiration: '2031-06-30' };
    service.verifierDocument('dossier-1', 'PIECE_VALIDE', saisie).subscribe();
    const requete = http.expectOne({ method: 'POST', url: '/api/bouchons-identite/stripe-identity/verifications-document' });
    expect(requete.request.body).toEqual({ dossierId: 'dossier-1', recto: 'PIECE_VALIDE', verso: 'PIECE_VALIDE', saisie });
    requete.flush({});
  });

  it('envoie le code du selfie', () => {
    service.verifierSelfie('dossier-1', 'SELFIE_CONFORME').subscribe();
    const requete = http.expectOne({ method: 'POST', url: '/api/bouchons-identite/stripe-identity/verifications-selfie' });
    expect(requete.request.body).toEqual({ dossierId: 'dossier-1', selfie: 'SELFIE_CONFORME' });
    requete.flush({});
  });
});
