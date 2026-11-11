import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { SuiviSession } from '../session/suivi-session';
import { CadreEspace, PageDuCadre } from './cadre-espace';

@Component({
  imports: [CadreEspace],
  template: `<app-cadre-espace [page]="page()" [identifiant]="identifiant()"><p class="contenu">Contenu</p></app-cadre-espace>`,
})
class Hote {
  readonly page = signal<PageDuCadre>('virement');
  readonly identifiant = signal('ABC');
}

describe('CadreEspace', () => {
  const suivi = { restant: signal<number | null>(600), deconnecter: vi.fn() };

  beforeEach(async () => {
    suivi.restant.set(600);
    suivi.deconnecter.mockClear();
    await TestBed.configureTestingModule({
      imports: [Hote],
      providers: [provideRouter([]), { provide: SuiviSession, useValue: suivi }],
    }).compileComponents();
  });

  async function afficher(page: PageDuCadre, identifiant = 'ABC') {
    const fixture = TestBed.createComponent(Hote);
    fixture.componentInstance.page.set(page);
    fixture.componentInstance.identifiant.set(identifiant);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche le menu, la barre du haut et le contenu de la page', async () => {
    const page = await afficher('virement');
    expect(page.querySelector('aside .logo')?.textContent).toContain('Hi-Tech');
    expect(page.querySelector('.hello')?.textContent).toContain('ABC');
    expect(page.querySelector('.minuteur')?.textContent).toContain('10:00');
    expect(page.querySelector('main .contenu')?.textContent).toBe('Contenu');
    expect(page.querySelector('aside a[href="/versement"]')?.textContent).toContain('Faire un virement');
  });

  it.each([
    ['virement', 'Faire un virement', 'Virement'],
    ['comptes', 'Mes comptes', 'Mes comptes'],
  ] as const)('marque la page %s dans les deux menus', async (cible, menu, mobile) => {
    const page = await afficher(cible);
    const actifs = page.querySelectorAll('a[aria-current="page"]');
    expect(actifs).toHaveLength(2);
    expect(page.querySelector('aside a[aria-current="page"]')?.textContent).toContain(menu);
    expect(page.querySelector('.mnav a[aria-current="page"]')?.textContent).toBe(mobile);
  });

  it("n'affiche ni identifiant ni minuteur tant qu'ils ne sont pas connus", async () => {
    suivi.restant.set(null);
    const page = await afficher('comptes', '');
    expect(page.querySelector('.hello')?.textContent?.trim()).toBe('');
    expect(page.querySelector('.minuteur')).toBeNull();
  });

  it('« Me déconnecter » ferme la session', async () => {
    const page = await afficher('comptes');
    (page.querySelector('.deconnexion') as HTMLButtonElement).click();
    expect(suivi.deconnecter).toHaveBeenCalledTimes(1);
  });
});
