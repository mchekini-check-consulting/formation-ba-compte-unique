import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProfilService } from '../profil/profil';
import { SuiviSession } from '../session/suivi-session';
import { Ariane, GabaritEspace, MENU } from './gabarit-espace';

@Component({ template: '<p class="contenu">Contenu</p>' })
class PageTest {}

const VIREMENT: Ariane = { rubrique: 'Virements et bénéficiaires', page: 'Faire un virement', retour: { libelle: 'Accueil', lien: '/espace' } };
const SOUSCRIRE: Ariane = {
  rubrique: 'Épargne et placements',
  parent: { libelle: 'Découvrir le produit', lien: '/decouvrir' },
  page: 'Souscrire',
  retour: { libelle: 'Découvrir le produit', lien: '/decouvrir' },
  garderProduit: true,
};

// Lot 2 (PO, 27/11/2026) : gabarit unique des pages connectées (en-tête, menu unique, fil d'Ariane, retour).
describe('GabaritEspace', () => {
  const suivi = {
    ouvrir: vi.fn<() => Observable<{ identifiant: string; expireDansSecondes: number }>>(),
    arreter: vi.fn(),
    deconnecter: vi.fn(),
    restant: vi.fn(() => 600) as unknown as () => number | null,
  };
  const profils = { lire: vi.fn() };

  beforeEach(() => {
    suivi.ouvrir.mockReset().mockReturnValue(of({ identifiant: 'ABC', expireDansSecondes: 600 }));
    suivi.arreter.mockReset();
    suivi.deconnecter.mockReset();
    profils.lire.mockReset().mockReturnValue(of({ statut: 'VALIDE', profil: null, prenom: 'Marie' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: GabaritEspace,
            children: [
              { path: 'espace', component: PageTest },
              { path: 'versement', component: PageTest, data: { ariane: VIREMENT } },
              { path: 'souscrire', component: PageTest, data: { ariane: SOUSCRIRE } },
            ],
          },
        ]),
        { provide: SuiviSession, useValue: suivi },
        { provide: ProfilService, useValue: profils },
      ],
    });
  });

  async function ouvrir(adresse: string) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(adresse);
    harness.detectChanges();
    return { harness, page: harness.routeNativeElement!.ownerDocument.querySelector('app-gabarit-espace') as HTMLElement };
  }

  it('en-tête : « Bonjour + prénom » (D8), minuterie, Messages et Me déconnecter ; la page dans le gabarit', async () => {
    const { page } = await ouvrir('/versement');
    expect(page.querySelector('.hello')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Bonjour Marie');
    expect(page.querySelector('.minuteur')?.textContent).toContain('10:00');
    expect(page.querySelector('.top')?.textContent).toContain('Messages');
    expect(page.querySelector('.contenu')).not.toBeNull();
    page.querySelector<HTMLButtonElement>('.deconnexion')!.click();
    expect(suivi.deconnecter).toHaveBeenCalled();
  });

  it('sans prénom en base : l’identifiant reste affiché (D8)', async () => {
    profils.lire.mockReturnValue(of({ statut: 'VALIDE', profil: null, prenom: null }));
    const { page } = await ouvrir('/versement');
    expect(page.querySelector('.hello')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Bonjour, identifiant ABC');
  });

  it('sans session : rien de l’espace n’est affiché (CU-18)', async () => {
    suivi.ouvrir.mockReturnValue(throwError(() => new Error('401')));
    const { page } = await ouvrir('/versement');
    expect(page.querySelector('.contenu')).toBeNull();
    expect(page.querySelector('.hello')?.textContent?.trim()).toBe('');
  });

  it('menu unique : les 8 rubriques du PO, toutes les entrées des deux anciens menus, la page courante marquée', async () => {
    expect(MENU.map((r) => r.titre)).toEqual([
      'Accueil', 'Mes comptes et contrats', 'Virements et bénéficiaires', 'Épargne et placements', 'Crédits', 'Simulateurs', 'Documents', 'Mon profil et sécurité',
    ]);
    const { page } = await ouvrir('/versement');
    const liens = [...page.querySelectorAll<HTMLAnchorElement>('aside nav a')].map((a) => a.getAttribute('href'));
    for (const attendu of ['/espace', '/espace#compte', '/comptes', '/espace#epargne', '/espace#credits', '/espace#patrimoine', '/espace#optimisation',
      '/espace#simulateurs', '/espace#documents', '/espace#infos', '/espace#bientot', '/versement', '/beneficiaires', '/profil',
      '/livret-a', '/supports', '/gestion?produit=PER', '/gestion?produit=AV']) {
      expect(liens, attendu).toContain(attendu);
    }
    const actifs = page.querySelectorAll('aside a[aria-current="page"]');
    expect(actifs).toHaveLength(1);
    expect(actifs[0].textContent).toContain('Faire un virement');
  });

  it('fil d’Ariane et retour ; le produit est gardé par le retour de « Souscrire »', async () => {
    const { page } = await ouvrir('/souscrire?produit=AV');
    expect([...page.querySelectorAll('.ariane li')].map((li) => li.textContent?.trim())).toEqual(['Accueil', 'Épargne et placements', 'Découvrir le produit', 'Souscrire']);
    expect(page.querySelector('.ariane [aria-current="page"]')?.textContent).toBe('Souscrire');
    expect(page.querySelector('.retour')?.getAttribute('href')).toBe('/decouvrir?produit=AV');
  });

  it('accueil : ni fil d’Ariane ni retour ; changer de page signale une action (CU-17) ; « Aller au contenu »', async () => {
    const { harness, page } = await ouvrir('/espace');
    expect(page.querySelector('.ariane')).toBeNull();
    expect(suivi.ouvrir).toHaveBeenCalledTimes(1);
    await harness.navigateByUrl('/versement');
    expect(suivi.ouvrir).toHaveBeenCalledTimes(2);
    expect(TestBed.inject(Router).url).toBe('/versement');
    const evitement = page.querySelector<HTMLAnchorElement>('.evitement')!;
    evitement.click();
    expect(document.activeElement?.id).toBe('contenu');
  });

  it('« Messages » annonce la messagerie à venir', async () => {
    vi.useFakeTimers();
    const { harness, page } = await ouvrir('/espace');
    [...page.querySelectorAll<HTMLButtonElement>('.top button')].find((b) => b.textContent?.includes('Messages'))!.click();
    harness.detectChanges();
    expect(page.querySelector('.toast')?.textContent).toContain('Messagerie');
    vi.advanceTimersByTime(3200);
    harness.detectChanges();
    expect(page.querySelector('.toast')).toBeNull();
    vi.useRealTimers();
  });
});
