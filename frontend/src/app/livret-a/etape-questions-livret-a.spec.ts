import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Mock, vi } from 'vitest';
import {
  EtapeQuestionsLivretA,
  OBJETS,
  OBJET_OBLIGATOIRE,
  ORIGINES,
  ORIGINE_OBLIGATOIRE,
  PRECISION_OBLIGATOIRE,
  PRECISION_TROP_LONGUE,
  SaisieQuestions,
  TEXTE_REFUSE,
  validerQuestions,
} from './etape-questions-livret-a';
import { EtapeQuestions, LivretAService, Questionnaire } from './livret-a';

const A_REMPLIR: EtapeQuestions = {
  versionTexte: 'V1',
  texte: 'Hi-Tech Bank transmet à l’administration fiscale vos nom, prénom, sexe, date et lieu de naissance.',
  texteAAccepter: true,
  questionnaire: null,
};
const TEXTE_DEJA_ACCEPTE: EtapeQuestions = { ...A_REMPLIR, texteAAccepter: false };
const ENREGISTRE: Questionnaire = {
  reference: 'q-1',
  objetEpargne: 'PRECAUTION',
  origineFonds: 'SALAIRE_REVENUS',
  precisionOrigine: null,
  reponduLe: '2026-11-25T09:00:00Z',
};
const COMPLET: SaisieQuestions = { objetEpargne: 'PROJET', origineFonds: 'VENTE_BIEN', precisionOrigine: '', texteAccepte: true };

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

describe('validerQuestions (CU-32)', () => {
  it('propose les choix validés par la BA', () => {
    expect(OBJETS.map((o) => o.code)).toEqual(['PRECAUTION', 'PROJET', 'AUTRE']);
    expect(ORIGINES.map((o) => o.code)).toEqual(['SALAIRE_REVENUS', 'EPARGNE_EXISTANTE', 'HERITAGE_DONATION', 'VENTE_BIEN', 'AUTRE']);
  });

  it('questionnaire complet et texte accepté : aucune erreur', () => {
    expect(validerQuestions(COMPLET, true)).toEqual({});
  });

  it('objet ou origine manquant : bloqué', () => {
    expect(validerQuestions({ ...COMPLET, objetEpargne: '', origineFonds: '' }, true)).toEqual({
      objetEpargne: OBJET_OBLIGATOIRE,
      origineFonds: ORIGINE_OBLIGATOIRE,
    });
  });

  it('origine « Autre » : précision obligatoire, 200 caractères au plus', () => {
    expect(validerQuestions({ ...COMPLET, origineFonds: 'AUTRE', precisionOrigine: '  ' }, true).precisionOrigine).toBe(
      PRECISION_OBLIGATOIRE,
    );
    expect(validerQuestions({ ...COMPLET, origineFonds: 'AUTRE', precisionOrigine: 'x'.repeat(201) }, true).precisionOrigine).toBe(
      PRECISION_TROP_LONGUE,
    );
    expect(validerQuestions({ ...COMPLET, origineFonds: 'AUTRE', precisionOrigine: 'x'.repeat(200) }, true)).toEqual({});
  });

  it('texte refusé : bloqué seulement s’il est à accepter', () => {
    expect(validerQuestions({ ...COMPLET, texteAccepte: false }, true).texte).toBe(TEXTE_REFUSE);
    expect(validerQuestions({ ...COMPLET, texteAccepte: false }, false)).toEqual({});
  });
});

// CU-32 (US-23) : étape « Questions et données » de l'ouverture d'un Livret A.
describe('EtapeQuestionsLivretA', () => {
  const service = { repondreQuestions: vi.fn() };
  let fixture: ComponentFixture<EtapeQuestionsLivretA>;
  let page: HTMLElement;
  let validee: Mock<() => void>;

  beforeEach(() => {
    service.repondreQuestions.mockReset();
    service.repondreQuestions.mockReturnValue(of(ENREGISTRE));
    TestBed.configureTestingModule({
      imports: [EtapeQuestionsLivretA],
      providers: [{ provide: LivretAService, useValue: service }],
    });
  });

  async function afficher(donnees: EtapeQuestions = A_REMPLIR) {
    fixture = TestBed.createComponent(EtapeQuestionsLivretA);
    fixture.componentRef.setInput('donnees', donnees);
    fixture.componentRef.setInput('numero', 2);
    fixture.componentRef.setInput('total', 3);
    validee = vi.fn<() => void>();
    fixture.componentInstance.validee.subscribe(validee);
    page = fixture.nativeElement;
    await fixture.whenStable();
  }

  const choisir = async (nom: string, valeur: string) => {
    page.querySelector<HTMLInputElement>(`input[name=${nom}][value=${valeur}]`)!.click();
    await fixture.whenStable();
  };
  const accepterTexte = async () => {
    page.querySelector<HTMLInputElement>('input[name=texteAccepte]')!.click();
    await fixture.whenStable();
  };
  const valider = async () => {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  const message = () => page.querySelector('.message')?.textContent ?? '';

  it('affiche le texte d’information avec sa version et le numéro d’étape', async () => {
    await afficher();
    expect(page.querySelector('h2')?.textContent).toContain('Étape 2 sur 3');
    expect(page.querySelector('.texte')?.textContent).toContain('administration fiscale');
    expect(page.querySelector('.texte')?.textContent).toContain('version V1');
  });

  it('questionnaire incomplet : bloqué, rien n’est envoyé', async () => {
    await afficher();
    await valider();
    expect(page.textContent).toContain(OBJET_OBLIGATOIRE);
    expect(page.textContent).toContain(ORIGINE_OBLIGATOIRE);
    expect(message()).toBe(TEXTE_REFUSE);
    expect(service.repondreQuestions).not.toHaveBeenCalled();
  });

  it('origine « Autre » : champ de précision affiché et obligatoire', async () => {
    await afficher();
    expect(page.querySelector('textarea')).toBeNull();
    await choisir('objetEpargne', 'PRECAUTION');
    await choisir('origineFonds', 'AUTRE');
    expect(page.querySelector('textarea')).not.toBeNull();
    await accepterTexte();
    await valider();
    expect(page.textContent).toContain(PRECISION_OBLIGATOIRE);
    expect(message()).toBe('Vérifiez les réponses signalées.');

    const precision = page.querySelector<HTMLTextAreaElement>('textarea')!;
    precision.value = '  Vente de parts sociales ';
    precision.dispatchEvent(new Event('input'));
    await valider();
    expect(service.repondreQuestions).toHaveBeenCalledWith({
      objetEpargne: 'PRECAUTION',
      origineFonds: 'AUTRE',
      precisionOrigine: 'Vente de parts sociales',
      texteAccepte: true,
    });
    expect(validee).toHaveBeenCalled();
  });

  it('texte refusé : message, la cliente ne peut pas continuer', async () => {
    await afficher();
    await choisir('objetEpargne', 'PROJET');
    await choisir('origineFonds', 'SALAIRE_REVENUS');
    await valider();
    expect(message()).toBe(TEXTE_REFUSE);
    expect(service.repondreQuestions).not.toHaveBeenCalled();
    expect(validee).not.toHaveBeenCalled();
  });

  it('texte déjà accepté dans sa version en vigueur : seul le questionnaire est posé', async () => {
    await afficher(TEXTE_DEJA_ACCEPTE);
    expect(page.querySelector('.texte')).toBeNull();
    await choisir('objetEpargne', 'AUTRE');
    await choisir('origineFonds', 'HERITAGE_DONATION');
    await valider();
    expect(service.repondreQuestions).toHaveBeenCalledWith({
      objetEpargne: 'AUTRE',
      origineFonds: 'HERITAGE_DONATION',
      precisionOrigine: null,
      texteAccepte: false,
    });
    expect(validee).toHaveBeenCalled();
  });

  it('refus du backend : son message est affiché ; 401 sans message', async () => {
    service.repondreQuestions.mockReturnValue(refus(409, { message: 'Confirmez d’abord votre identité.' }));
    await afficher(TEXTE_DEJA_ACCEPTE);
    await choisir('objetEpargne', 'PROJET');
    await choisir('origineFonds', 'EPARGNE_EXISTANTE');
    await valider();
    expect(message()).toBe('Confirmez d’abord votre identité.');
    expect(validee).not.toHaveBeenCalled();

    service.repondreQuestions.mockReturnValue(refus(401, null));
    fixture.componentInstance['message'].set(null);
    await valider();
    expect(message()).toBe('');
  });
});
