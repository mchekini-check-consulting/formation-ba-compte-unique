import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { VirementsService } from './virements';

describe('VirementsService (CU-147)', () => {
  let http: HttpTestingController;
  let service: VirementsService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(VirementsService);
  });

  afterEach(() => http.verify());

  it('prépare puis signe un virement avec le cookie de session', () => {
    service.preparer({ beneficiaireId: 8, montant: '12,50', libelle: 'Loyer' }).subscribe();
    service.signer(21, '123456').subscribe();

    const preparer = http.expectOne({ method: 'POST', url: '/api/backend/virements' });
    expect(preparer.request.withCredentials).toBe(true);
    expect(preparer.request.body).toEqual({ beneficiaireId: 8, montant: '12,50', libelle: 'Loyer' });
    preparer.flush({});
    const signer = http.expectOne({ method: 'POST', url: '/api/backend/virements/21/signature' });
    expect(signer.request.withCredentials).toBe(true);
    expect(signer.request.body).toEqual({ code: '123456' });
    signer.flush({});
  });

  it('simule un refus de la banque destinataire (bouchon de test)', () => {
    service.simulerRefus(21, 'Compte clôturé').subscribe();

    const refus = http.expectOne({ method: 'POST', url: '/api/backend/test/virements/21/refus' });
    expect(refus.request.body).toEqual({ motif: 'Compte clôturé' });
    refus.flush({});
  });
});
