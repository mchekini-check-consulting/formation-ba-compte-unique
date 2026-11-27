import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { formaterSolde } from '../comptes/comptes';
import { sessionFermee } from '../session/session-fermee';
import { Espace } from './espace';

const ACTIVITE = '/api/backend/connexion/session/activite';
const COMPTES = '/api/backend/comptes';
const PROFIL = '/api/backend/profil';
const PARAMETRES = '/api/backend/catalogue/parametres-publics';
const PARAMETRES_PUBLICS = { fraisUcPct: { PER: 0.5, AV: 0.65 }, fondsEurosAv: { tauxBrutVisePct: 2.9, dateMiseAJour: '2026-01-01', fraisGestionPct: null } };
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

  async function ouvrir(reponse: unknown, options?: { status: number; statusText: string }, parametres: unknown = PARAMETRES_PUBLICS) {
    const fixture = TestBed.createComponent(Espace);
    // CU-17 : ouvrir l'espace est une action de la cliente (le délai de 10 minutes repart).
    const session = http.expectOne({ method: 'POST', url: ACTIVITE });
    expect(session.request.withCredentials).toBe(true);
    session.flush({ identifiant: 'ABC', expireDansSecondes: 600 });
    const comptes = http.expectOne(COMPTES);
    expect(comptes.request.withCredentials).toBe(true);
    comptes.flush(reponse as never, options);
    http.expectOne(PROFIL).flush({ statut: 'A_COMPLETER', profil: null });
    http.expectOne(PARAMETRES).flush(parametres as never);
    await fixture.whenStable();
    // Lot 3 : interrupteurs des boutons de fonctionnalité, tous désactivés (« Bientôt »).
    http.match('/api/backend/fonctionnalites').forEach((r) => r.flush({}));
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  // CU-24 : taux brut visé du fonds en euros lu dans les paramètres publics ; sans réponse, il n'est pas affiché.
  it('carte assurance vie : taux brut visé du fonds en euros lu dans le paramètre, sans net (D1)', async () => {
    const carte = (page: HTMLElement) => [...page.querySelectorAll('#epargne .produit')].find((c) => c.textContent?.includes('Assurance-vie'))!;
    const { page } = await ouvrir([COMPTE]);
    expect(carte(page).querySelector('.rate')?.textContent?.trim()).toBe('Taux brut visé en 2026 : 2,90 % (mis à jour le 01/01/2026) sur le fonds en euros, non garanti');
    expect(carte(page).textContent).toContain('Frais de gestion détaillés avant signature');

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Espace],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([sessionFermee])), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const sansParametres = await ouvrir([COMPTE], undefined, { fraisUcPct: { PER: 0.5, AV: 0.65 }, fondsEurosAv: { tauxBrutVisePct: null, dateMiseAJour: null, fraisGestionPct: null } });
    expect(carte(sansParametres.page).querySelector('.rate')).toBeNull();
  });

  it("affiche l'espace et le solde du compte quand la session est valide", async () => {
    const { page } = await ouvrir([COMPTE]);
    expect(page.querySelector('.solde')?.textContent).toBe(formaterSolde(124050));
    expect(page.querySelector('.float.a .v')?.textContent).toBe(formaterSolde(124050));
    expect(page.querySelector('.proof')?.textContent).toContain('05/11/2026');
    expect(page.querySelector('.proof')?.textContent).toContain('Actif');
    expect(page.querySelector('.noacct')).toBeNull();
  });

  it('affiche le statut du profil investisseur et un lien vers /profil (CU-10)', async () => {
    const { page } = await ouvrir([COMPTE]);
    expect(page.querySelector('.statut-profil')?.textContent).toBe('À compléter');
    expect(page.querySelector('a[href="/profil"]')?.textContent).toContain('Voir mon profil');
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

  it('lot 3 : les parcours pas encore développés sont des boutons « Bientôt » ; un clic n’affiche plus aucun message', async () => {
    const { fixture, page } = await ouvrir([COMPTE]);
    const ldds = page.querySelector<HTMLButtonElement>('#epargne [data-fonctionnalite="ldds"]')!;
    expect(ldds.getAttribute('aria-disabled')).toBe('true');
    expect(ldds.textContent).toContain('Souscrire');
    ldds.click();
    await fixture.whenStable();
    expect(page.querySelector('.toast')).toBeNull();
    expect(page.textContent).not.toContain('bientôt disponible.');
  });

  it('la carte Livret A ouvre la page « Ouvrir mon Livret A » (CU-31)', async () => {
    const { page } = await ouvrir([COMPTE]);
    const lien = page.querySelector<HTMLAnchorElement>('#epargne .produit .btn')!;
    expect(lien.textContent?.trim()).toBe('Ouvrir mon Livret A');
    expect(lien.getAttribute('href')).toBe('/livret-a');
  });

  it('la rubrique Épargne mène aux supports disponibles de l’assurance vie et du PER (CU-412)', async () => {
    const { page } = await ouvrir([COMPTE]);
    expect(page.querySelector('#epargne [data-test="supports-av"]')!.getAttribute('href')).toBe('/supports?produit=AV');
    expect(page.querySelector('#epargne [data-test="supports-per"]')!.getAttribute('href')).toBe('/supports?produit=PER');
  });

  it('« Souscrire » de l’assurance vie et du PER mène à leur présentation et à leurs documents (CU-311, CU-331)', async () => {
    const { page } = await ouvrir([COMPTE]);
    const liens = Array.from(page.querySelectorAll<HTMLAnchorElement>('#epargne .produit a.btn')).map((a) =>
      a.getAttribute('href'),
    );
    expect(liens).toEqual(['/livret-a', '/decouvrir?produit=AV', '/decouvrir?produit=PER']);
  });

  it('les deux boutons « Faire un virement » ouvrent la page de virement (CU-11)', async () => {
    const { page } = await ouvrir([COMPTE]);
    const boutons = Array.from(page.querySelectorAll('a')).filter((lien) =>
      lien.textContent?.includes('Faire un virement'),
    );
    expect(boutons).toHaveLength(2);
    for (const bouton of boutons) {
      expect(bouton.getAttribute('href')).toBe('/versement');
    }
    expect(page.textContent).not.toContain('Faire un versement');
    expect(page.querySelector('a.tile[href="/versement"]')?.textContent).toContain(
      'Virement entrant ou sortant, depuis ou vers votre compte.',
    );
  });

  it('fait défiler la page vers une rubrique de l’accueil, sans quitter l’espace', async () => {
    const { page } = await ouvrir([COMPTE]);
    const rubrique = page.querySelector<HTMLElement>('#epargne')!;
    rubrique.scrollIntoView = vi.fn();
    const clic = new MouseEvent('click', { bubbles: true, cancelable: true });
    page.querySelector('.hero a[href="#epargne"]')!.dispatchEvent(clic);
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
