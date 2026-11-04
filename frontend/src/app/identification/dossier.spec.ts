import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DossierService, controlesDe } from './dossier';
import { IDENTITE_EXEMPLE } from './exemple';

describe('DossierService', () => {
  let service: DossierService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DossierService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("enregistre l'identité dans le backend, dates au format AAAA-MM-JJ", () => {
    let statut: string | undefined;
    service.enregistrerIdentite('a b', IDENTITE_EXEMPLE).subscribe((d) => (statut = d.statut));
    const requete = http.expectOne({ method: 'PUT', url: '/api/backend/dossiers/a%20b/identite' });
    expect(requete.request.body).toEqual({
      ...IDENTITE_EXEMPLE,
      dateNaissance: '1990-05-14',
      dateExpiration: '2031-06-30',
    });
    requete.flush({ statut: 'EN_COURS_DE_VERIFICATION' });
    expect(statut).toBe('EN_COURS_DE_VERIFICATION');
  });

  it('enregistre le résultat des contrôles sanctions et PEP', () => {
    service.enregistrerControles('d1', { sanctions: 'OK', pep: 'NOK' }).subscribe();
    const requete = http.expectOne({ method: 'PUT', url: '/api/backend/dossiers/d1/controles' });
    expect(requete.request.body).toEqual({ sanctions: 'OK', pep: 'NOK' });
    requete.flush({});
  });

  it('ne met en échec que le contrôle en correspondance', () => {
    expect(controlesDe('AUCUNE_CORRESPONDANCE')).toEqual({ sanctions: 'OK', pep: 'OK' });
    expect(controlesDe('CORRESPONDANCE_SANCTIONS')).toEqual({ sanctions: 'NOK', pep: 'OK' });
    expect(controlesDe('CORRESPONDANCE_PEP')).toEqual({ sanctions: 'OK', pep: 'NOK' });
  });
});
