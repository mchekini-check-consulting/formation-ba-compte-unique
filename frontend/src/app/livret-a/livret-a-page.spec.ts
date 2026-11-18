import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { BouchonsIdentiteService, ReponseFiscale } from '../identification/bouchons-identite';
import { SuiviSession } from '../session/suivi-session';
import { ControleIdentite, DemandeLivretA, IdentiteLivretA, LivretAService } from './livret-a';
import { ATTESTATION_OBLIGATOIRE, CAS_SANCTIONS, CHOIX_OBLIGATOIRE, LivretAPage, SANS_IDENTITE_CONFIRMEE } from './livret-a-page';

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
  };
  const bouchons = { interrogerAdministrationFiscale: vi.fn() };
  const suivi = {
    ouvrir: vi.fn(() => of({ identifiant: 'ABC', expireDansSecondes: 600 })),
    arreter: vi.fn(),
    restant: signal<number | null>(600),
    deconnecter: vi.fn(),
  };

  beforeEach(async () => {
    for (const appel of [...Object.values(service), bouchons.interrogerAdministrationFiscale]) {
      appel.mockReset();
    }
    service.etat.mockReturnValue(of({ demande: null }));
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: IDENTITE_VALIDEE }));
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

  it('enregistre la demande, interroge le bouchon fiscal puis transmet sa réponse au backend', async () => {
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=non]')!.click();
    page.querySelector<HTMLInputElement>('input[name=cas-fiscal][value=DETENU_AILLEURS]')!.click();
    await valider(fixture, page);

    expect(service.demander).toHaveBeenCalledWith(true, false);
    expect(bouchons.interrogerAdministrationFiscale).toHaveBeenCalledWith({
      demandeId: 'ref-1',
      ...EN_COURS.transmission,
      partageDetailAutorise: true,
      casDeTest: 'DETENU_AILLEURS',
    });
    expect(service.transmettreReponse).toHaveBeenCalledWith('ref-1', 'i-1', 'AUCUN_LIVRET_A');
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
    expect(service.transmettreReponse).toHaveBeenCalledWith('ref-1', null, 'INDISPONIBLE');
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

  it('demande en cours : relance de la vérification et expiration simulée du délai', async () => {
    service.etat.mockReturnValue(of({ demande: SANS_REPONSE }));
    service.transmettreReponse.mockReturnValue(of(SANS_REPONSE));
    service.simulerExpirationDelai.mockReturnValue(of({ ...SANS_REPONSE, retard: true }));
    const { fixture, page } = await afficher();
    expect(page.querySelector('form.eligibilite')).toBeNull();
    expect(page.textContent).toContain('partage du détail autorisé le 12/11/2026 à 10:00');

    const boutons = Array.from(page.querySelectorAll<HTMLButtonElement>('button.secondaire'));
    boutons.find((b) => b.textContent?.includes('Relancer'))!.click();
    await fixture.whenStable();
    expect(service.transmettreReponse).toHaveBeenCalledWith('ref-1', 'i-1', 'AUCUN_LIVRET_A');

    fixture.detectChanges();
    Array.from(page.querySelectorAll<HTMLButtonElement>('button.secondaire'))
      .find((b) => b.textContent?.includes('expiration'))!
      .click();
    await fixture.whenStable();
    expect(service.simulerExpirationDelai).toHaveBeenCalledWith('ref-1');
  });

  it('erreur de chargement, et session expirée sans message', async () => {
    service.etat.mockReturnValue(refus(500, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');

    service.etat.mockReturnValue(refus(401, null));
    const autre = await afficher();
    expect(autre.page.textContent).toContain('Chargement');
  });

  it('message par défaut quand l’expiration simulée échoue sans message', async () => {
    service.etat.mockReturnValue(of({ demande: SANS_REPONSE }));
    service.simulerExpirationDelai.mockReturnValue(refus(404, null));
    const { fixture, page } = await afficher();
    Array.from(page.querySelectorAll<HTMLButtonElement>('button.secondaire'))
      .find((b) => b.textContent?.includes('expiration'))!
      .click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.querySelector('.message')?.textContent).toContain('momentanément indisponible');
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

  it('PPE : « Simuler la validation du responsable » ouvre l’éligibilité', async () => {
    const attente: ControleIdentite = { ...IDENTITE_VALIDEE, statut: 'ATTENTE_RESPONSABLE', utilisable: false, message: 'En cours d’examen.' };
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: attente }));
    service.simulerValidationResponsable.mockReturnValue(of(IDENTITE_VALIDEE));
    const { fixture, page } = await afficher();
    expect(page.querySelector('.identite .message')?.textContent).toBe('En cours d’examen.');
    Array.from(page.querySelectorAll<HTMLButtonElement>('button.secondaire'))
      .find((b) => b.textContent?.includes('responsable'))!
      .click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(service.simulerValidationResponsable).toHaveBeenCalledWith('ci-1');
    expect(page.querySelector('form.eligibilite')).not.toBeNull();
  });

  it('PPE : validation refusée, message affiché', async () => {
    const attente: ControleIdentite = { ...IDENTITE_VALIDEE, statut: 'ATTENTE_RESPONSABLE', utilisable: false };
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: attente }));
    service.simulerValidationResponsable.mockReturnValue(refus(409, { message: 'Pas en attente.' }));
    const { fixture, page } = await afficher();
    Array.from(page.querySelectorAll<HTMLButtonElement>('button.secondaire'))
      .find((b) => b.textContent?.includes('responsable'))!
      .click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.querySelector('.livret > .message')?.textContent).toBe('Pas en attente.');
  });

  it('éligibilité refusée faute d’identité confirmée : retour à l’étape « Mon identité »', async () => {
    service.demander.mockReturnValue(refus(409, { message: SANS_IDENTITE_CONFIRMEE }));
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('app-etape-identite-livret-a')).not.toBeNull();
  });

  it('le cadre de test choisit la réponse du contrôle sanctions/PPE', async () => {
    service.identite.mockReturnValue(of({ identite: IDENTITE, controle: null }));
    const { fixture, page } = await afficher();
    const radios = page.querySelectorAll<HTMLInputElement>('input[name=cas-sanctions]');
    expect(radios.length).toBe(CAS_SANCTIONS.length);
    radios[2].click();
    fixture.detectChanges();
    expect(radios[2].checked).toBe(true);
  });

  it('transmission refusée avec une session expirée : aucun message', async () => {
    service.transmettreReponse.mockReturnValue(refus(401, null));
    const { fixture, page } = await afficher();
    page.querySelector<HTMLInputElement>('input[name=attestation]')!.click();
    page.querySelector<HTMLInputElement>('input[name=partage][value=oui]')!.click();
    await valider(fixture, page);
    expect(page.querySelector('.message')).toBeNull();
  });
});
