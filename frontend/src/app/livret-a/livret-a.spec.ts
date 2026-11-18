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

  it('appelle les routes /livret-a/identite du backend (CU-28) avec le cookie de session', () => {
    const service = TestBed.inject(LivretAService);
    const coordonnees = {
      voie: '1 rue de la Paix',
      complement: null,
      codePostal: '75002',
      ville: 'Paris',
      paysAdresse: 'France',
      email: 'cliente@example.com',
      mobile: '0612345678',
    };
    service.identite().subscribe();
    service.confirmerIdentite(coordonnees, true).subscribe();
    service.transmettreControle('ci 1', 'CORRESPONDANCE_PEP', 2).subscribe();
    service.simulerValidationResponsable('ci 1').subscribe();

    const identite = http.expectOne({ method: 'GET', url: '/api/backend/livret-a/identite' });
    const confirmer = http.expectOne({ method: 'POST', url: '/api/backend/livret-a/identite' });
    const resultat = http.expectOne({ method: 'POST', url: '/api/backend/livret-a/identite/ci%201/resultat' });
    const responsable = http.expectOne({
      method: 'POST',
      url: '/api/backend/test/livret-a/identite/ci%201/validation-responsable',
    });
    for (const requete of [identite, confirmer, resultat, responsable]) {
      expect(requete.request.withCredentials).toBe(true);
      requete.flush(null);
    }
    expect(confirmer.request.body).toEqual({ coordonnees, residenceFiscaleConfirmee: true });
    expect(resultat.request.body).toEqual({ resultat: 'CORRESPONDANCE_PEP', tentatives: 2 });
  });

  it('appelle les routes /livret-a/piece du backend (CU-29) avec le cookie de session', () => {
    const service = TestBed.inject(LivretAService);
    const nouvelle = { typePiece: 'PASSEPORT' as const, numeroPiece: '18AB12345', dateExpiration: '2036-03-01' };
    service.piece().subscribe();
    service.soumettrePiece(nouvelle).subscribe();
    service.transmettreDocument('pi 1', 'VERIFIE', 1).subscribe();
    service.transmettreSelfie('pi 1', 'CORRESPONDANCE', 96, 2).subscribe();

    const piece = http.expectOne({ method: 'GET', url: '/api/backend/livret-a/piece' });
    const soumettre = http.expectOne({ method: 'POST', url: '/api/backend/livret-a/piece' });
    const documentPiece = http.expectOne({ method: 'POST', url: '/api/backend/livret-a/piece/pi%201/document' });
    const selfie = http.expectOne({ method: 'POST', url: '/api/backend/livret-a/piece/pi%201/selfie' });
    for (const requete of [piece, soumettre, documentPiece, selfie]) {
      expect(requete.request.withCredentials).toBe(true);
      requete.flush(null);
    }
    expect(soumettre.request.body).toEqual(nouvelle);
    expect(documentPiece.request.body).toEqual({ resultat: 'VERIFIE', tentatives: 1 });
    expect(selfie.request.body).toEqual({ resultat: 'CORRESPONDANCE', score: 96, tentatives: 2 });
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
