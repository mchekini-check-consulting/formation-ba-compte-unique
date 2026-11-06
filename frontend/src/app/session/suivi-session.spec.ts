import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { CANAL_SESSION, SuiviSession, formaterRestant } from './suivi-session';

const ACTIVITE = '/api/backend/connexion/session/activite';
const SESSION = '/api/backend/connexion/session';
const DECONNEXION = '/api/backend/connexion/deconnexion';
const NON_CONNECTEE = { status: 401, statusText: 'Unauthorized' };

// Canal entre onglets simulé : on voit ce qui est envoyé et on peut simuler un autre onglet.
class FauxCanal {
  static ouverts: FauxCanal[] = [];
  readonly envoyes: unknown[] = [];
  onmessage: ((message: MessageEvent) => void) | null = null;
  ferme = false;

  constructor(readonly nom: string) {
    FauxCanal.ouverts.push(this);
  }

  postMessage(message: unknown): void {
    this.envoyes.push(message);
  }

  close(): void {
    this.ferme = true;
  }

  recevoir(message: unknown): void {
    this.onmessage?.({ data: message } as MessageEvent);
  }
}

// CU-17 : déconnexion et fermeture de la session après 10 minutes sans activité.
describe('SuiviSession', () => {
  let http: HttpTestingController;
  let suivi: SuiviSession;
  let navigation: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    FauxCanal.ouverts = [];
    vi.stubGlobal('BroadcastChannel', FauxCanal);
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    suivi = TestBed.inject(SuiviSession);
    navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    suivi.arreter();
    http.verify();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  const canal = () => FauxCanal.ouverts[0];
  const cliquer = () => document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));

  function ouvrir(expireDansSecondes = 600) {
    let identifiant = '';
    suivi.ouvrir().subscribe((session) => (identifiant = session.identifiant));
    const requete = http.expectOne({ method: 'POST', url: ACTIVITE });
    expect(requete.request.withCredentials).toBe(true);
    requete.flush({ identifiant: 'ABC', expireDansSecondes });
    return identifiant;
  }

  it("ouvrir une page de l'espace est une action : le délai repart et les autres onglets sont prévenus", () => {
    expect(ouvrir()).toBe('ABC');
    expect(suivi.restant()).toBe(600);
    expect(canal().nom).toBe(CANAL_SESSION);
    expect(canal().envoyes).toEqual([{ type: 'activite', expireDansSecondes: 600 }]);
  });

  it('le compte à rebours diminue chaque seconde, sans appeler le serveur', () => {
    ouvrir();
    vi.advanceTimersByTime(5000);
    expect(suivi.restant()).toBe(595);
    http.expectNone(SESSION);
    http.expectNone(ACTIVITE);
  });

  it('une action remet le délai à zéro côté serveur, au plus une fois par intervalle, sans perdre la dernière', () => {
    ouvrir();
    vi.advanceTimersByTime(3000);
    cliquer();
    cliquer();
    http.expectNone(ACTIVITE);

    vi.advanceTimersByTime(12_000);
    http.expectOne(ACTIVITE).flush({ identifiant: 'ABC', expireDansSecondes: 600 });
    expect(suivi.restant()).toBe(600);

    vi.advanceTimersByTime(20_000);
    document.body.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
    http.expectOne(ACTIVITE).flush({ identifiant: 'ABC', expireDansSecondes: 600 });
    expect(canal().envoyes).toHaveLength(3);
  });

  it('à la fin du délai, la session est relue : fermée, retour à la connexion avec « Session expirée »', () => {
    ouvrir(3);
    vi.advanceTimersByTime(3000);
    expect(suivi.restant()).toBe(0);
    http.expectOne({ method: 'GET', url: SESSION }).flush('', NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], { queryParams: { motif: 'expiree' } });
    expect(suivi.restant()).toBeNull();
    expect(canal().ferme).toBe(true);
  });

  it("à la fin du délai, la session reste ouverte si la cliente a agi dans un autre onglet", () => {
    ouvrir(2);
    vi.advanceTimersByTime(2000);
    http.expectOne(SESSION).flush({ identifiant: 'ABC', expireDansSecondes: 300 });
    expect(suivi.restant()).toBe(300);
    vi.advanceTimersByTime(1000);
    expect(suivi.restant()).toBe(299);
    expect(navigation).not.toHaveBeenCalled();
  });

  it("la session est relue régulièrement, sans être prolongée : une déconnexion ailleurs renvoie vers la connexion", () => {
    ouvrir();
    vi.advanceTimersByTime(60_000);
    http.expectOne({ method: 'GET', url: SESSION }).flush('', NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
  });

  it("l'activité d'un autre onglet recale le compte à rebours", () => {
    ouvrir();
    vi.advanceTimersByTime(100_000);
    http.expectOne(SESSION).flush({ identifiant: 'ABC', expireDansSecondes: 540 });
    canal().recevoir({ type: 'activite', expireDansSecondes: 600 });
    expect(suivi.restant()).toBe(600);
  });

  it("la déconnexion dans un autre onglet ferme celui-ci, sans rien afficher de plus", () => {
    ouvrir();
    canal().recevoir({ type: 'deconnexion' });
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
    expect(suivi.restant()).toBeNull();
    cliquer();
    vi.advanceTimersByTime(60_000);
    http.expectNone(ACTIVITE);
    http.expectNone(SESSION);
  });

  it('« Me déconnecter » révoque la session, prévient les autres onglets et renvoie vers la connexion', () => {
    ouvrir();
    suivi.deconnecter();
    const requete = http.expectOne({ method: 'POST', url: DECONNEXION });
    expect(requete.request.withCredentials).toBe(true);
    const envoye = canal();
    requete.flush(null, { status: 204, statusText: 'No Content' });
    expect(envoye.envoyes).toContainEqual({ type: 'deconnexion' });
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
  });

  it('« Me déconnecter » renvoie aussi vers la connexion si le serveur ne répond pas', () => {
    ouvrir();
    suivi.deconnecter();
    http.expectOne(DECONNEXION).flush('', { status: 503, statusText: 'Unavailable' });
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
  });

  it('une action refusée par le serveur (session fermée) renvoie vers la connexion', () => {
    ouvrir();
    vi.advanceTimersByTime(20_000);
    cliquer();
    http.expectOne(ACTIVITE).flush('', NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
  });

  // CU-169 : session révoquée ou expirée côté serveur avant la fin du compte à rebours.
  it('une action refusée pour session expirée ou révoquée renvoie vers la connexion avec « Session expirée »', () => {
    ouvrir();
    vi.advanceTimersByTime(20_000);
    cliquer();
    http.expectOne(ACTIVITE).flush({ motif: 'EXPIREE' }, NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], { queryParams: { motif: 'session_expiree' } });
  });

  it('une relecture refusée pour session révoquée renvoie vers la connexion avec « Session expirée »', () => {
    ouvrir();
    vi.advanceTimersByTime(60_000);
    http.expectOne({ method: 'GET', url: SESSION }).flush({ motif: 'EXPIREE' }, NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], { queryParams: { motif: 'session_expiree' } });
  });

  it("l'ouverture d'une page avec une session révoquée renvoie vers la connexion avec « Session expirée »", () => {
    suivi.ouvrir().subscribe({ error: () => undefined });
    http.expectOne(ACTIVITE).flush({ motif: 'EXPIREE' }, NON_CONNECTEE);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], { queryParams: { motif: 'session_expiree' } });
  });

  it("sans session, l'ouverture renvoie vers la connexion", () => {
    let refus = false;
    suivi.ouvrir().subscribe({ error: () => (refus = true) });
    http.expectOne(ACTIVITE).flush('', NON_CONNECTEE);
    expect(refus).toBe(true);
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
  });

  it('une panne du serveur ne déconnecte pas la cliente', () => {
    ouvrir();
    vi.advanceTimersByTime(60_000);
    http.expectOne(SESSION).flush('', { status: 503, statusText: 'Unavailable' });
    vi.advanceTimersByTime(20_000);
    cliquer();
    http.expectOne(ACTIVITE).flush('', { status: 503, statusText: 'Unavailable' });
    expect(navigation).not.toHaveBeenCalled();
  });

  it('après arrêt (page quittée), plus aucune action ni relecture', () => {
    ouvrir();
    suivi.arreter();
    cliquer();
    vi.advanceTimersByTime(120_000);
    http.expectNone(ACTIVITE);
    http.expectNone(SESSION);
    expect(suivi.restant()).toBeNull();
  });

  it('ouvrir une seconde page ne double pas le compte à rebours', () => {
    ouvrir();
    ouvrir();
    vi.advanceTimersByTime(1000);
    expect(suivi.restant()).toBe(599);
    expect(FauxCanal.ouverts).toHaveLength(1);
  });
});

describe('formaterRestant', () => {
  it('affiche minutes et secondes', () => {
    expect(formaterRestant(600)).toBe('10:00');
    expect(formaterRestant(65)).toBe('1:05');
    expect(formaterRestant(0)).toBe('0:00');
    expect(formaterRestant(-3)).toBe('0:00');
  });
});
