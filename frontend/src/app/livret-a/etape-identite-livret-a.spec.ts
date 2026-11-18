import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { Mock, vi } from 'vitest';
import { BouchonsIdentiteService, ControleSanctionsReponse } from '../identification/bouchons-identite';
import {
  EMAIL_INVALIDE,
  EtapeIdentiteLivretA,
  RESIDENCE_OBLIGATOIRE,
  validerCoordonnees,
} from './etape-identite-livret-a';
import { ControleIdentite, Coordonnees, IdentiteLivretA, LivretAService } from './livret-a';

const COORDONNEES: Coordonnees = {
  voie: '1 rue de Test',
  complement: null,
  codePostal: '75001',
  ville: 'Paris',
  paysAdresse: 'France',
  email: 'cliente@exemple.fr',
  mobile: '0600000000',
};
const IDENTITE: IdentiteLivretA = {
  civilite: 'Mme',
  nom: 'Cliente-A',
  prenoms: 'Test',
  dateNaissance: '1990-05-14',
  coordonnees: COORDONNEES,
  residenceFiscale: 'France',
};
const A_CONTROLER = { nomNaissance: 'Cliente-A', prenoms: 'Test', dateNaissance: '1990-05-14', nationalite: 'FRANCE' };
const EN_COURS: ControleIdentite = { reference: 'ci-1', statut: 'EN_COURS', message: 'En cours.', utilisable: false, aControler: A_CONTROLER };
const vue = (statut: ControleIdentite['statut'], message = `Statut ${statut}`): ControleIdentite => ({
  reference: 'ci-1',
  statut,
  message,
  utilisable: statut === 'VALIDE',
  aControler: null,
});
const reponse = (resultat: ControleSanctionsReponse['resultat']): ControleSanctionsReponse => ({
  resultat,
  suite: 'POURSUIVRE',
  statutDossier: 'EN_COURS_DE_VERIFICATION',
  message: null,
});
const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

// CU-28 (US-19) : étape « Mon identité » de l'ouverture d'un Livret A.
describe('EtapeIdentiteLivretA', () => {
  const service = { confirmerIdentite: vi.fn(), transmettreControle: vi.fn() };
  const bouchons = { controlerSanctions: vi.fn() };
  let fixture: ComponentFixture<EtapeIdentiteLivretA>;
  let page: HTMLElement;
  let validee: Mock<() => void>;
  let controles: ControleIdentite[];

  beforeEach(() => {
    for (const appel of [...Object.values(service), bouchons.controlerSanctions]) {
      appel.mockReset();
    }
    service.confirmerIdentite.mockReturnValue(of(EN_COURS));
    service.transmettreControle.mockReturnValue(of(vue('VALIDE')));
    bouchons.controlerSanctions.mockReturnValue(of(reponse('AUCUNE_CORRESPONDANCE')));
    TestBed.configureTestingModule({
      imports: [EtapeIdentiteLivretA],
      providers: [
        { provide: LivretAService, useValue: service },
        { provide: BouchonsIdentiteService, useValue: bouchons },
      ],
    });
  });

  async function afficher(controle: ControleIdentite | null = null, cas = 'CONFORME') {
    fixture = TestBed.createComponent(EtapeIdentiteLivretA);
    fixture.componentRef.setInput('donnees', { identite: IDENTITE, controle });
    fixture.componentRef.setInput('casSanctions', cas);
    validee = vi.fn<() => void>();
    controles = [];
    fixture.componentInstance.validee.subscribe(validee);
    fixture.componentInstance.controleChange.subscribe((c) => controles.push(c));
    page = fixture.nativeElement;
    await fixture.whenStable();
  }
  const champ = (nom: string) => page.querySelector<HTMLInputElement>(`input[name=${nom}]`)!;
  const saisir = (nom: string, valeur: string) => {
    champ(nom).value = valeur;
    champ(nom).dispatchEvent(new Event('input'));
  };
  const confirmerResidence = () => champ('residence').click();
  const valider = async () => {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  const message = () => page.querySelector('.message')?.textContent ?? '';

  it('pré-remplit l’identité ; nom, prénom et date de naissance ne sont pas modifiables (critères 1 et 2)', async () => {
    await afficher();
    const fige = page.querySelector('.fige')!.textContent!;
    expect(fige).toContain('Cliente-A');
    expect(fige).toContain('Test');
    expect(fige).toContain('14/05/1990');
    const noms = Array.from(page.querySelectorAll<HTMLInputElement>('input')).map((i) => i.name);
    expect(noms).toEqual(['voie', 'complement', 'codePostal', 'ville', 'paysAdresse', 'email', 'mobile', 'residence']);
    expect(champ('email').value).toBe('cliente@exemple.fr');
    expect(champ('voie').value).toBe('1 rue de Test');
    expect(page.textContent).toContain('Résidence fiscale : France');
  });

  it('une coordonnée vidée est signalée et bloque la validation (critère 3)', async () => {
    await afficher();
    confirmerResidence();
    saisir('ville', '  ');
    await valider();
    expect(page.querySelector('#err-ville')?.textContent).toBe('Ville obligatoire.');
    expect(champ('ville').getAttribute('aria-invalid')).toBe('true');
    expect(service.confirmerIdentite).not.toHaveBeenCalled();
  });

  it('un e-mail sans « @ » est signalé comme invalide (critère 4)', async () => {
    await afficher();
    confirmerResidence();
    saisir('email', 'cliente.exemple.fr');
    await valider();
    expect(page.querySelector('#err-email')?.textContent).toBe(EMAIL_INVALIDE);
    expect(service.confirmerIdentite).not.toHaveBeenCalled();
  });

  it('sans confirmation de la résidence fiscale, impossible de continuer (critère 5)', async () => {
    await afficher();
    await valider();
    expect(page.querySelector('#err-residence')?.textContent).toBe(RESIDENCE_OBLIGATOIRE);
    expect(message()).toContain('Vérifiez les champs signalés');
    expect(service.confirmerIdentite).not.toHaveBeenCalled();
  });

  it('identité confirmée : contrôle sanctions/PPE envoyé puis résultat transmis (critère 6)', async () => {
    await afficher(null, 'PEP');
    saisir('complement', '  ');
    saisir('mobile', '0611223344');
    confirmerResidence();
    await valider();
    expect(service.confirmerIdentite).toHaveBeenCalledWith({ ...COORDONNEES, mobile: '0611223344', complement: null }, true);
    expect(bouchons.controlerSanctions).toHaveBeenCalledWith({ ...A_CONTROLER, casDeTest: 'PEP' });
    expect(service.transmettreControle).toHaveBeenCalledWith('ci-1', 'AUCUNE_CORRESPONDANCE', 1);
    expect(validee).toHaveBeenCalled();
    expect(controles.map((c) => c.statut)).toEqual(['EN_COURS', 'VALIDE']);
  });

  it('relance automatiquement jusqu’à 3 fois (critère 7)', async () => {
    bouchons.controlerSanctions
      .mockReturnValueOnce(throwError(() => new Error('délai')))
      .mockReturnValueOnce(throwError(() => new Error('délai')))
      .mockReturnValueOnce(of(reponse('AUCUNE_CORRESPONDANCE')));
    await afficher();
    confirmerResidence();
    await valider();
    expect(bouchons.controlerSanctions).toHaveBeenCalledTimes(3);
    expect(service.transmettreControle).toHaveBeenCalledWith('ci-1', 'AUCUNE_CORRESPONDANCE', 3);
  });

  it('sans réponse après 3 relances : démarche en attente, conseiller alerté (critère 7)', async () => {
    bouchons.controlerSanctions.mockReturnValue(throwError(() => new Error('délai')));
    service.transmettreControle.mockReturnValue(of(vue('EN_ATTENTE', 'En attente, un conseiller a été alerté.')));
    await afficher();
    confirmerResidence();
    await valider();
    expect(bouchons.controlerSanctions).toHaveBeenCalledTimes(4);
    expect(service.transmettreControle).toHaveBeenCalledWith('ci-1', 'SANS_REPONSE', 4);
    expect(message()).toContain('conseiller a été alerté');
    expect(page.querySelector('form')).toBeNull();
    expect(validee).not.toHaveBeenCalled();
  });

  it('gel des avoirs : démarche bloquée sans donner la raison (critère 8)', async () => {
    bouchons.controlerSanctions.mockReturnValue(of(reponse('CORRESPONDANCE_SANCTIONS')));
    service.transmettreControle.mockReturnValue(of(vue('BLOQUE', 'Votre demande ne peut pas aboutir en ligne.')));
    await afficher();
    confirmerResidence();
    await valider();
    expect(service.transmettreControle).toHaveBeenCalledWith('ci-1', 'CORRESPONDANCE_SANCTIONS', 1);
    expect(message()).toBe('Votre demande ne peut pas aboutir en ligne.');
    expect(page.textContent!.toLowerCase()).not.toMatch(/gel|sanction|avoirs/);
    expect(page.querySelector('form')).toBeNull();
  });

  it('PPE : la démarche attend la validation d’un responsable (critère 9)', async () => {
    bouchons.controlerSanctions.mockReturnValue(of(reponse('CORRESPONDANCE_PEP')));
    service.transmettreControle.mockReturnValue(of(vue('ATTENTE_RESPONSABLE', 'En cours d’examen.')));
    await afficher();
    confirmerResidence();
    await valider();
    expect(service.transmettreControle).toHaveBeenCalledWith('ci-1', 'CORRESPONDANCE_PEP', 1);
    expect(message()).toBe('En cours d’examen.');
    expect(controles.at(-1)?.statut).toBe('ATTENTE_RESPONSABLE');
  });

  it('affiche l’essai en cours pendant le contrôle', async () => {
    bouchons.controlerSanctions.mockReturnValue(new Subject());
    await afficher();
    confirmerResidence();
    await valider();
    expect(page.querySelector('.attente')?.textContent).toContain('essai 1 sur 4');
  });

  it('reprend un contrôle resté en cours (page rechargée)', async () => {
    await afficher(EN_COURS);
    expect(bouchons.controlerSanctions).toHaveBeenCalledWith({ ...A_CONTROLER, casDeTest: 'CONFORME' });
    expect(validee).toHaveBeenCalled();
  });

  it('démarche déjà suspendue : message sans formulaire', async () => {
    await afficher(vue('EN_ATTENTE', 'Votre demande est en attente.'));
    expect(message()).toBe('Votre demande est en attente.');
    expect(page.querySelector('form')).toBeNull();
  });

  it('identité déjà confirmée mais utilisée : le formulaire s’affiche à nouveau', async () => {
    await afficher({ ...vue('VALIDE'), utilisable: false });
    expect(page.querySelector('form')).not.toBeNull();
  });

  it('refus du backend : message affiché ; démarche suspendue si terminé', async () => {
    service.confirmerIdentite.mockReturnValue(refus(409, { message: 'Démarche en cours de traitement.', termine: true }));
    await afficher();
    confirmerResidence();
    await valider();
    expect(message()).toBe('Démarche en cours de traitement.');
    expect(page.querySelector('form')).toBeNull();
  });

  it('transmission refusée : message ; session expirée : aucun message', async () => {
    service.transmettreControle.mockReturnValue(refus(500, null));
    await afficher();
    confirmerResidence();
    await valider();
    expect(message()).toContain('momentanément indisponible');

    service.confirmerIdentite.mockReturnValue(refus(401, null));
    await afficher();
    confirmerResidence();
    await valider();
    expect(page.querySelector('.message')).toBeNull();
  });
});

describe('validerCoordonnees (CU-28)', () => {
  it('accepte des coordonnées complètes avec la résidence confirmée', () => {
    expect(validerCoordonnees(COORDONNEES, true)).toEqual({});
  });

  it('signale code postal, mobile et e-mail mal formés', () => {
    const erreurs = validerCoordonnees({ ...COORDONNEES, codePostal: '750', mobile: '0112345678', email: 'a@b' }, true);
    expect(erreurs).toEqual({
      codePostal: 'Code postal à 5 chiffres.',
      mobile: 'Numéro de téléphone mobile invalide.',
      email: EMAIL_INVALIDE,
    });
  });

  it('hors de France, le code postal est libre', () => {
    expect(validerCoordonnees({ ...COORDONNEES, codePostal: 'D-10115', paysAdresse: 'Allemagne' }, true)).toEqual({});
  });

  it('chaque champ obligatoire vide est signalé', () => {
    const vide: Coordonnees = { voie: '', complement: null, codePostal: '', ville: '', paysAdresse: '', email: '', mobile: '' };
    expect(Object.keys(validerCoordonnees(vide, false)).sort()).toEqual(
      ['codePostal', 'email', 'mobile', 'paysAdresse', 'residence', 'ville', 'voie'].sort(),
    );
  });
});
