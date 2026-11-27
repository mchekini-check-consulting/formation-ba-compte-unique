import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ControleIdentite, DemandeLivretA, LivretAService } from '../livret-a/livret-a';
import { LivretATest, MOTIF_REFUS_TEST } from './livret-a-test';

const DEMANDE: DemandeLivretA = {
  reference: 'ref-1',
  statut: 'EN_COURS_DE_VERIFICATION',
  message: 'Votre demande est en cours de vérification.',
  attestationLe: '2026-11-12T09:00:00Z',
  partageDetailAutorise: true,
  choixPartageLe: '2026-11-12T09:00:00Z',
  interrogationLe: '2026-11-12T09:00:00Z',
  reponseFiscale: 'SANS_REPONSE',
  retard: false,
  delai: '2 jours ouvrés',
  transmission: null,
};
const ATTENTE: ControleIdentite = { reference: 'ci-1', statut: 'ATTENTE_RESPONSABLE', message: 'En cours d’examen.', utilisable: false, aControler: null };
const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

// D4 : simulations de CU-28 (critère 9), CU-295 (critère 5) et CU-296, sorties de la page Livret A de la cliente.
describe('LivretATest (/test/livret-a)', () => {
  const service = {
    etat: vi.fn(),
    identite: vi.fn(),
    simulerValidationResponsable: vi.fn(),
    simulerExpirationDelai: vi.fn(),
    simulerValidationCertificat: vi.fn(),
    simulerRefusCertificat: vi.fn(),
  };

  beforeEach(() => {
    Object.values(service).forEach((f) => f.mockReset());
    service.etat.mockReturnValue(of({ demande: DEMANDE }));
    service.identite.mockReturnValue(of({ identite: null, controle: ATTENTE }));
    TestBed.configureTestingModule({
      imports: [LivretATest],
      providers: [provideRouter([]), { provide: LivretAService, useValue: service }],
    });
  });

  async function afficher() {
    const fixture = TestBed.createComponent(LivretATest);
    fixture.detectChanges();
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const cliquer = async (test: string) => {
      page.querySelector<HTMLButtonElement>(`[data-test="${test}"]`)!.click();
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return { page, cliquer };
  }

  it('PPE en attente : « Simuler la validation du responsable » (CU-28 critère 9), puis relecture', async () => {
    service.simulerValidationResponsable.mockReturnValue(of({ ...ATTENTE, statut: 'VALIDE', utilisable: true }));
    const { page, cliquer } = await afficher();
    expect(page.querySelector('[data-test="controle"]')?.textContent).toBe('En cours d’examen.');
    await cliquer('responsable');
    expect(service.simulerValidationResponsable).toHaveBeenCalledWith('ci-1');
    expect(service.etat).toHaveBeenCalledTimes(2);
    expect(page.querySelector('[role=status]')?.textContent).toContain('Ouvrir mon Livret A');
  });

  it('demande interrogée sans réponse : expiration du délai simulée (CU-295 critère 5) ; refus sans message', async () => {
    service.simulerExpirationDelai.mockReturnValueOnce(of({ ...DEMANDE, retard: true })).mockReturnValueOnce(refus(404, null));
    const { page, cliquer } = await afficher();
    await cliquer('expiration');
    expect(service.simulerExpirationDelai).toHaveBeenCalledWith('ref-1');
    await cliquer('expiration');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('La simulation n’a pas pu aboutir.');
  });

  it('certificat à vérifier : refus avec motif puis validation par un conseiller (CU-296)', async () => {
    service.etat.mockReturnValue(of({ demande: { ...DEMANDE, statut: 'CERTIFICAT_A_VERIFIER' } }));
    service.simulerRefusCertificat.mockReturnValue(refus(409, { message: 'Pas de certificat.' }));
    service.simulerValidationCertificat.mockReturnValue(of(DEMANDE));
    const { page, cliquer } = await afficher();
    expect(page.querySelector('[data-test="expiration"]')).toBeNull();
    await cliquer('refuser-certificat');
    expect(service.simulerRefusCertificat).toHaveBeenCalledWith('ref-1', MOTIF_REFUS_TEST);
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Pas de certificat.');
    await cliquer('valider-certificat');
    expect(service.simulerValidationCertificat).toHaveBeenCalledWith('ref-1');
  });

  it('sans démarche ni contrôle : rien à simuler ; sans session : message', async () => {
    service.etat.mockReturnValue(of({ demande: null }));
    service.identite.mockReturnValue(of({ identite: null, controle: null }));
    const { page } = await afficher();
    expect(page.textContent).toContain('Aucune demande de Livret A en cours.');
    expect(page.querySelector('button')).toBeNull();

    service.etat.mockReturnValue(refus(401, null));
    const sansSession = await afficher();
    expect(sansSession.page.querySelector('[role=alert]')?.textContent).toContain('Connectez-vous d’abord');
  });
});
