import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Landing } from './landing';

describe('Landing', () => {
  let fixture: ComponentFixture<Landing>;
  let page: HTMLElement;

  const cliquer = async (selecteur: string) => {
    page.querySelector<HTMLElement>(selecteur)!.click();
    await fixture.whenStable();
  };
  const etapeVisible = () => page.querySelector('.pv.on');

  beforeEach(async () => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    await TestBed.configureTestingModule({ imports: [Landing] }).compileComponents();
    fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => vi.restoreAllMocks());

  it('affiche la promesse et les rubriques de la banque', () => {
    expect(page.querySelector('h1')?.textContent).toContain('Tout votre argent');
    for (const rubrique of ['compte', 'epargne', 'credits', 'optimisation', 'simulateurs']) {
      expect(page.querySelector(`#${rubrique}`)).not.toBeNull();
    }
    expect(page.querySelector('.parcours')).toBeNull();
  });

  it("ouvre le parcours à l'étape 1 et masque la page", async () => {
    await cliquer('.top .btn.p');
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 1 sur 6');
    expect(etapeVisible()?.textContent).toContain('Mon identité');
    expect(page.querySelector('.hero')).toBeNull();
  });

  it('avance et recule entre les étapes', async () => {
    await cliquer('.top .btn.p');
    await cliquer('.pv.on .pbtn:not(.ghost)');
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 2 sur 6');
    expect(etapeVisible()?.textContent).toContain("Ma pièce d'identité");
    await cliquer('.pv.on .pbtn.ghost');
    expect(etapeVisible()?.textContent).toContain('Mon identité');
  });

  it("revient à la page en reculant depuis l'étape 1", async () => {
    await cliquer('.top .btn.p');
    await cliquer('.pv.on .pbtn.ghost');
    expect(page.querySelector('.hero')).not.toBeNull();
  });

  it('revient à la page avec le lien de retour', async () => {
    await cliquer('.top .btn.p');
    await cliquer('.pbar a');
    expect(page.querySelector('.hero')).not.toBeNull();
    expect(page.querySelector('.parcours')).toBeNull();
  });

  it('confirme le compte ouvert puis revient à la page', async () => {
    await cliquer('.top .btn.p');
    for (let etape = 1; etape < 6; etape++) {
      await cliquer('.pv.on .pbtn:not(.ghost)');
    }
    expect(etapeVisible()?.textContent).toContain('Votre compte courant est ouvert');
    await cliquer('.pv.on .pbtn');
    expect(page.querySelector('.hero')).not.toBeNull();
  });

  it('envoie les cartes épargne vers le compte courant, sans ouvrir le parcours', async () => {
    const cartes = page.querySelectorAll<HTMLAnchorElement>('#epargne .card');
    expect(cartes.length).toBe(5);
    cartes.forEach((carte) => expect(carte.getAttribute('href')).toBe('#compte'));
    await cliquer('#epargne .card');
    expect(page.querySelector('.parcours')).toBeNull();
  });

  it("annonce le lien d'activation et le Livret A à la fin du parcours", async () => {
    await cliquer('.top .btn.p');
    for (let etape = 1; etape < 6; etape++) {
      await cliquer('.pv.on .pbtn:not(.ghost)');
    }
    const fin = etapeVisible()?.textContent ?? '';
    expect(fin).toContain("lien d'activation");
    expect(fin).toContain('Livret A');
    expect(fin).not.toContain('mot de passe provisoire');
  });

  it("repart de l'étape 1 à chaque ouverture", async () => {
    await cliquer('.top .btn.p');
    await cliquer('.pv.on .pbtn:not(.ghost)');
    await cliquer('.pbar a');
    await cliquer('.top .btn.p');
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 1 sur 6');
  });
});
