import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ComptesService } from '../comptes/comptes';
import { BouchonsIdentiteService, ReponseFiscale } from '../identification/bouchons-identite';
import { choisirScenario } from '../outils-test/scenarios';
import { SuiviSession } from '../session/suivi-session';
import { ControleIdentite, DemandeLivretA, EtapeQuestions, IdentiteLivretA, LivretAService } from './livret-a';
import {
  ATTESTATION_OBLIGATOIRE,
  CHOIX_OBLIGATOIRE,
  LivretAPage,
  PIECE_EXPIREE,
  SANS_IDENTITE_CONFIRMEE,
  SANS_QUESTIONNAIRE,
  CERTIFICAT_OBLIGATOIRE,
  SUITE_OBLIGATOIRE,
} from './livret-a-page';

const EN_COURS: DemandeLivretA = {
  reference: 'ref-1',
  statut: 'EN_COURS_DE_VERIFICATION',
  message: 'Votre demande est en cours de vérification.',
  attestationLe: '2026-11-12T09:00:00Z',
  partageDetailAutorise: true,
  choixPartageLe: '2026-11-12T09:00:00Z',
  interrogationLe: null,
  reponseFiscale: null,
  retard: false,
  delai: '2 jours ouvrés',
  transmission: { nom: 'Cliente-A', prenoms: 'Test', sexe: 'F', dateNaissance: '1990-05-14', lieuNaissance: 'Paris' },
};
const VALIDEE: DemandeLivretA = {
  ...EN_COURS,
  statut: 'OUVERTURE_VALIDEE',
  message: 'Ouverture validée.',
  interrogationLe: '2026-11-12T09:00:00Z',
  reponseFiscale: 'AUCUN_LIVRET_A',
  transmission: null,
};
const SANS_REPONSE: DemandeLivretA = { ...EN_COURS, interrogationLe: '2026-11-12T09:00:00Z', reponseFiscale: 'SANS_REPONSE' };
const REPONSE: ReponseFiscale = { interrogationId: 'i-1', resultat: 'AUCUN_LIVRET_A', detail: null, message: 'Aucun autre Livret A' };

// CU-28 : identité déjà confirmée, l'éligibilité s'affiche directement.
const IDENTITE: IdentiteLivretA = {
  civilite: 'Mme',
  nom: 'Cliente-A',
  prenoms: 'Test',
  dateNaissance: '1990-05-14',
  coordonnees: { voie: '1 rue de Test', complement: null, codePostal: '75001', ville: 'Paris', paysAdresse: 'France', email: 'a@exemple.fr', mobile: '0600000000' },
  residenceFiscale: 'France',
};
const IDENTITE_VALIDEE: ControleIdentite = { reference: 'ci-1', statut: 'VALIDE', message: 'Identité confirmée.', utilisable: true, aControler: null };
// CU-32 : questions déjà remplies pour cette ouverture, ou encore à remplir (texte à accepter).
const QUESTIONS_REMPLIES: EtapeQuestions = {
  versionTexte: 'V1',
  texte: 'Texte',
  texteAAccepter: false,
  questionnaire: { reference: 'q-1', objetEpargne: 'PRECAUTION', origineFonds: 'SALAIRE_REVENUS', precisionOrigine: null, reponduLe: '2026-11-25T09:00:00Z' },
};
const QUESTIONS_A_REMPLIR: EtapeQuestions = { versionTexte: 'V1', texte: 'Texte', texteAAccepter: true, questionnaire: null };

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

describe('LivretAPage (CU-31)', () => {
  const service = {
    etat: vi.fn(),
    demander: vi.fn(),
    transmettreReponse: vi.fn(),
    simulerExpirationDelai: vi.fn(),
    identite: vi.fn(),
    confirmerIdentite: vi.fn(),
    transmettreControle: vi.fn(),
    simulerValidationResponsable: vi.fn(),
    piece: vi.fn(),
    questions: vi.fn(),
    repondreQuestions: vi.fn(),
    choisirSuite: vi.fn(),
    deposerCertificat: vi.fn(),
    simulerValidationCertificat: vi.fn(),
    simulerRefusCertificat: vi.fn(),
  };
  const PIECE_VALIDE = { requise: false, dateExpirationActuelle: '2035-01-01', verification: null };
  const PIECE_EXPIREE_ETAPE = { requise: true, dateExpirationActuelle: '2024-01-01', verification: null };
  const bouchons = { interrogerAdministrationFiscale: vi.fn() };
  const suivi = {
    ouvrir: vi.fn(() => of({ identifiant: 'ABC', expireDansSecondes: 600 })),
    arreter: vi.fn(),
    restant: signal<number | null>(600),
    deconnecter: vi.fn(),
  };

  beforeEach(async () => {
    localStorage.clear();
    for (const appel of [...Object.values(service), bouchons.interrogerAdministrationFiscale]) {
      appel.mockReset();
    }
    service.etat.mockReturnValue(of({ demande: null }));
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: IDENTITE_VALIDEE }));
    service.piece.mockReturnValue(of(PIECE_VALIDE));
    service.questions.mockReturnValue(of(QUESTIONS_REMPLIES));
    service.demander.mockReturnValue(of(EN_COURS));
    service.transmettreReponse.mockReturnValue(of(VALIDEE));
    bouchons.interrogerAdministrationFiscale.mockReturnValue(of(REPONSE));
    await TestBed.configureTestingModule({
      imports: [LivretAPage],
      providers: [
        provideRouter([]),
        { provide: LivretAService, useValue: service },
        { provide: BouchonsIdentiteService, useValue: bouchons },
        { provide: SuiviSession, useValue: suivi },
        // CU-33 : compte courant lu par le versement initial, quand l'ouverture est validée.
        {
          provide: ComptesService,
          useValue: { mesComptes: vi.fn(() => of([{ id: 1, ibanMasque: '•••• 0105', statut: 'ACTIF', soldeCentimes: 124050, soldeMisAJourLe: null, premierVersementAttendu: false }])) },
        },
      ],
    }).compileComponents();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(LivretAPage);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  async function valider(fixture: { whenStable: () => Promise<unknown>; detectChanges: () => void }, page: HTMLElement) {
    page.querySelector<HTMLButtonElement>('form.eligibilite button[type=submit]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('affiche l’information sur la multidétention, ses risques et le contrôle fiscal', async () => {
    const { page } = await afficher();
    const info = page.querySelector('.info')!.textContent!;
    expect(info).toContain('Un seul Livret A par personne');
    expect(info).toContain('amende de 2 %');
    expect(info).toContain('intérêts du livret en trop sont imposés');
    expect(info).toContain('administration fiscale');
  });

  it('bloque sans attestation, puis sans choix de partage', async () => {
    const { fixture, page } = await afficher();
    await valider(fixture, page);
    expect(page.querySelector('.message')?.textContent).toBe(ATTESTATION_OBLIGATOIRE);
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('.message')?.textContent).toBe(CHOIX_OBLIGATOIRE);
    expect(service.demander).not.toHaveBeenCalled();
  });

  it('enregistre la demande, interroge le bouchon fiscal (réponse choisie sur /test/scenarios) puis transmet sa réponse', async () => {
    choisirScenario('livret-a.fiscal', 'DETENU_AILLEURS');
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=non]')!.click();
    await valider(fixture, page);

    expect(service.demander).toHaveBeenCalledWith(true, false);
    expect(bouchons.interrogerAdministrationFiscale).toHaveBeenCalledWith({
      demandeId: 'ref-1',
      ...EN_COURS.transmission,
      partageDetailAutorise: true,
      casDeTest: 'DETENU_AILLEURS',
    });
    expect(service.transmettreReponse).toHaveBeenCalledWith('ref-1', 'i-1', 'AUCUN_LIVRET_A', null);
    expect(page.querySelector('.statut')?.textContent?.trim()).toBe('Ouverture validée');
    expect(page.querySelector('.texte')?.textContent).toBe('Ouverture validée.');
  });

  it('bouchon injoignable : la réponse transmise est « indisponible »', async () => {
    bouchons.interrogerAdministrationFiscale.mockReturnValue(refus(0, null));
    service.transmettreReponse.mockReturnValue(of({ ...EN_COURS, reponseFiscale: 'INDISPONIBLE' }));
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(service.transmettreReponse).toHaveBeenCalledWith('ref-1', null, 'INDISPONIBLE', null);
    expect(page.querySelector('.statut')?.textContent?.trim()).toBe('En cours de vérification');
    expect(page.textContent).toContain('2 jours ouvrés');
  });

  it('affiche le détail d’un Livret A existant quand la cliente l’a autorisé', async () => {
    bouchons.interrogerAdministrationFiscale.mockReturnValue(
      of({
        ...REPONSE,
        resultat: 'LIVRET_A_EXISTANT',
        detail: { etablissement: 'Banque Exemple', agence: 'Agence Paris Centre', dateOuverture: '2015-03-02' },
      }),
    );
    service.transmettreReponse.mockReturnValue(of({ ...VALIDEE, statut: 'LIVRET_A_EXISTANT', message: 'Existant.' }));
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('.detail')?.textContent).toContain('Banque Exemple, Agence Paris Centre, ouvert le 02/03/2015');
  });

  it('affiche le refus du backend (Livret A déjà détenu) et recharge la demande', async () => {
    service.demander.mockReturnValue(refus(409, { message: 'Vous détenez déjà un Livret A.', termine: true }));
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('.message')?.textContent).toBe('Vous détenez déjà un Livret A.');
    expect(service.etat).toHaveBeenCalledTimes(2);
  });

  it('demande en cours : relance de la vérification, sans bouton de simulation (D4 : sur /test/livret-a)', async () => {
    service.etat.mockReturnValue(of({ demande: SANS_REPONSE }));
    service.transmettreReponse.mockReturnValue(of(SANS_REPONSE));
    const { fixture, page } = await afficher();
    expect(page.querySelector('form.eligibilite')).toBeNull();
    expect(page.textContent).toContain('partage du détail autorisé le 12/11/2026 à 10:00');
    expect(page.textContent).not.toContain('Simuler');

    const boutons = Array.from(page.querySelectorAll<HTMLButtonElement>('button.secondaire'));
    boutons.find((b) => b.textContent?.includes('Relancer'))!.click();
    await fixture.whenStable();
    expect(service.transmettreReponse).toHaveBeenCalledWith('ref-1', 'i-1', 'AUCUN_LIVRET_A', null);
  });

  it('erreur de chargement, et session expirée sans message', async () => {
    service.etat.mockReturnValue(refus(500, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');

    service.etat.mockReturnValue(refus(401, null));
    const autre = await afficher();
    expect(autre.page.textContent).toContain('Chargement');
  });

  // CU-28 : étape « Mon identité » avant l'éligibilité.
  it('sans identité confirmée, l’étape « Mon identité » s’affiche avant l’éligibilité', async () => {
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: null }));
    const { page } = await afficher();
    expect(page.querySelector('app-etape-identite-livret-a h2')?.textContent).toContain('Mon identité');
    expect(page.querySelector('form.eligibilite')).toBeNull();
  });

  it('identité déjà utilisée par une autre ouverture : à confirmer à nouveau', async () => {
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: { ...IDENTITE_VALIDEE, utilisable: false } }));
    const { page } = await afficher();
    expect(page.querySelector('app-etape-identite-livret-a')).not.toBeNull();
  });

  it('identité indisponible : message de chargement en erreur', async () => {
    service.identite.mockReturnValue(refus(500, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('PPE : démarche en attente du responsable, sans bouton de simulation ; validée, l’éligibilité s’ouvre', async () => {
    const attente: ControleIdentite = { ...IDENTITE_VALIDEE, statut: 'ATTENTE_RESPONSABLE', utilisable: false, message: 'En cours d’examen.' };
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: attente }));
    const { page } = await afficher();
    expect(page.querySelector('.identite .message')?.textContent).toBe('En cours d’examen.');
    expect(page.textContent).not.toContain('Simuler');
    expect(page.querySelector('form.eligibilite')).toBeNull();

    // Validation simulée sur /test/livret-a, puis retour sur la page : contrôle validé.
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: IDENTITE_VALIDEE }));
    const retour = await afficher();
    expect(retour.page.querySelector('form.eligibilite')).not.toBeNull();
  });

  it('éligibilité refusée faute d’identité confirmée : retour à l’étape « Mon identité »', async () => {
    service.demander.mockReturnValue(refus(409, { message: SANS_IDENTITE_CONFIRMEE }));
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('app-etape-identite-livret-a')).not.toBeNull();
  });

  // CU-29 : nouvelle pièce et selfie, seulement si la pièce du dossier a expiré.
  it('pièce du dossier encore valide et questions remplies : directement l’éligibilité, étape 3 sur 3', async () => {
    const { page } = await afficher();
    expect(page.querySelector('app-etape-piece-livret-a')).toBeNull();
    expect(page.querySelector('form.eligibilite h2')?.textContent).toContain('Étape 3 sur 3');
  });

  it('pièce du dossier expirée : étape « Ma pièce d’identité » avant l’éligibilité, en 4 étapes', async () => {
    service.piece.mockReturnValue(of(PIECE_EXPIREE_ETAPE));
    const { fixture, page } = await afficher();
    expect(page.querySelector('app-etape-piece-livret-a h2')?.textContent).toContain('Étape 2 sur 4');
    expect(page.querySelector('form.eligibilite')).toBeNull();
    fixture.debugElement.query((e) => e.name === 'app-etape-piece-livret-a').componentInstance.validee.emit();
    fixture.detectChanges();
    expect(page.querySelector('app-etape-piece-livret-a')).toBeNull();
    expect(page.querySelector('form.eligibilite h2')?.textContent).toContain('Étape 4 sur 4');
  });

  it('pièce expirée et identité à confirmer : l’étape identité annonce 4 étapes', async () => {
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: null }));
    service.piece.mockReturnValue(of(PIECE_EXPIREE_ETAPE));
    const { page } = await afficher();
    expect(page.querySelector('app-etape-identite-livret-a h2')?.textContent).toContain('Étape 1 sur 4');
  });

  it('éligibilité refusée car la pièce a expiré : retour à l’étape « Ma pièce d’identité »', async () => {
    service.demander.mockReturnValue(refus(409, { message: PIECE_EXPIREE }));
    const { fixture, page } = await afficher();
    service.piece.mockReturnValue(of(PIECE_EXPIREE_ETAPE));
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('app-etape-piece-livret-a')).not.toBeNull();
  });

  // CU-32 : questions et données, entre la pièce et l'éligibilité.
  it('questions à remplir : étape « Questions et données » avant l’éligibilité, étape 2 sur 3', async () => {
    service.questions.mockReturnValue(of(QUESTIONS_A_REMPLIR));
    const { fixture, page } = await afficher();
    expect(page.querySelector('app-etape-questions-livret-a h2')?.textContent).toContain('Étape 2 sur 3');
    expect(page.querySelector('form.eligibilite')).toBeNull();
    fixture.debugElement.query((e) => e.name === 'app-etape-questions-livret-a').componentInstance.validee.emit();
    fixture.detectChanges();
    expect(page.querySelector('app-etape-questions-livret-a')).toBeNull();
    expect(page.querySelector('form.eligibilite h2')?.textContent).toContain('Étape 3 sur 3');
  });

  it('pièce expirée et questions à remplir : les questions sont l’étape 3 sur 4, après la pièce', async () => {
    service.piece.mockReturnValue(of(PIECE_EXPIREE_ETAPE));
    service.questions.mockReturnValue(of(QUESTIONS_A_REMPLIR));
    const { fixture, page } = await afficher();
    expect(page.querySelector('app-etape-questions-livret-a')).toBeNull();
    fixture.debugElement.query((e) => e.name === 'app-etape-piece-livret-a').componentInstance.validee.emit();
    fixture.detectChanges();
    expect(page.querySelector('app-etape-questions-livret-a h2')?.textContent).toContain('Étape 3 sur 4');
  });

  it('éligibilité refusée faute de questionnaire : retour à l’étape « Questions et données »', async () => {
    service.demander.mockReturnValue(refus(409, { message: SANS_QUESTIONNAIRE }));
    const { fixture, page } = await afficher();
    service.questions.mockReturnValue(of(QUESTIONS_A_REMPLIR));
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('app-etape-questions-livret-a')).not.toBeNull();
    expect(page.querySelector('.livret > .message')?.textContent).toBe(SANS_QUESTIONNAIRE);
  });

  it('questions illisibles au chargement : message d’erreur de chargement', async () => {
    service.questions.mockReturnValue(refus(500, null));
    const { page } = await afficher();
    expect(page.querySelector('form.eligibilite')).toBeNull();
    expect(page.querySelector('app-etape-questions-livret-a')).toBeNull();
  });

  // CU-33 : versement initial seulement quand l'ouverture est validée ; la demande mise à jour est affichée.
  it('ouverture validée : versement initial proposé, demande mise à jour après validation du montant', async () => {
    service.etat.mockReturnValue(of({ demande: VALIDEE }));
    const { fixture, page } = await afficher();
    expect(page.querySelector('app-versement-initial-livret-a')).not.toBeNull();
    const enregistree = { ...VALIDEE, message: 'Versement enregistré.', versementInitialCentimes: 15050 };
    fixture.debugElement.query((e) => e.name === 'app-versement-initial-livret-a').componentInstance.demandeChange.emit(enregistree);
    fixture.detectChanges();
    expect(page.querySelector('.suivi .texte')?.textContent).toBe('Versement enregistré.');
  });

  it('demande en cours de vérification : pas de versement initial', async () => {
    service.etat.mockReturnValue(of({ demande: EN_COURS }));
    const { page } = await afficher();
    expect(page.querySelector('app-versement-initial-livret-a')).toBeNull();
  });

  it('pièce indisponible : message de chargement en erreur', async () => {
    service.piece.mockReturnValue(refus(500, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('D4 : aucun élément de test dans la page (cas des bouchons, simulations, liens /test)', async () => {
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: null }));
    const { page } = await afficher();
    expect(page.querySelector('input[name=cas-sanctions], input[name=cas-fiscal], select[id^=scenario]')).toBeNull();
    expect(page.textContent).not.toContain('Environnement de test');
    expect(page.querySelector('a[href^="/test/"]')).toBeNull();
  });

  it('transmission refusée avec une session expirée : aucun message', async () => {
    service.transmettreReponse.mockReturnValue(refus(401, null));
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('.message')).toBeNull();
  });

  // CU-296 (US-22B) : Livret A déjà détenu dans une autre banque.
  describe('Livret A détenu ailleurs (CU-296)', () => {
    const EXISTANT: DemandeLivretA = {
      ...VALIDEE,
      statut: 'LIVRET_A_EXISTANT',
      message: 'Vous détenez déjà un Livret A.',
      reponseFiscale: 'LIVRET_A_EXISTANT',
      partageDetailAutorise: false,
      detail: null,
      delaiVerification: '48 heures ouvrées',
    };
    const EN_ATTENTE: DemandeLivretA = {
      ...EXISTANT,
      statut: 'EN_ATTENTE_CERTIFICAT',
      message: 'Déposez votre certificat.',
      suiteChoisie: 'CLOTURE_PAR_CLIENTE',
      dateLimiteCertificat: '2027-02-12',
    };
    const A_VERIFIER: DemandeLivretA = {
      ...EN_ATTENTE,
      statut: 'CERTIFICAT_A_VERIFIER',
      message: 'Certificat reçu.',
      certificat: { nomFichier: 'certificat.pdf', deposeLe: '2026-11-25T09:00:00Z', decision: null, motifRefus: null },
    };

    async function rafraichir(fixture: { whenStable: () => Promise<unknown>; detectChanges: () => void }) {
      await fixture.whenStable();
      fixture.detectChanges();
    }

    function deposer(page: HTMLElement, fichier: File) {
      const champ = page.querySelector<HTMLInputElement>('#certificat-fichier')!;
      Object.defineProperty(champ, 'files', { value: [fichier], configurable: true });
      champ.dispatchEvent(new Event('change'));
    }

    it('partage refusé : établissement non communiqué et deux choix, choix obligatoire', async () => {
      service.etat.mockReturnValue(of({ demande: EXISTANT }));
      service.choisirSuite.mockReturnValue(of(EN_ATTENTE));
      const { fixture, page } = await afficher();
      expect(page.querySelector('.detail')?.textContent).toContain('établissement non communiqué');
      expect(page.querySelectorAll('input[name=suite]').length).toBe(2);

      page.querySelector<HTMLButtonElement>('form.choix-suite button')!.click();
      await rafraichir(fixture);
      expect(page.querySelector('.message')?.textContent).toBe(SUITE_OBLIGATOIRE);
      expect(service.choisirSuite).not.toHaveBeenCalled();

      page.querySelector<HTMLInputElement>('input[name=suite][value=CLOTURE_PAR_CLIENTE]')!.click();
      page.querySelector<HTMLButtonElement>('form.choix-suite button')!.click();
      await rafraichir(fixture);
      expect(service.choisirSuite).toHaveBeenCalledWith('ref-1', 'CLOTURE_PAR_CLIENTE');
      expect(page.querySelector('.statut')?.textContent?.trim()).toBe('En attente du certificat de clôture');
      expect(page.querySelector('.limite')?.textContent).toContain('12/02/2027');
    });

    it('partage accepté : détail du livret existant enregistré par le backend', async () => {
      service.etat.mockReturnValue(
        of({
          demande: {
            ...EXISTANT,
            partageDetailAutorise: true,
            detail: { etablissement: 'Banque Exemple', agence: 'Agence Paris Centre', dateOuverture: '2015-03-02' },
          },
        }),
      );
      const { page } = await afficher();
      expect(page.querySelector('.detail')?.textContent).toContain('Banque Exemple, Agence Paris Centre, ouvert le 02/03/2015');
    });

    it('fichier de 3 Mo ou mauvais format refusé avant envoi, puis certificat envoyé', async () => {
      service.etat.mockReturnValue(of({ demande: EN_ATTENTE }));
      service.deposerCertificat.mockReturnValue(of(A_VERIFIER));
      const { fixture, page } = await afficher();

      page.querySelector<HTMLButtonElement>('form.certificat button')!.click();
      await rafraichir(fixture);
      expect(page.querySelector('#err-certificat')?.textContent).toBe(CERTIFICAT_OBLIGATOIRE);

      deposer(page, new File([new Uint8Array(3 * 1024 * 1024)], 'lourd.pdf', { type: 'application/pdf' }));
      await rafraichir(fixture);
      expect(page.querySelector('#err-certificat')?.textContent).toContain('2 Mo maximum');
      deposer(page, new File(['x'], 'certificat.docx', { type: 'application/msword' }));
      await rafraichir(fixture);
      expect(page.querySelector('#err-certificat')?.textContent).toContain('Format non accepté');
      page.querySelector<HTMLButtonElement>('form.certificat button')!.click();
      expect(service.deposerCertificat).not.toHaveBeenCalled();

      const bon = new File(['%PDF'], 'certificat.pdf', { type: 'application/pdf' });
      deposer(page, bon);
      page.querySelector<HTMLButtonElement>('form.certificat button')!.click();
      await rafraichir(fixture);
      expect(service.deposerCertificat).toHaveBeenCalledWith('ref-1', bon);
      expect(page.querySelector('.statut')?.textContent?.trim()).toBe('Certificat à vérifier');
      expect(page.querySelector('.certificat-depose')?.textContent).toContain('48 heures ouvrées');
    });

    it('dépôt refusé par le backend (date limite dépassée) : message et rechargement', async () => {
      service.etat.mockReturnValue(of({ demande: EN_ATTENTE }));
      service.deposerCertificat.mockReturnValue(refus(409, { message: 'Date limite dépassée.', termine: true }));
      const { fixture, page } = await afficher();
      deposer(page, new File(['%PDF'], 'certificat.pdf', { type: 'application/pdf' }));
      page.querySelector<HTMLButtonElement>('form.certificat button')!.click();
      await rafraichir(fixture);
      expect(page.querySelector('.message')?.textContent).toBe('Date limite dépassée.');
      expect(service.etat).toHaveBeenCalledTimes(2);
    });

    it('certificat à vérifier : ni bouton de simulation ni lien de test (D4 : sur /test/livret-a)', async () => {
      service.etat.mockReturnValue(of({ demande: A_VERIFIER }));
      const { page } = await afficher();
      expect(page.textContent).toContain('certificat.pdf');
      expect(page.textContent).not.toContain('Simuler');
      expect(page.querySelector('a[href^="/test/"]')).toBeNull();
    });

    it('demande renoncée : nouvelle demande possible depuis l’étape « Mon identité »', async () => {
      service.etat.mockReturnValue(of({ demande: { ...EXISTANT, statut: 'RENONCEE', message: 'Renoncée.' } }));
      const { fixture, page } = await afficher();
      expect(page.querySelector('.statut')?.textContent?.trim()).toBe('Demande annulée (renonciation)');
      page.querySelector<HTMLButtonElement>('button.nouvelle')!.click();
      await rafraichir(fixture);
      expect(service.identite).toHaveBeenCalled();
      expect(page.querySelector('form.eligibilite')).not.toBeNull();
    });

    it('nouvelle demande : étapes indisponibles, message d’erreur', async () => {
      service.etat.mockReturnValue(of({ demande: { ...EXISTANT, statut: 'ANNULEE', message: 'Annulée.' } }));
      service.identite.mockReturnValue(refus(500, null));
      const { fixture, page } = await afficher();
      page.querySelector<HTMLButtonElement>('button.nouvelle')!.click();
      await rafraichir(fixture);
      expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
    });
  });
});
