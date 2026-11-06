import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
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

  // CU-9 : refus d'une nouvelle ouverture pour une personne déjà connue.
  const refuser = async (refus: object) => {
    saisir(IDENTITE_EXEMPLE);
    await valider();
    http.expectOne(IDENTITE).flush(refus, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    http.expectNone(SANCTIONS);
  };

  it('CU-9 : compte activé, message et lien vers la connexion, sans contrôle', async () => {
    const message = 'Vous avez déjà un espace client. Connectez-vous avec votre identifiant.';
    await refuser({ code: 'ESPACE_EXISTANT', message, reprise: null, conseiller: null });
    expect(page.querySelector('[role=alert]')?.textContent).toBe(message);
    expect(page.querySelector('a')?.getAttribute('href')).toBe('/connexion');
    expect(page.querySelector('button')).toBeNull();
    expect(validee).toBeUndefined();
  });

  it("CU-9 : compte pas encore activé, message d'activation sans lien", async () => {
    const message = 'Votre espace n’est pas encore activé. Ouvrez le lien d’activation reçu par SMS.';
    await refuser({ code: 'ACTIVATION_ATTENDUE', message, reprise: null, conseiller: null });
    expect(page.querySelector('[role=alert]')?.textContent).toBe(message);
    expect(page.querySelector('a, button')).toBeNull();
  });

  it('CU-9 : ouverture annulée par contrôle, message et coordonnées du conseiller, sans motif', async () => {
    await refuser({
      code: 'OUVERTURE_ANNULEE',
      message: 'Votre demande d’ouverture en ligne est annulée. Pour ouvrir un compte à la banque, contactez un conseiller.',
      reprise: null,
      conseiller: 'Service client 01 23 45 67 89',
    });
    expect(page.querySelector('.conseiller')?.textContent).toBe('Service client 01 23 45 67 89');
    expect(page.textContent).not.toContain('Motif');
  });

  it('CU-9 : ouverture en cours, « Reprendre » enregistre la saisie sur le dossier existant et continue', async () => {
    let reprise: unknown;
    fixture.componentInstance.reprise.subscribe((r) => (reprise = r));
    await refuser({
      code: 'OUVERTURE_EN_COURS',
      message: 'Vous avez déjà une ouverture de compte en cours. Reprenez-la.',
      reprise: { reference: 'dossier-existant', etape: 'IDENTITE' },
      conseiller: null,
    });
    expect(page.querySelector('h2')?.textContent).toBe('Ouverture déjà en cours');

    page.querySelector<HTMLButtonElement>('button.pbtn')!.click();
    await fixture.whenStable();

    expect(reprise).toEqual({ reference: 'dossier-existant', etape: 'IDENTITE' });
    http.expectOne('/api/backend/dossiers/dossier-existant/identite').flush({ ...DOSSIER, reference: 'dossier-existant' });
    http.expectOne(SANCTIONS).flush({ resultat: 'AUCUNE_CORRESPONDANCE', suite: 'POURSUIVRE', message: null });
    http.expectOne('/api/backend/dossiers/dossier-existant/controles').flush(DOSSIER);
    await fixture.whenStable();
    expect(validee).toEqual(IDENTITE_EXEMPLE);
  });

  it("CU-9 : un dossier qui attend la banque se reprend au suivi de l'ouverture, sans nouvelle saisie", async () => {
    let reprise: unknown;
    fixture.componentInstance.reprise.subscribe((r) => (reprise = r));
    await refuser({
      code: 'OUVERTURE_EN_COURS',
      message: 'Vous avez déjà une ouverture de compte en cours. Reprenez-la.',
      reprise: { reference: 'dossier-signe', etape: 'OUVERTURE' },
      conseiller: null,
    });
    page.querySelector<HTMLButtonElement>('button.pbtn')!.click();
    await fixture.whenStable();
    expect(reprise).toEqual({ reference: 'dossier-signe', etape: 'OUVERTURE' });
    http.expectNone('/api/backend/dossiers/dossier-signe/identite');
  });

  it('un autre conflit que le doublon reste « Vérification impossible »', async () => {
    saisir(IDENTITE_EXEMPLE);
    await valider();
    http.expectOne(IDENTITE).flush('', { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
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
