import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProfilService, QUESTIONS, reponsesDe } from './profil';

const URL = '/api/backend/profil';

describe('ProfilService (CU-10)', () => {
  let http: HttpTestingController;
  let service: ProfilService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ProfilService);
  });

  afterEach(() => http.verify());

  it('appelle les routes /profil du backend avec le cookie de session', () => {
    const reponses = reponsesDe(new FormData());
    service.lire().subscribe();
    service.valider(reponses, true).subscribe();
    service.versions().subscribe();

    const lire = http.expectOne({ method: 'GET', url: URL });
    const valider = http.expectOne({ method: 'POST', url: URL });
    const versions = http.expectOne({ method: 'GET', url: `${URL}/versions` });
    for (const requete of [lire, valider, versions]) {
      expect(requete.request.withCredentials).toBe(true);
      requete.flush(null);
    }
    expect(valider.request.body).toEqual({ reponses, consentement: true });
  });

  it('convertit les saisies : nombres pour montants, années et TMI, null si vide', () => {
    const formulaire = new FormData();
    formulaire.set('statutMatrimonial', 'PACSE');
    formulaire.set('plafondRetraiteEuros', '52000');
    formulaire.set('tmi', '30');
    formulaire.set('anneeDepartRetraite', 'abc');

    const reponses = reponsesDe(formulaire);

    expect(reponses.statutMatrimonial).toBe('PACSE');
    expect(reponses.plafondRetraiteEuros).toBe(52000);
    expect(reponses.tmi).toBe(30);
    expect(reponses.anneeDepartRetraite).toBeNull();
    expect(reponses.horizon).toBeNull();
    expect(Object.keys(reponses)).toHaveLength(QUESTIONS.length);
  });
});
