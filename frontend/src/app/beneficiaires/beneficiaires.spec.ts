import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BeneficiairesService, refusDe } from './beneficiaires';

const URL = '/api/backend/beneficiaires';

describe('BeneficiairesService (CU-146)', () => {
  let http: HttpTestingController;
  let service: BeneficiairesService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(BeneficiairesService);
  });

  afterEach(() => http.verify());

  it('appelle les routes /beneficiaires du backend avec le cookie de session', () => {
    service.lister().subscribe();
    service.ajouter({ iban: 'FR76', nom: 'Test', libelle: 'Papa' }).subscribe();
    service.confirmer(11, '123456').subscribe();
    service.renommer(5, 'Maman').subscribe();
    service.supprimer(5).subscribe();

    const lister = http.expectOne({ method: 'GET', url: URL });
    const ajouter = http.expectOne({ method: 'POST', url: URL });
    const confirmer = http.expectOne({ method: 'POST', url: `${URL}/ajouts/11/confirmation` });
    const renommer = http.expectOne({ method: 'PATCH', url: `${URL}/5` });
    const supprimer = http.expectOne({ method: 'DELETE', url: `${URL}/5` });
    for (const requete of [lister, ajouter, confirmer, renommer, supprimer]) {
      expect(requete.request.withCredentials).toBe(true);
      requete.flush(null);
    }
    expect(ajouter.request.body).toEqual({ iban: 'FR76', nom: 'Test', libelle: 'Papa' });
    expect(confirmer.request.body).toEqual({ code: '123456' });
    expect(renommer.request.body).toEqual({ libelle: 'Maman' });
  });

  it('donne le message exact du refus du backend, ou un message par défaut', () => {
    const refus = new HttpErrorResponse({ status: 400, error: { message: 'Code incorrect', termine: true } });
    expect(refusDe(refus, 'défaut')).toEqual({ texte: 'Code incorrect', termine: true });
    expect(refusDe(new HttpErrorResponse({ status: 400, error: { message: 'IBAN invalide' } }), 'défaut')).toEqual({
      texte: 'IBAN invalide',
      termine: false,
    });
    expect(refusDe(new HttpErrorResponse({ status: 0 }), 'défaut')).toEqual({ texte: 'défaut', termine: false });
  });
});
