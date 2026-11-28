import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Route, provideRouter } from '@angular/router';
import { Espace } from '../espace/espace';
import { MENU } from '../espace/gabarit-espace';
import { PAGES_PUBLIQUES, routes } from '../app.routes';
import { FONCTIONNALITES, Fonctionnalite } from './fonctionnalites';

// Lot 3 (PO, 27/11/2026) : test automatique des liens, dans la chaîne d'intégration (un échec bloque la fusion).
// Il échoue si un bouton « Disponible » ne mène à aucune page (renvoi par défaut vers l'accueil), si une page de la
// cliente n'est reliée à aucun bouton du menu, ou si un bouton reste « Bientôt » alors que sa fonctionnalité est livrée.

const ESPACE = routes.find((r) => r.children)!;
const PAGES_CLIENTE = ESPACE.children!.map((r) => r.path!);
const DEFINIES = [...PAGES_PUBLIQUES, ...ESPACE.children!].map((r: Route) => r.path!);

/** Une adresse mène à une page définie (et non au renvoi par défaut « ** »). */
function pageDefinie(adresse: string): boolean {
  const chemin = adresse.split(/[?#]/)[0].replace(/^\//, '');
  return DEFINIES.some((p) => p === chemin || new RegExp(`^${p.replace(/:[^/]+/g, '[^/]+')}$`).test(chemin));
}

/** Fonctionnalités sans page à ce jour : leur bouton reste « Bientôt » même si leur interrupteur était activé. */
const SANS_PAGE = Object.entries(FONCTIONNALITES as Record<string, Fonctionnalite>).filter(([, f]) => !f.lien).map(([cle]) => cle);

describe('Liens et boutons des pages connectées (lot 3)', () => {
  it('chaque entrée du menu unique mène à une page définie', () => {
    for (const entree of MENU.flatMap((r) => (r.lien ? [r.lien] : (r.entrees ?? [])))) {
      expect(pageDefinie(entree.lien), entree.libelle).toBe(true);
    }
  });

  it('chaque page de la cliente est reliée au menu (sauf le détail d’un compte, ouvert depuis Mes comptes, et le parcours de souscription)', () => {
    const relies = new Set(MENU.flatMap((r) => (r.lien ? [r.lien] : (r.entrees ?? []))).map((e) => e.lien.replace(/^\//, '')));
    // Parcours : Découvrir puis Souscrire s'ouvrent depuis les cartes « Souscrire » de Produits à souscrire.
    const horsMenu = ['comptes/:id', 'produit', 'decouvrir', 'souscrire'];
    for (const page of PAGES_CLIENTE.filter((p) => !horsMenu.includes(p))) {
      expect(relies.has(page), `/${page}`).toBe(true);
    }
  });

  it('une fonctionnalité livrée mène à une page définie ; les fonctionnalités sans page sont connues', () => {
    for (const [cle, f] of Object.entries(FONCTIONNALITES as Record<string, Fonctionnalite>)) {
      if (f.lien) {
        expect(pageDefinie(f.lien), cle).toBe(true);
      }
    }
    expect(SANS_PAGE).toHaveLength(21);
  });

  describe('accueil de l’espace', () => {
    async function ouvrir(etats: Record<string, boolean>) {
      TestBed.configureTestingModule({
        imports: [Espace],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      const fixture = TestBed.createComponent(Espace);
      const http = TestBed.inject(HttpTestingController);
      http.expectOne({ method: 'POST', url: '/api/backend/connexion/session/activite' }).flush({ identifiant: 'ABC', expireDansSecondes: 600 });
      http.expectOne('/api/backend/comptes').flush([]);
      http.expectOne('/api/backend/profil').flush({ statut: 'VALIDE', profil: null });
      http.expectOne('/api/backend/catalogue/parametres-publics').flush({ fraisUcPct: {}, fondsEurosAv: { tauxBrutVisePct: null, dateMiseAJour: null, fraisGestionPct: null } });
      http.expectOne('/api/backend/gestion/contrats').flush([{ produit: 'PER', numeroContrat: 'PER-1', ouvertLe: '2026-11-27T10:00:00Z', mode: 'PILOTEE', profil: 'PRUDENT' }]);
      http.expectOne('/api/backend/livret-a/demande').flush({ demande: null });
      fixture.detectChanges();
      await fixture.whenStable();
      http.expectOne('/api/backend/fonctionnalites').flush(etats);
      fixture.detectChanges();
      await fixture.whenStable();
      return fixture.nativeElement as HTMLElement;
    }

    it('tous les liens mènent à une page définie ou à une rubrique de la page ; aucun bouton ne reste sans action', async () => {
      const page = await ouvrir({});
      for (const lien of page.querySelectorAll<HTMLAnchorElement>('a[href]')) {
        const href = lien.getAttribute('href')!;
        if (href.startsWith('#')) {
          expect(page.querySelector(href), href).not.toBeNull();
        } else {
          expect(pageDefinie(href), href).toBe(true);
        }
      }
      // Un bouton est soit un bouton de fonctionnalité « Bientôt », soit un bouton d'action de la page.
      for (const bouton of page.querySelectorAll('button')) {
        expect(bouton.getAttribute('data-etat') ?? 'action', bouton.textContent ?? '').not.toBe('');
      }
    });

    it('interrupteurs désactivés : tous les boutons de fonctionnalité sont « Bientôt », désactivés, sans lien', async () => {
      const page = await ouvrir({});
      const boutons = [...page.querySelectorAll<HTMLElement>('[data-fonctionnalite]')];
      expect(boutons.length).toBeGreaterThanOrEqual(20);
      for (const b of boutons) {
        expect(b.getAttribute('data-etat'), b.getAttribute('data-fonctionnalite')!).toBe('bientot');
        expect(b.getAttribute('aria-disabled')).toBe('true');
        expect(b.tagName).toBe('BUTTON');
        expect(b.textContent).toContain('Bientôt');
      }
    });

    it('interrupteurs activés : un bouton n’est « Bientôt » que si sa fonctionnalité n’a pas encore de page', async () => {
      const page = await ouvrir(Object.fromEntries(Object.keys(FONCTIONNALITES).map((cle) => [cle, true])));
      for (const b of page.querySelectorAll<HTMLElement>('[data-fonctionnalite]')) {
        const cle = b.getAttribute('data-fonctionnalite')!;
        expect(b.getAttribute('data-etat'), cle).toBe(SANS_PAGE.includes(cle) ? 'bientot' : 'disponible');
      }
    });
  });
});
