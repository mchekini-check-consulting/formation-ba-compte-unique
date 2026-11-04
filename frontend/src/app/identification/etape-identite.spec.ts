import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnnulationControles, EtapeIdentite } from './etape-identite';
import { IDENTITE_EXEMPLE } from './exemple';
import { Identite, MESSAGE_ANNULATION } from './regles';

const SANCTIONS = '/api/bouchons-identite/sanctions-pep/controles';
const IDENTITE = '/api/backend/dossiers/dossier-1/identite';
const CONTROLES = '/api/backend/dossiers/dossier-1/controles';
const DOSSIER = {
  reference: 'dossier-1',
  statut: 'EN_COURS_DE_VERIFICATION',
  indiceAmericain: false,
  residenceFrancaise: true,
  controleSanctions: null,
  controlePep: null,
  controleLe: null,
};

describe('EtapeIdentite', () => {
  let fixture: ComponentFixture<EtapeIdentite>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let validee: Identite | undefined;
  let annulation: AnnulationControles | undefined;

  const saisir = (identite: Partial<Identite>) => {
    for (const [champ, valeur] of Object.entries(identite)) {
      if (champ === 'nationalite') {
        const radio = page.querySelector<HTMLInputElement>(`input[name=nationalite][value="${valeur}"]`)!;
        radio.checked = true;
        radio.dispatchEvent(new Event('change'));
        continue;
      }
      const element = page.querySelector<HTMLInputElement | HTMLSelectElement>(`#id-${champ}`)!;
      element.value = valeur;
      element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input'));
    }
  };
  const valider = async () => {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  const choisirCas = async (libelle: string) => {
    [...page.querySelectorAll<HTMLButtonElement>('.scenario-grid button')]
      .find((b) => b.textContent?.trim() === libelle)!
      .click();
    await fixture.whenStable();
  };
  /** Le dossier est enregistré, puis le contrôle sanctions/PEP est demandé au bouchon. */
  const enregistrerPuisControler = () => {
    http.expectOne(IDENTITE).flush(DOSSIER);
    return http.expectOne(SANCTIONS);
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EtapeIdentite],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EtapeIdentite);
    fixture.componentRef.setInput('dossierId', 'dossier-1');
    validee = undefined;
    annulation = undefined;
    fixture.componentInstance.valide.subscribe((i) => (validee = i));
    fixture.componentInstance.annule.subscribe((a) => (annulation = a));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  it('affiche les rubriques de la maquette', () => {
    const rubriques = [...page.querySelectorAll('legend')].map((l) => l.textContent);
    expect(rubriques).toEqual(['Identité', 'Adresse postale', 'Contact', "Pièce d'identité"]);
  });

  it('propose les catégories de pays de naissance de CUB-8, États-Unis compris', () => {
    const options = [...page.querySelectorAll('#id-paysNaissance option')].map((o) => o.textContent?.trim());
    expect(options).toEqual(['France', 'Union européenne hors France', 'Hors Union européenne', 'États-Unis']);
  });

  it('fait choisir une seule catégorie de nationalité', () => {
    const radios = [...page.querySelectorAll<HTMLInputElement>('input[name=nationalite]')];
    expect(radios.map((r) => r.parentElement?.textContent?.trim())).toEqual([
      'France',
      'Union européenne hors France',
      'Hors Union européenne',
    ]);
    expect(radios.every((r) => r.type === 'radio')).toBe(true);
    expect(page.textContent).toContain('Une seule catégorie possible');
  });

  it("ne propose pas le passeport et indique la résidence française", () => {
    const types = [...page.querySelectorAll('#id-typePiece option')].map((o) => o.textContent?.trim());
    expect(types).toEqual(["Carte nationale d'identité", 'Titre de séjour']);
    expect(page.textContent).toContain("Le passeport n'est pas accepté.");
    expect(page.textContent).toContain('vous rend résidente française');
  });

  it('bloque la suite et signale les champs manquants, sans appeler les backends', async () => {
    await valider();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Corrigez les champs signalés');
    expect(page.querySelector('#id-nomNaissance')?.getAttribute('aria-invalid')).toBe('true');
    expect(page.textContent).toContain('Nom de naissance obligatoire');
    expect(validee).toBeUndefined();
    http.expectNone(IDENTITE);
    http.expectNone(SANCTIONS);
  });

  it('enregistre le dossier, contrôle sanctions et PEP, enregistre le résultat puis passe à la suite', async () => {
    saisir({ ...IDENTITE_EXEMPLE, paysNaissance: 'ETATS_UNIS', nationalite: 'HORS_UE' });
    await valider();
    const enregistrement = http.expectOne(IDENTITE);
    expect(enregistrement.request.method).toBe('PUT');
    expect(enregistrement.request.body).toEqual({
      ...IDENTITE_EXEMPLE,
      paysNaissance: 'ETATS_UNIS',
      nationalite: 'HORS_UE',
      dateNaissance: '1990-05-14',
      dateExpiration: '2031-06-30',
    });
    enregistrement.flush({ ...DOSSIER, indiceAmericain: true });
    const controle = http.expectOne(SANCTIONS);
    expect(controle.request.body).toEqual({
      nomNaissance: 'Dupont',
      prenoms: 'Marie Claire',
      dateNaissance: '1990-05-14',
      nationalite: 'HORS_UE',
      casDeTest: 'CONFORME',
    });
    controle.flush({ resultat: 'AUCUNE_CORRESPONDANCE', suite: 'POURSUIVRE', statutDossier: 'EN_COURS_DE_VERIFICATION', message: null });
    const resultat = http.expectOne(CONTROLES);
    expect(resultat.request.body).toEqual({ sanctions: 'OK', pep: 'OK' });
    resultat.flush(DOSSIER);
    expect(validee?.paysNaissance).toBe('ETATS_UNIS');
    expect(annulation).toBeUndefined();
  });

  it('annule la demande en cas de correspondance PEP, avec le seul contrôle PEP en échec', async () => {
    saisir(IDENTITE_EXEMPLE);
    await choisirCas('Test PEP');
    await valider();
    const controle = enregistrerPuisControler();
    expect(controle.request.body.casDeTest).toBe('PEP');
    controle.flush({ resultat: 'CORRESPONDANCE_PEP', suite: 'ANNULER', statutDossier: 'ANNULE', message: MESSAGE_ANNULATION });
    const resultat = http.expectOne(CONTROLES);
    expect(resultat.request.body).toEqual({ sanctions: 'OK', pep: 'NOK' });
    resultat.flush({ ...DOSSIER, statut: 'ANNULE' });
    expect(annulation).toEqual({ message: MESSAGE_ANNULATION, controles: { sanctions: 'OK', pep: 'NOK' } });
    expect(validee).toBeUndefined();
  });

  it('annule la demande en cas de sanction, avec le message générique par défaut', async () => {
    saisir(IDENTITE_EXEMPLE);
    await choisirCas('Test Sanctionné');
    await valider();
    enregistrerPuisControler().flush({ resultat: 'CORRESPONDANCE_SANCTIONS', suite: 'ANNULER', statutDossier: 'ANNULE', message: null });
    http.expectOne(CONTROLES).flush({ ...DOSSIER, statut: 'ANNULE' });
    expect(annulation).toEqual({ message: MESSAGE_ANNULATION, controles: { sanctions: 'NOK', pep: 'OK' } });
  });

  it('affiche « Vérification impossible » si le contrôle ne répond pas, sans relance automatique', async () => {
    saisir(IDENTITE_EXEMPLE);
    await choisirCas('Mode indisponible');
    await valider();
    const controle = enregistrerPuisControler();
    expect(controle.request.body.casDeTest).toBe('INDISPONIBLE');
    controle.flush('', { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('h2')?.textContent).toBe('Vérification impossible');
    expect(page.querySelector('.status')?.textContent).toContain('En cours de vérification');
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Vérification impossible, réessayez plus tard');
    http.expectNone(CONTROLES);
    expect(validee).toBeUndefined();
    expect(annulation).toBeUndefined();

    page.querySelector<HTMLButtonElement>('.pbtn.ghost')!.click();
    await fixture.whenStable();
    expect(page.querySelector('h2')?.textContent).toBe('Mon identité');
    expect(page.querySelector<HTMLInputElement>('#id-nomNaissance')?.value).toBe('Dupont');
  });

  it("affiche « Vérification impossible » si le dossier ne peut pas être enregistré", async () => {
    saisir(IDENTITE_EXEMPLE);
    await valider();
    http.expectOne(IDENTITE).flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    http.expectNone(SANCTIONS);
    expect(page.querySelector('h2')?.textContent).toBe('Vérification impossible');
  });

  it('reprend la saisie déjà validée et signale le retour', async () => {
    fixture.componentRef.setInput('initiale', { ...IDENTITE_EXEMPLE, paysNaissance: 'ETATS_UNIS', nationalite: 'UE_HORS_FRANCE', typePiece: 'TITRE_SEJOUR' });
    await fixture.whenStable();
    expect(page.querySelector<HTMLInputElement>('#id-nomNaissance')?.value).toBe('Dupont');
    expect(page.querySelector<HTMLSelectElement>('#id-paysNaissance')?.value).toBe('ETATS_UNIS');
    expect(page.querySelector<HTMLSelectElement>('#id-typePiece')?.value).toBe('TITRE_SEJOUR');
    expect(page.querySelector<HTMLInputElement>('input[name=nationalite]:checked')?.value).toBe('UE_HORS_FRANCE');
    let retour = false;
    fixture.componentInstance.retour.subscribe(() => (retour = true));
    page.querySelector<HTMLButtonElement>('.pbtn.ghost')!.click();
    expect(retour).toBe(true);
  });
});
