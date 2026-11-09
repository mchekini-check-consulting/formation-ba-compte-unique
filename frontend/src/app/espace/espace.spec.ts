import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { formaterSolde } from '../comptes/comptes';
import { sessionFermee } from '../session/session-fermee';
import { Espace } from './espace';

const ACTIVITE = '/api/backend/connexion/session/activite';
const DECONNEXION = '/api/backend/connexion/deconnexion';
const COMPTES = '/api/backend/comptes';
const COMPTE = {
  id: 1,
  ibanMasque: 'FR76 •••• •••• 0105',
  statut: 'ACTIF',
  soldeCentimes: 124050,
  soldeMisAJourLe: '2026-11-05T15:10:00Z',
  premierVersementAttendu: false,
};

describe('Espace', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Espace],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([sessionFermee])), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir(reponse: unknown, options?: { status: number; statusText: string }) {
    const fixture = TestBed.createComponent(Espace);
    // CU-17 : ouvrir l'espace est une action de la cliente (le délai de 10 minutes repart).
    const session = http.expectOne({ method: 'POST', url: ACTIVITE });
    expect(session.request.withCredentials).toBe(true);
    session.flush({ identifiant: 'ABC', expireDansSecondes: 600 });
    const comptes = http.expectOne(COMPTES);
    expect(comptes.request.withCredentials).toBe(true);
    comptes.flush(reponse as never, options);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it("affiche l'espace et le solde du compte quand la session est valide", async () => {
    const { page } = await ouvrir([COMPTE]);
    expect(page.querySelector('.hello')?.textContent).toContain('ABC');
    expect(page.querySelector('a[href="/comptes"]')).not.toBeNull();
    expect(page.querySelector('.solde')?.textContent).toBe(formaterSolde(124050));
    expect(page.querySelector('.float.a .v')?.textContent).toBe(formaterSolde(124050));
    expect(page.querySelector('.proof')?.textContent).toContain('05/11/2026');
    expect(page.querySelector('.proof')?.textContent).toContain('Actif');
    expect(page.querySelector('.noacct')).toBeNull();
  });

  it('affiche tous les produits ouverts à la souscription, épargne et crédits', async () => {
    const { page } = await ouvrir([COMPTE]);
    const noms = (section: string) =>
      Array.from(page.querySelectorAll(`#${section} .produit h3`)).map((titre) => titre.textContent);
    expect(noms('epargne')).toEqual(['Livret A', 'LDDS', 'CEL', 'Assurance-vie', 'PER']);
    expect(noms('credits')).toEqual(['Crédit immobilier', 'Crédit à la consommation', 'Rachat de crédits']);
    for (const id of ['compte', 'patrimoine', 'optimisation', 'simulateurs', 'documents', 'infos', 'bientot']) {
      expect(page.querySelector(`#${id}`)).not.toBeNull();
    }
  });

  it('annonce les parcours pas encore développés, sans rien envoyer', async () => {
    const { fixture, page } = await ouvrir([COMPTE]);
    page.querySelector<HTMLButtonElement>('#epargne .produit .btn')!.click();
    await fixture.whenStable();
    expect(page.querySelector('.toast')?.textContent).toBe('Souscription Livret A : bientôt disponible.');
  });

  it('les deux boutons « Faire un versement » ouvrent la page de versement (CU-11)', async () => {
    const { page } = await ouvrir([COMPTE]);
    const boutons = Array.from(page.querySelectorAll('a')).filter((lien) =>
      lien.textContent?.includes('Faire un versement'),
    );
    expect(boutons).toHaveLength(2);
    for (const bouton of boutons) {
      expect(bouton.getAttribute('href')).toBe('/versement');
    }
  });

  it('affiche le compte à rebours de la session', async () => {
    const { page } = await ouvrir([COMPTE]);
    expect(page.querySelector('.minuteur')?.textContent).toContain('10:00');
  });

  it('« Me déconnecter » révoque la session et renvoie vers la connexion (CU-17)', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture, page } = await ouvrir([COMPTE]);
    page.querySelector<HTMLButtonElement>('button.deconnexion')!.click();
    const deconnexion = http.expectOne({ method: 'POST', url: DECONNEXION });
    expect(deconnexion.request.withCredentials).toBe(true);
    deconnexion.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
  });

  it('fait défiler la page vers une rubrique du menu, sans quitter l’espace', async () => {
    const { page } = await ouvrir([COMPTE]);
    const rubrique = page.querySelector<HTMLElement>('#epargne')!;
    rubrique.scrollIntoView = vi.fn();
    const clic = new MouseEvent('click', { bubbles: true, cancelable: true });
    page.querySelector('aside a[href="#epargne"]')!.dispatchEvent(clic);
    expect(rubrique.scrollIntoView).toHaveBeenCalled();
    expect(clic.defaultPrevented).toBe(true);
  });

  it("invite à ouvrir un compte, sans solde, quand la cliente n'en a pas", async () => {
    const { page } = await ouvrir([]);
    expect(page.querySelector('.solde')).toBeNull();
    expect(page.querySelector('.hero-art')).toBeNull();
    expect(page.querySelector('.proof')).toBeNull();
    expect(page.querySelector('.noacct')?.textContent).toContain('Ouvrir mon compte');
    expect(page.querySelector('.noacct a')?.getAttribute('href')).toBe('/');
  });

  it('affiche seulement le message si la lecture échoue', async () => {
    const { page } = await ouvrir('', { status: 500, statusText: 'Erreur' });
    expect(page.querySelector('.solde.err')?.textContent).toBe('Votre solde est momentanément indisponible');
    expect(page.querySelector('.acct')?.textContent).not.toContain('€');
    expect(page.querySelector('.float.a .v.err')).not.toBeNull();
    expect(page.querySelector('.proof')?.textContent).not.toContain('Actif');
    expect(page.querySelector('.noacct')).toBeNull();
  });

  it('renvoie vers la connexion avec « Session expirée » si la session expire avant la lecture du solde', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await ouvrir({ motif: 'EXPIREE' }, { status: 401, statusText: 'Unauthorized' });
    expect(navigation).toHaveBeenCalledWith(['/connexion'], { queryParams: { motif: 'session_expiree' } });
  });

  // CU-160 : compte TEST-CLIENTE-NEGATIF (db/demo) ; la valeur reçue est incohérente.
  it("n'affiche jamais un solde négatif reçu", async () => {
    const { page } = await ouvrir([{ ...COMPTE, soldeCentimes: -500 }]);
    expect(page.querySelector('.solde.err')?.textContent).toBe('Votre solde est momentanément indisponible');
    expect(page.querySelector('.acct')?.textContent).not.toContain('€');
    expect(page.querySelector('.float.a .v.err')).not.toBeNull();
    expect(page.querySelector('.proof')?.textContent).not.toContain('Actif');
  });

  it('renvoie vers la connexion sans rien afficher si la session est fermée (retour arrière après déconnexion)', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Espace);
    http.expectOne(ACTIVITE).flush('', { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(navigation).toHaveBeenCalledWith(['/connexion'], {});
    expect((fixture.nativeElement as HTMLElement).querySelector('main')).toBeNull();
    http.expectNone(COMPTES);
  });
});
