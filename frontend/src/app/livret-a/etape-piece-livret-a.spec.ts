import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { Mock, vi } from 'vitest';
import { choisirScenario } from '../outils-test/scenarios';
import {
  BouchonsIdentiteService,
  VerificationDocumentReponse,
  VerificationSelfieReponse,
} from '../identification/bouchons-identite';
import {
  EtapePieceLivretA,
  SCENARIOS_PIECE_LIVRET_A,
  SCENARIOS_SELFIE_LIVRET_A,
  TYPES_PIECE_LIVRET_A,
  aujourdhuiIso,
  numeroNormalise,
  pieceAFournir,
  validerPiece,
} from './etape-piece-livret-a';
import { EtapePiece, LivretAService, VerificationPiece } from './livret-a';

const A_VERIFIER = { nomNaissance: 'Cliente-Piece', prenoms: 'Test', dateNaissance: '1990-01-01', dateExpiration: '2036-03-01' };
const vue = (statut: VerificationPiece['statut'], message = `Statut ${statut}`): VerificationPiece => ({
  reference: 'pi-1',
  statut,
  message,
  typePiece: 'PASSEPORT',
  dateExpiration: '2036-03-01',
  aVerifier: statut === 'PIECE_A_VERIFIER' ? A_VERIFIER : null,
});
const document = (statut: VerificationDocumentReponse['statut'], motif: string | null = null): VerificationDocumentReponse => ({
  statut,
  motif,
  suite: 'POURSUIVRE',
  statutDossier: 'EN_COURS_DE_VERIFICATION',
  message: null,
});
const selfie = (resultat: VerificationSelfieReponse['resultat'], score: number | null): VerificationSelfieReponse => ({
  resultat,
  score,
  suite: 'POURSUIVRE',
  statutDossier: 'EN_COURS_DE_VERIFICATION',
  message: null,
});
const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));
const etape = (verification: VerificationPiece | null = null): EtapePiece => ({
  requise: true,
  dateExpirationActuelle: '2024-01-01',
  verification,
});

describe('règles de la nouvelle pièce (CU-29)', () => {
  const saisie = { typePiece: 'PASSEPORT' as const, numeroPiece: '18ab-123 45', dateExpiration: '2036-03-01' };

  it('accepte une pièce complète, en cours de validité, avec un fichier accepté', () => {
    expect(validerPiece(saisie, null, '2026-11-18')).toEqual({});
    expect(numeroNormalise(' 18ab-123 45 ')).toBe('18AB12345');
  });

  it('signale chaque champ manquant ou invalide', () => {
    const erreurs = validerPiece({ typePiece: '', numeroPiece: '12', dateExpiration: '' }, undefined, '2026-11-18');
    expect(Object.keys(erreurs).sort()).toEqual(['dateExpiration', 'fichier', 'numeroPiece', 'typePiece']);
    expect(validerPiece({ ...saisie, dateExpiration: '2026-11-18' }, null, '2026-11-18').dateExpiration).toContain('expirée');
    expect(validerPiece(saisie, 'Format non accepté', '2026-11-18').fichier).toContain('Format non accepté');
  });

  it('propose CNI, passeport et titre de séjour, sans permis de conduire', () => {
    expect(TYPES_PIECE_LIVRET_A.map((t) => t.code)).toEqual(['CARTE_NATIONALE_IDENTITE', 'PASSEPORT', 'TITRE_SEJOUR']);
  });

  it('le cadre de test annonce le résultat attendu de chaque pièce, la première étant vérifiée', () => {
    expect(SCENARIOS_PIECE_LIVRET_A[0]).toEqual({ code: 'PIECE_CONFORME_A_LA_SAISIE', libelle: 'Pièce valide à votre nom : vérifiée' });
    expect(SCENARIOS_PIECE_LIVRET_A.find((p) => p.code === 'PIECE_VALIDE')?.libelle).toContain('rejetée');
  });

  it('propose le selfie de test au seuil de 90 %', () => {
    expect(SCENARIOS_SELFIE_LIVRET_A.map((s) => s.code)).toContain('SCORE_90');
  });

  it('date du jour au format AAAA-MM-JJ', () => {
    expect(aujourdhuiIso(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('pièce à fournir seulement si celle du dossier a expiré et n’est pas encore remplacée', () => {
    expect(pieceAFournir(null)).toBe(false);
    expect(pieceAFournir({ ...etape(), requise: false })).toBe(false);
    expect(pieceAFournir(etape())).toBe(true);
    expect(pieceAFournir(etape(vue('VALIDE')))).toBe(false);
  });
});

// CU-29 (US-20, avec ex-CU-30) : étape « Ma pièce d'identité » de l'ouverture d'un Livret A.
describe('EtapePieceLivretA', () => {
  const service = { soumettrePiece: vi.fn(), transmettreDocument: vi.fn(), transmettreSelfie: vi.fn() };
  const bouchons = { verifierDocument: vi.fn(), verifierSelfie: vi.fn() };
  let fixture: ComponentFixture<EtapePieceLivretA>;
  let page: HTMLElement;
  let validee: Mock<() => void>;

  beforeEach(() => {
    localStorage.clear();
    for (const appel of [...Object.values(service), ...Object.values(bouchons)]) {
      appel.mockReset();
    }
    service.soumettrePiece.mockReturnValue(of(vue('PIECE_A_VERIFIER')));
    service.transmettreDocument.mockReturnValue(of(vue('SELFIE_A_FAIRE', 'Votre pièce est vérifiée.')));
    service.transmettreSelfie.mockReturnValue(of(vue('VALIDE')));
    bouchons.verifierDocument.mockReturnValue(of(document('VERIFIE')));
    bouchons.verifierSelfie.mockReturnValue(of(selfie('CORRESPONDANCE', 96)));
    TestBed.configureTestingModule({
      imports: [EtapePieceLivretA],
      providers: [
        { provide: LivretAService, useValue: service },
        { provide: BouchonsIdentiteService, useValue: bouchons },
      ],
    });
  });

  async function afficher(verification: VerificationPiece | null = null) {
    fixture = TestBed.createComponent(EtapePieceLivretA);
    fixture.componentRef.setInput('donnees', etape(verification));
    validee = vi.fn<() => void>();
    fixture.componentInstance.validee.subscribe(validee);
    page = fixture.nativeElement;
    await fixture.whenStable();
  }
  const element = <T extends HTMLElement>(selecteur: string) => page.querySelector<T>(selecteur)!;
  // D4 : résultats des bouchons choisis sur /test/scenarios (mémorisés), plus dans l'écran.
  const SCENARIOS: Record<string, 'livret-a.piece' | 'livret-a.selfie'> = { 'scenario-piece-livret-a': 'livret-a.piece', 'scenario-selfie-livret-a': 'livret-a.selfie' };
  const choisir = (id: string, valeur: string) => {
    if (SCENARIOS[id]) {
      expect(page.querySelector(`#${id}`)).toBeNull();
      choisirScenario(SCENARIOS[id], valeur);
      return;
    }
    const champ = element<HTMLSelectElement | HTMLInputElement>(`#${id}`);
    champ.value = valeur;
    champ.dispatchEvent(new Event(champ instanceof HTMLSelectElement ? 'change' : 'input'));
  };
  const deposer = (fichier: File) => {
    const champ = element<HTMLInputElement>('#piece-fichier');
    Object.defineProperty(champ, 'files', { value: [fichier], configurable: true });
    champ.dispatchEvent(new Event('change'));
  };
  const remplir = () => {
    choisir('piece-type', 'PASSEPORT');
    choisir('piece-numero', '18ab 12345');
    choisir('piece-expiration', '2036-03-01');
    deposer(new File(['x'], 'passeport.png', { type: 'image/png' }));
  };
  const envoyer = async () => {
    element('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  const verifierSelfie = async () => {
    element<HTMLButtonElement>('.selfie button').click();
    await fixture.whenStable();
  };
  const message = () => page.querySelector('.message')?.textContent ?? '';

  it('indique la date d’expiration de la pièce actuelle et l’étape du parcours', async () => {
    await afficher();
    expect(page.textContent).toContain('a expiré le 01/01/2024');
    expect(page.querySelector('h2')!.textContent).toContain('Étape 2 sur 3');
    const types = Array.from(page.querySelectorAll('#piece-type option')).map((o) => o.textContent);
    expect(types).not.toContain('Permis de conduire');
  });

  it('refuse avant envoi un formulaire incomplet, un fichier trop lourd ou d’un autre format', async () => {
    await afficher();
    await envoyer();
    expect(page.querySelectorAll('.erreur').length).toBe(4);
    expect(service.soumettrePiece).not.toHaveBeenCalled();
    deposer(new File([new Uint8Array(3 * 1024 * 1024)], 'lourd.pdf', { type: 'application/pdf' }));
    await fixture.whenStable();
    expect(element('#err-piece-fichier').textContent).toContain('2 Mo maximum');
    deposer(new File(['x'], 'permis.txt', { type: 'text/plain' }));
    await fixture.whenStable();
    expect(element('#err-piece-fichier').textContent).toContain('Format non accepté');
  });

  it('pièce et selfie vérifiés : la pièce est enregistrée et l’éligibilité peut commencer', async () => {
    await afficher();
    remplir();
    await envoyer();
    expect(service.soumettrePiece).toHaveBeenCalledWith({ typePiece: 'PASSEPORT', numeroPiece: '18AB12345', dateExpiration: '2036-03-01' });
    expect(bouchons.verifierDocument).toHaveBeenCalledWith('pi-1', 'PIECE_CONFORME_A_LA_SAISIE', A_VERIFIER);
    expect(service.transmettreDocument).toHaveBeenCalledWith('pi-1', 'VERIFIE', 1);
    expect(message()).toContain('Votre pièce est vérifiée.');
    expect(page.querySelector('form')).toBeNull();

    choisir('scenario-selfie-livret-a', 'SCORE_90');
    await verifierSelfie();
    expect(bouchons.verifierSelfie).toHaveBeenCalledWith('pi-1', 'SCORE_90');
    expect(service.transmettreSelfie).toHaveBeenCalledWith('pi-1', 'CORRESPONDANCE', 96, 1);
    expect(validee).toHaveBeenCalledTimes(1);
  });

  it('pièce à reprendre : motif précis, la cliente peut recommencer', async () => {
    bouchons.verifierDocument.mockReturnValue(of(document('A_REPRENDRE', 'image illisible : photo floue, reflet ou bords coupés')));
    service.transmettreDocument.mockReturnValue(of(vue('PIECE_A_VERIFIER')));
    await afficher();
    remplir();
    choisir('scenario-piece-livret-a', 'IMAGE_ILLISIBLE');
    await envoyer();
    expect(bouchons.verifierDocument).toHaveBeenCalledWith('pi-1', 'IMAGE_ILLISIBLE', A_VERIFIER);
    expect(service.transmettreDocument).toHaveBeenCalledWith('pi-1', 'A_REPRENDRE', 1);
    expect(message()).toContain('photo floue');
    expect(page.querySelector('form')).not.toBeNull();
  });

  it('pièce rejetée : la demande est arrêtée sans détail de la vérification', async () => {
    bouchons.verifierDocument.mockReturnValue(of(document('REJETE', 'document expiré')));
    service.transmettreDocument.mockReturnValue(of(vue('BLOQUE', 'Votre demande ne peut pas aboutir en ligne.')));
    await afficher();
    remplir();
    await envoyer();
    expect(service.transmettreDocument).toHaveBeenCalledWith('pi-1', 'REJETE', 1);
    expect(message()).toBe('Votre demande ne peut pas aboutir en ligne.');
    expect(page.querySelector('form')).toBeNull();
    expect(validee).not.toHaveBeenCalled();
  });

  it('relance l’analyse jusqu’à 3 fois ; « indisponible » compte comme un essai sans réponse', async () => {
    bouchons.verifierDocument
      .mockReturnValueOnce(throwError(() => new Error('délai')))
      .mockReturnValueOnce(of(document('INDISPONIBLE')))
      .mockReturnValueOnce(of(document('VERIFIE')));
    await afficher();
    remplir();
    await envoyer();
    expect(bouchons.verifierDocument).toHaveBeenCalledTimes(3);
    expect(service.transmettreDocument).toHaveBeenCalledWith('pi-1', 'VERIFIE', 3);
  });

  it('sans réponse après 3 relances : démarche en attente, conseiller alerté', async () => {
    bouchons.verifierDocument.mockReturnValue(throwError(() => new Error('délai')));
    service.transmettreDocument.mockReturnValue(of(vue('EN_ATTENTE', 'Un conseiller a été alerté (48 heures ouvrées).')));
    await afficher();
    remplir();
    await envoyer();
    expect(bouchons.verifierDocument).toHaveBeenCalledTimes(4);
    expect(service.transmettreDocument).toHaveBeenCalledWith('pi-1', 'SANS_REPONSE', 4);
    expect(message()).toContain('48 heures ouvrées');
    expect(page.querySelector('form')).toBeNull();
  });

  it('selfie sous 90 % ou photo d’écran : demande bloquée', async () => {
    bouchons.verifierSelfie.mockReturnValue(of(selfie('NON_CORRESPONDANCE', 89)));
    service.transmettreSelfie.mockReturnValue(of(vue('BLOQUE', 'Contactez un conseiller.')));
    await afficher(vue('SELFIE_A_FAIRE'));
    await verifierSelfie();
    expect(service.transmettreSelfie).toHaveBeenCalledWith('pi-1', 'NON_CORRESPONDANCE', 89, 1);
    expect(message()).toBe('Contactez un conseiller.');
    expect(page.querySelector('.selfie')).toBeNull();
    expect(validee).not.toHaveBeenCalled();
  });

  it('selfie sans réponse après 3 relances : démarche en attente', async () => {
    bouchons.verifierSelfie.mockReturnValue(of(selfie('INDISPONIBILITE_TECHNIQUE', null)));
    service.transmettreSelfie.mockReturnValue(of(vue('EN_ATTENTE', 'En attente.')));
    await afficher(vue('SELFIE_A_FAIRE'));
    await verifierSelfie();
    expect(bouchons.verifierSelfie).toHaveBeenCalledTimes(4);
    expect(service.transmettreSelfie).toHaveBeenCalledWith('pi-1', 'SANS_REPONSE', null, 4);
  });

  it('affiche l’essai en cours pendant la vérification', async () => {
    bouchons.verifierSelfie.mockReturnValue(new Subject());
    await afficher(vue('SELFIE_A_FAIRE'));
    await verifierSelfie();
    expect(page.querySelector('.attente')?.textContent).toContain('essai 1 sur 4');
  });

  it('reprend là où la cliente s’était arrêtée, ou affiche la démarche suspendue', async () => {
    await afficher(vue('SELFIE_A_FAIRE', 'Dernière étape : le selfie.'));
    expect(page.querySelector('.selfie')).not.toBeNull();
    expect(message()).toBe('Dernière étape : le selfie.');
    await afficher(vue('EN_ATTENTE', 'En attente.'));
    expect(page.querySelector('form')).toBeNull();
    expect(message()).toBe('En attente.');
    await afficher(vue('VALIDE'));
    expect(validee).toHaveBeenCalled();
  });

  it('refus du backend : message affiché, démarche suspendue si elle est terminée', async () => {
    service.soumettrePiece.mockReturnValue(refus(409, { message: 'Démarche en cours de traitement.', termine: true }));
    await afficher();
    remplir();
    await envoyer();
    expect(message()).toBe('Démarche en cours de traitement.');
    expect(page.querySelector('form')).toBeNull();
  });

  it('erreur à la transmission d’un résultat : message d’indisponibilité ; 401 sans message', async () => {
    service.transmettreDocument.mockReturnValue(refus(500, null));
    await afficher();
    remplir();
    await envoyer();
    expect(message()).toContain('momentanément indisponible');
    service.soumettrePiece.mockReturnValue(refus(401, null));
    await afficher();
    remplir();
    await envoyer();
    expect(message()).toBe('');
  });
});
