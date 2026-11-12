import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BouchonsIdentiteService } from '../identification/bouchons-identite';
import { LivretAService } from './livret-a';

describe('LivretAService (CU-31) et bouchon fiscal (CU-295)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('appelle les routes /livret-a du backend avec le cookie de session', () => {
    const service = TestBed.inject(LivretAService);
    service.etat().subscribe();
    service.demander(true, false).subscribe();
    service.transmettreReponse('ref 1', 'i-1', 'AUCUN_LIVRET_A').subscribe();
    service.simulerExpirationDelai('ref 1').subscribe();

    const etat = http.expectOne({ method: 'GET', url: '/api/backend/livret-a/demande' });
    const demander = http.expectOne({ method: 'POST', url: '/api/backend/livret-a/demandes' });
    const reponse = http.expectOne({ method: 'POST', url: '/api/backend/livret-a/demandes/ref%201/reponse-fiscale' });
    const expiration = http.expectOne({
      method: 'POST',
      url: '/api/backend/test/livret-a/demandes/ref%201/expiration-delai',
    });
    for (const requete of [etat, demander, reponse, expiration]) {
      expect(requete.request.withCredentials).toBe(true);
      requete.flush(null);
    }
    expect(demander.request.body).toEqual({ attestation: true, partageDetailAutorise: false });
    expect(reponse.request.body).toEqual({ interrogationId: 'i-1', resultat: 'AUCUN_LIVRET_A' });
  });

  it('interroge le bouchon de l’administration fiscale via bouchons-identite', () => {
    const interrogation = {
      demandeId: 'ref',
      nom: 'Cliente-A',
      prenoms: 'Test',
      sexe: 'F',
      dateNaissance: '1990-05-14',
      lieuNaissance: 'Paris',
      partageDetailAutorise: true,
      casDeTest: 'DETENU_AILLEURS' as const,
    };
    TestBed.inject(BouchonsIdentiteService).interrogerAdministrationFiscale(interrogation).subscribe();
    const requete = http.expectOne({
      method: 'POST',
      url: '/api/bouchons-identite/administration-fiscale/interrogations',
    });
    expect(requete.request.body).toEqual(interrogation);
    requete.flush(null);
  });
});
