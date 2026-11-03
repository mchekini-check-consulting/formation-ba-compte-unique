import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EtapeIdentite } from './etape-identite';
import { IDENTITE_EXEMPLE } from './exemple';
import { Identite, MESSAGE_ANNULATION } from './regles';

const URL = '/api/bouchons-identite/sanctions-pep/controles';

describe('EtapeIdentite', () => {
  let fixture: ComponentFixture<EtapeIdentite>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let validee: Identite | undefined;
  let annulation: string | undefined;

  const saisir = (identite: Partial<Identite>) => {
    for (const [champ, valeur] of Object.entries(identite)) {
      const element = page.querySelector<HTMLInputElement | HTMLSelectElement>(`#id-${champ}`)!;
      element.value = valeur;
      element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input'));
    }
  };
  const valider = async () => {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EtapeIdentite],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EtapeIdentite);
    validee = undefined;
    annulation = undefined;
    fixture.componentInstance.valide.subscribe((i) => (validee = i));
    fixture.componentInstance.annule.subscribe((m) => (annulation = m));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  it('affiche les rubriques de la maquette', () => {
    const rubriques = [...page.querySelectorAll('legend')].map((l) => l.textContent);
    expect(rubriques).toEqual(['Identité', 'Adresse postale', 'Contact', "Pièce d'identité"]);
  });

  it("bloque la suite et signale les champs manquants, sans appeler le backend", async () => {
    await valider();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Corrigez les champs signalés');
    expect(page.querySelector('#id-nomNaissance')?.getAttribute('aria-invalid')).toBe('true');
    expect(page.textContent).toContain('Nom de naissance obligatoire');
    expect(validee).toBeUndefined();
    http.expectNone(URL);
  });

  it('contrôle sanctions et PEP puis passe à la suite', async () => {
    saisir(IDENTITE_EXEMPLE);
    await valider();
    const requete = http.expectOne(URL);
    expect(requete.request.body).toEqual({
      nomNaissance: 'Dupont',
      prenoms: 'Marie Claire',
      dateNaissance: '1990-05-14',
      nationalite: 'Française',
    });
    requete.flush({ resultat: 'AUCUNE_CORRESPONDANCE', suite: 'POURSUIVRE', statutDossier: 'EN_COURS_DE_VERIFICATION', message: null });
    expect(validee).toEqual(IDENTITE_EXEMPLE);
  });

  it('annule la demande en cas de correspondance sanctions ou PEP', async () => {
    saisir({ ...IDENTITE_EXEMPLE, prenoms: 'Test', nomNaissance: 'PEP' });
    await valider();
    http.expectOne(URL).flush({
      resultat: 'CORRESPONDANCE_PEP',
      suite: 'ANNULER',
      statutDossier: 'ANNULE',
      message: MESSAGE_ANNULATION,
    });
    expect(annulation).toBe(MESSAGE_ANNULATION);
    expect(validee).toBeUndefined();
  });

  it('indique une vérification impossible si le contrôle ne répond pas', async () => {
    saisir(IDENTITE_EXEMPLE);
    await valider();
    http.expectOne(URL).flush('', { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Vérification impossible, réessayez plus tard');
    expect(validee).toBeUndefined();
    expect(annulation).toBeUndefined();
  });

  it('reprend la saisie déjà validée et signale le retour', async () => {
    fixture.componentRef.setInput('initiale', IDENTITE_EXEMPLE);
    await fixture.whenStable();
    expect(page.querySelector<HTMLInputElement>('#id-nomNaissance')?.value).toBe('Dupont');
    let retour = false;
    fixture.componentInstance.retour.subscribe(() => (retour = true));
    page.querySelector<HTMLButtonElement>('.pbtn.ghost')!.click();
    expect(retour).toBe(true);
  });
});
