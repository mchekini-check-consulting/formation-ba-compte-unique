import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BouchonsIdentiteService } from '../identification/bouchons-identite';
import {
  CLE_DOSSIER_EN_SIGNATURE,
  OuvertureService,
  dossierEnSignature,
  memoriserDossierEnSignature,
} from './ouverture';

describe('OuvertureService et bouchon Docusign', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('appelle le backend pour la signature et l’ouverture', () => {
    const api = TestBed.inject(OuvertureService);
    api.signature('a b').subscribe();
    http.expectOne({ method: 'GET', url: '/api/backend/dossiers/a%20b/signature' }).flush({});
    api.ouverture('d1').subscribe();
    http.expectOne({ method: 'GET', url: '/api/backend/dossiers/d1/ouverture' }).flush({});
    api.renvoyer('d1', 'EMAIL').subscribe();
    http.expectOne({ method: 'POST', url: '/api/backend/dossiers/d1/envois/EMAIL' }).flush({});
  });

  it('appelle le bouchon Docusign (CU-89)', () => {
    const bouchons = TestBed.inject(BouchonsIdentiteService);
    bouchons.envoyerEnveloppe('d1').subscribe();
    expect(http.expectOne('/api/bouchons-identite/docusign/enveloppes').request.body).toEqual({ dossierId: 'd1' });
    bouchons.saisirCode('e/1', '111111').subscribe();
    expect(http.expectOne('/api/bouchons-identite/docusign/enveloppes/e%2F1/code').request.body).toEqual({
      code: '111111',
    });
    bouchons.annulerSignature('e1').subscribe();
    http.expectOne('/api/bouchons-identite/docusign/enveloppes/e1/annulation').flush({});
  });

  it('garde puis oublie le dossier en cours de signature', () => {
    memoriserDossierEnSignature('d1');
    expect(localStorage.getItem(CLE_DOSSIER_EN_SIGNATURE)).toBe('d1');
    expect(dossierEnSignature()).toBe('d1');
    memoriserDossierEnSignature(null);
    expect(dossierEnSignature()).toBeNull();
  });

  it('se passe du stockage quand le navigateur le refuse', () => {
    const lecture = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('refusé');
    });
    const ecriture = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('refusé');
    });
    expect(() => memoriserDossierEnSignature('d1')).not.toThrow();
    expect(dossierEnSignature()).toBeNull();
    lecture.mockRestore();
    ecriture.mockRestore();
  });
});
