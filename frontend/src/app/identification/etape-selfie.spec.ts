import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EtapeSelfie } from './etape-selfie';
import { MESSAGE_ANNULATION, MESSAGE_INDISPONIBLE } from './regles';

const URL = '/api/bouchons-identite/stripe-identity/verifications-selfie';

describe('EtapeSelfie', () => {
  let fixture: ComponentFixture<EtapeSelfie>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let validee: boolean;
  let annulation: string | undefined;
  const arret = vi.fn();
  const getUserMedia = vi.fn();

  const cliquer = async (texte: string) => {
    [...page.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(texte))!.click();
    await fixture.whenStable();
  };
  const reponse = (suite: string, message: string | null) => ({
    resultat: 'X',
    score: null,
    suite,
    statutDossier: suite === 'ANNULER' ? 'ANNULE' : 'EN_COURS_DE_VERIFICATION',
    message,
  });

  beforeEach(async () => {
    arret.mockReset();
    getUserMedia.mockReset().mockResolvedValue({ getTracks: () => [{ stop: arret }] });
    Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia }, configurable: true });
    await TestBed.configureTestingModule({
      imports: [EtapeSelfie],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EtapeSelfie);
    fixture.componentRef.setInput('dossierId', 'dossier-1');
    validee = false;
    annulation = undefined;
    fixture.componentInstance.valide.subscribe(() => (validee = true));
    fixture.componentInstance.annule.subscribe((m) => (annulation = m));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  it('guide la cliente puis ouvre la caméra', async () => {
    expect(page.textContent).toContain('Tournez doucement la tête vers la gauche, puis vers la droite.');
    await cliquer('Démarrer la caméra');
    expect(getUserMedia).toHaveBeenCalled();
    expect(page.querySelector('video')).not.toBeNull();
  });

  it('envoie le selfie, coupe la caméra et passe à la suite', async () => {
    await cliquer('Démarrer la caméra');
    await cliquer('Vérifier mon selfie');
    const requete = http.expectOne(URL);
    expect(requete.request.body).toEqual({ dossierId: 'dossier-1', selfie: 'SELFIE_CONFORME' });
    requete.flush(reponse('POURSUIVRE', null));
    expect(validee).toBe(true);
    expect(arret).toHaveBeenCalled();
  });

  it('annule la demande si le selfie ne correspond pas', async () => {
    const select = page.querySelector<HTMLSelectElement>('#scenario-selfie')!;
    select.value = 'SELFIE_DIFFERENT';
    select.dispatchEvent(new Event('change'));
    await cliquer('Démarrer la caméra');
    await cliquer('Vérifier mon selfie');
    const requete = http.expectOne(URL);
    expect(requete.request.body.selfie).toBe('SELFIE_DIFFERENT');
    requete.flush(reponse('ANNULER', MESSAGE_ANNULATION));
    expect(annulation).toBe(MESSAGE_ANNULATION);
  });

  it('indique une vérification impossible si le service ne répond pas', async () => {
    await cliquer('Démarrer la caméra');
    await cliquer('Vérifier mon selfie');
    http.expectOne(URL).flush(reponse('REESSAYER_PLUS_TARD', MESSAGE_INDISPONIBLE));
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain(MESSAGE_INDISPONIBLE);
    await cliquer('Vérifier mon selfie');
    http.expectOne(URL).flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain(MESSAGE_INDISPONIBLE);
    expect(validee).toBe(false);
  });

  it("explique comment autoriser la caméra refusée, sans rien envoyer", async () => {
    getUserMedia.mockRejectedValue(new Error('NotAllowedError'));
    await cliquer('Démarrer la caméra');
    expect(page.querySelector('h2')?.textContent).toBe('Autorisez la caméra');
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Vérification impossible, réessayez');
    http.expectNone(URL);
    getUserMedia.mockResolvedValue({ getTracks: () => [] });
    await cliquer('Réessayer');
    expect(page.querySelector('video')).not.toBeNull();
  });

  it("revient à l'accueil de la demande en coupant la caméra", async () => {
    let retour = false;
    fixture.componentInstance.retour.subscribe(() => (retour = true));
    await cliquer('Démarrer la caméra');
    await cliquer('Retour');
    expect(retour).toBe(true);
    expect(arret).toHaveBeenCalled();
  });
});
