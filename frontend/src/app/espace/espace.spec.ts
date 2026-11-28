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
const PARAMETRES_PUBLICS = { fraisUcPct: { PER: 0.5, AV: 0.65 }, fondsEurosAv: { tauxBrutVisePct: 2.9, dateMiseAJour: '2026-01-01', fraisGestionPct: null }, fondsEurosPer: { tauxBrutVisePct: 2.9, dateMiseAJour: '2026-01-01', fraisGestionPct: 0.65 }, mentions: { 'taux-non-garanti': 'Taux non garanti.', 'capital-garanti-fonds-euros': 'Le capital net investi sur le fonds en euros est garanti par l’assureur.' } };
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

  async function ouvrir(
    reponse: unknown,
    options?: { status: number; statusText: string },
    parametres: unknown = PARAMETRES_PUBLICS,
    lot4: { profil?: string; contrats?: unknown; demande?: unknown } = {},
  ) {
    const fixture = TestBed.createComponent(Espace);
    // CU-17 : ouvrir l'espace est une action de la cliente (le délai de 10 minutes repart).
    const session = http.expectOne({ method: 'POST', url: ACTIVITE });
    expect(session.request.withCredentials).toBe(true);
    session.flush({ identifiant: 'ABC', expireDansSecondes: 600 });
    const comptes = http.expectOne(COMPTES);
    expect(comptes.request.withCredentials).toBe(true);
    comptes.flush(reponse as never, options);
    http.expectOne(PROFIL).flush({ statut: lot4.profil ?? 'A_COMPLETER', profil: null });
    http.expectOne(PARAMETRES).flush(parametres as never);
    await fixture.whenStable();
    // Lot 3 : interrupteurs des boutons de fonctionnalité, tous désactivés (« Bientôt »).
    http.match('/api/backend/fonctionnalites').forEach((r) => r.flush({}));
    // Lot 4 : contrats et demande de Livret A de la cliente.
    http.match('/api/backend/gestion/contrats').forEach((r) => r.flush((lot4.contrats ?? []) as never));
    http.match('/api/backend/livret-a/demande').forEach((r) => r.flush({ demande: lot4.demande ?? null }));
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  // Lot 4 (PO, 27/11/2026) : synthèse en haut de l'espace.
  it('synthèse : compte courant, livrets, contrats sans valorisation et leurs actions ; actions rapides', async () => {
    const contrats = [{ produit: 'AV', numeroContrat: 'AV-1A2B3C4D', ouvertLe: '2026-11-27T22:30:00Z', mode: 'PILOTEE', profil: 'EQUILIBRE' }];
    const { page } = await ouvrir([COMPTE], undefined, PARAMETRES_PUBLICS, { profil: 'VALIDE', contrats, demande: { statut: 'OUVERTURE_VALIDEE' } });
    const synthese = page.querySelector('#synthese')!;
    expect(synthese.querySelector('h1')?.textContent).toBe('Ma synthèse');
    expect(synthese.querySelector('[data-test="synthese-compte"] .montant')?.textContent).toBe(formaterSolde(124050));
    expect(synthese.querySelector('[data-test="synthese-livrets"]')?.textContent).toContain('Livret A : Ouverture validée');
    expect(synthese.querySelector('[data-test="a-faire"]')).toBeNull();
    const ligne = [...synthese.querySelectorAll('[data-test="contrats"] tbody td')].map((td) => td.textContent?.trim());
    expect(ligne.slice(0, 6)).toEqual(['Assurance vie', 'AV-1A2B3C4D', '27/11/2026', 'Ouvert', 'Pilotée · profil Équilibré', 'Valorisation : bientôt disponible']);
    const actions = synthese.querySelector('.actions-contrat')!;
    expect(actions.querySelector('a[href="/gestion?produit=AV"]')?.textContent).toBe('Mon mode de gestion');
    expect(actions.querySelector('a[href="/supports?produit=AV"]')?.textContent).toBe('Supports disponibles');
    for (const cle of ['versement-libre', 'operations-contrat', 'suivi-contrat', 'arbitrage']) {
      expect(actions.querySelector(`[data-fonctionnalite="${cle}"]`)?.getAttribute('data-etat'), cle).toBe('bientot');
    }
    const rapides = synthese.querySelector('.actions-rapides')!;
    expect(rapides.querySelector('a[href="/versement"]')?.textContent).toBe('Faire un virement');
    expect(rapides.querySelector('[data-fonctionnalite="versement-libre"]')?.getAttribute('aria-disabled')).toBe('true');
    expect(rapides.querySelector('a[href="#epargne"]')?.textContent).toBe('Souscrire');
    expect(page.querySelector('#patrimoine')?.textContent).toContain('1 contrat(s) · valorisation bientôt disponible');
  });

  it('profil à compléter : bloc « À faire » vers le profil, cartes PER et assurance vie prévenues avant le parcours (CU-10)', async () => {
    const { page } = await ouvrir([COMPTE]);
    const aFaire = page.querySelector('[data-test="a-faire"]')!;
    expect(aFaire.textContent).toContain('Complétez votre profil investisseur pour pouvoir souscrire un PER ou une assurance vie.');
    expect(aFaire.querySelector('a')?.getAttribute('href')).toBe('/profil');
    expect(page.querySelectorAll('[data-test="profil-requis-carte"]')).toHaveLength(2);
    expect(page.querySelector('[data-test="sans-contrat"]')?.textContent).toContain('Aucun contrat');
    expect(page.querySelector('[data-test="synthese-livrets"]')?.textContent).toContain('Aucun livret');
  });

  it('cartes PER et assurance vie : frais des unités de compte lus dans le paramètre ; « détaillés avant signature » gardé', async () => {
    const { page } = await ouvrir([COMPTE]);
    const carte = (nom: string) => [...page.querySelectorAll('#epargne .produit')].find((c) => c.querySelector('h3')?.textContent === nom)!;
    expect(carte('PER').querySelector('[data-test="frais-uc"]')?.textContent).toBe('Frais de gestion des unités de compte : 0,50 % par an');
    expect(carte('Assurance-vie').querySelector('[data-test="frais-uc"]')?.textContent).toBe('Frais de gestion des unités de compte : 0,65 % par an');
    expect(carte('Assurance-vie').textContent).toContain('Frais de gestion détaillés avant signature');
    expect(carte('LDDS').querySelector('[data-test="frais-uc"]')).toBeNull();
  });

  it('contrats illisibles : message, le reste de l’espace s’affiche', async () => {
    TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Espace);
    http.expectOne({ method: 'POST', url: ACTIVITE }).flush({ identifiant: 'ABC', expireDansSecondes: 600 });
    http.expectOne(COMPTES).flush([COMPTE]);
    http.expectOne(PROFIL).flush({ statut: 'VALIDE', profil: null });
    http.expectOne(PARAMETRES).flush(PARAMETRES_PUBLICS);
    http.expectOne('/api/backend/gestion/contrats').flush(null, { status: 500, statusText: 'Erreur' });
    http.expectOne('/api/backend/livret-a/demande').flush({ demande: null });
    await fixture.whenStable();
    http.match('/api/backend/fonctionnalites').forEach((r) => r.flush({}));
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('#synthese [role="alert"]')?.textContent).toContain('Vos contrats sont momentanément indisponibles.');
    expect(page.querySelector('.solde')?.textContent).toBe(formaterSolde(124050));
  });

  // CU-24 : taux brut visé du fonds en euros lu dans les paramètres publics ; sans réponse, il n'est pas affiché.
  it('carte assurance vie : taux brut visé du fonds en euros lu dans le paramètre, sans net (D1)', async () => {
    const carte = (page: HTMLElement) => [...page.querySelectorAll('#epargne .produit')].find((c) => c.textContent?.includes('Assurance-vie'))!;
    const { page } = await ouvrir([COMPTE]);
    expect(carte(page).querySelector('.rate')?.textContent?.trim()).toBe('Taux brut visé en 2026 : 2,90 % (mis à jour le 01/01/2026). Taux non garanti.');
    expect(carte(page).textContent).toContain('Frais de gestion détaillés avant signature');
    // PO 28/11 : garantie du capital sur sa ligne, sous le taux ; carte PER : taux, net de ses frais, garantie.
    expect(carte(page).querySelector('[data-test="garantie"]')?.textContent).toBe('Le capital net investi sur le fonds en euros est garanti par l’assureur.');
    const per = [...page.querySelectorAll('#epargne .produit')].find((c) => c.querySelector('h3')?.textContent === 'PER')!;
    expect(per.querySelector('.rate')?.textContent?.trim()).toBe('Taux brut visé en 2026 : 2,90 % (mis à jour le 01/01/2026), soit 2,25 % net de frais de gestion. Taux non garanti.');
    expect(per.querySelector('[data-test="garantie"]')).not.toBeNull();

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
    expect(page.querySelector('#infos a[href="/profil"]')?.textContent).toContain('Voir mon profil');
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
    expect(liens).toEqual(['/livret-a', '/produit?produit=AV', '/produit?produit=PER']);
  });

  it('les boutons « Faire un virement » (accueil, compte courant, actions rapides) ouvrent la page de virement (CU-11)', async () => {
    const { page } = await ouvrir([COMPTE]);
    const boutons = Array.from(page.querySelectorAll('a')).filter((lien) =>
      lien.textContent?.includes('Faire un virement'),
    );
    expect(boutons).toHaveLength(3);
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
