import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { VirementsTestService, montantEnCentimes } from './virements-test';

describe('montantEnCentimes', () => {
  it.each([
    ['12,50', 1250],
    ['12.5', 1250],
    ['12', 1200],
    ['0,01', 1],
    ['1 000', 100000],
    ['1 000,99', 100099],
    [' 7 ', 700],
  ])('« %s » vaut %i centimes', (saisie, centimes) => {
    expect(montantEnCentimes(saisie)).toBe(centimes);
  });

  it.each(['', '0', '0,00', '-5', '12,345', 'abc', '12,', ',5', '1e3', '12,5,0'])(
    '« %s » est un montant invalide',
    (saisie) => {
      expect(montantEnCentimes(saisie)).toBeNull();
    },
  );
});

describe('VirementsTestService', () => {
  let http: HttpTestingController;
  let service: VirementsTestService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(VirementsTestService);
  });

  afterEach(() => http.verify());

  it('le bouchon est disponible quand le backend répond', async () => {
    const disponible = firstValueFrom(service.disponible());
    http.expectOne('/api/backend/test/virements-entrants').flush(null, { status: 204, statusText: 'No Content' });
    expect(await disponible).toBe(true);
  });

  it("le bouchon n'est pas disponible en preprod et prod (404)", async () => {
    const disponible = firstValueFrom(service.disponible());
    http.expectOne('/api/backend/test/virements-entrants').flush(null, { status: 404, statusText: 'Not Found' });
    expect(await disponible).toBe(false);
  });

  it('envoie le virement simulé au backend', async () => {
    const virement = { iban: 'FR76', montantCentimes: 1250, emetteur: 'Emetteur', libelle: 'Libellé' };
    const resultat = firstValueFrom(service.simuler(virement));
    const requete = http.expectOne('/api/backend/test/virements-entrants');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual(virement);
    requete.flush({ resultat: 'CREDITE', message: 'Virement reçu' });
    expect(await resultat).toEqual({ resultat: 'CREDITE', message: 'Virement reçu' });
  });
});
