import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BoutonFonctionnalite } from './bouton-fonctionnalite';
import { EtatsFonctionnalites, FonctionnalitesService } from './fonctionnalites';
import { FonctionnalitesTest } from './fonctionnalites-test';
import { outilsDeTest } from './outils-test';

@Component({
  imports: [BoutonFonctionnalite],
  template: `
    <app-bouton-fonctionnalite cle="simulateur-per" classe="go">Simuler mon PER →</app-bouton-fonctionnalite>
    <app-bouton-fonctionnalite cle="outils-test" classe="btn o sm" [phraseVisible]="false">Outils</app-bouton-fonctionnalite>
  `,
})
class Hote {}

async function afficher(etats: EtatsFonctionnalites) {
  TestBed.configureTestingModule({
    imports: [Hote],
    providers: [provideRouter([]), { provide: FonctionnalitesService, useValue: { etats: () => of(etats) } }],
  });
  const fixture = TestBed.createComponent(Hote);
  fixture.detectChanges();
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

// Lot 3 : bouton unique de fonctionnalité, deux états.
describe('BoutonFonctionnalite', () => {
  it('« Bientôt » : libellé gardé, désactivé, badge et phrase, annoncé « Bientôt disponible » ; un clic ne fait rien', async () => {
    const page = await afficher({ 'simulateur-per': false });
    const bouton = page.querySelector<HTMLButtonElement>('[data-fonctionnalite="simulateur-per"]')!;
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    expect(bouton.tagName).toBe('BUTTON');
    expect(bouton.classList).toContain('go');
    expect(bouton.classList).toContain('bientot');
    expect(bouton.getAttribute('aria-disabled')).toBe('true');
    expect(bouton.textContent?.replace(/\s+/g, ' ').trim()).toBe('Simuler mon PER →, Bientôt disponible');
    const phrase = page.querySelector(`#${bouton.getAttribute('aria-describedby')}`)!;
    expect(phrase.textContent).toBe('Simulez votre PER avant de souscrire : disponible prochainement.');
    expect(phrase.className).toBe('phrase-bientot');
    bouton.click();
    expect(navigation).not.toHaveBeenCalled();
  });

  it('« Disponible » : interrupteur activé et page développée, lien vers la page ; phrase masquée à l’écran si demandé', async () => {
    const page = await afficher({ 'simulateur-per': true, 'outils-test': true });
    const lien = page.querySelector<HTMLAnchorElement>('[data-fonctionnalite="outils-test"]')!;
    expect(lien.tagName).toBe('A');
    expect(lien.getAttribute('href')).toBe('/test/fonctionnalites');
    expect(lien.getAttribute('data-etat')).toBe('disponible');
    // Interrupteur activé mais page pas encore développée : reste « Bientôt » (le test des liens le signale).
    expect(page.querySelector('[data-fonctionnalite="simulateur-per"]')?.getAttribute('data-etat')).toBe('bientot');
  });

  it('phrase masquée à l’écran (en-tête) : lue seulement par les lecteurs d’écran', async () => {
    const page = await afficher({});
    const bouton = page.querySelector('[data-fonctionnalite="outils-test"]')!;
    expect(page.querySelector(`#${bouton.getAttribute('aria-describedby')}`)?.className).toBe('visuellement-cache');
  });
});

describe('FonctionnalitesService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));

  it('lit les interrupteurs une fois ; sans réponse, tout reste « Bientôt »', () => {
    const service = TestBed.inject(FonctionnalitesService);
    const lus: EtatsFonctionnalites[] = [];
    service.etats().subscribe((e) => lus.push(e));
    service.etats().subscribe((e) => lus.push(e));
    TestBed.inject(HttpTestingController).expectOne('/api/backend/fonctionnalites').flush(null, { status: 503, statusText: 'Indisponible' });
    expect(lus).toEqual([{}, {}]);
  });
});

describe('Outils de test (D4)', () => {
  function garde(etats: () => ReturnType<FonctionnalitesService['etats']>) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: FonctionnalitesService, useValue: { etats } }] });
    let resultat: unknown;
    TestBed.runInInjectionContext(() => (outilsDeTest({} as never, {} as never) as ReturnType<typeof of>).subscribe((r) => (resultat = r)));
    return resultat;
  }

  it('les pages /test/... s’ouvrent si « Outils de test » est activé, sinon retour à l’accueil', () => {
    expect(garde(() => of({ 'outils-test': true }))).toBe(true);
    TestBed.resetTestingModule();
    expect(String(garde(() => of({})))).toBe('/');
  });

  it('page /test/fonctionnalites : état de chaque interrupteur et accès aux pages de test', async () => {
    TestBed.configureTestingModule({
      imports: [FonctionnalitesTest],
      providers: [provideRouter([]), { provide: FonctionnalitesService, useValue: { etats: () => of({ messagerie: false, 'outils-test': true }) } }],
    });
    const fixture = TestBed.createComponent(FonctionnalitesTest);
    fixture.detectChanges();
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const lignes = [...page.querySelectorAll('[data-test="interrupteurs"] tbody tr')].map((tr) => [...tr.querySelectorAll('td')].map((td) => td.textContent).join(' '));
    expect(lignes).toEqual(['messagerie Bientôt', 'outils-test Disponible']);
    expect(page.querySelectorAll('[data-test="pages"] a').length).toBe(12);
  });

  it('interrupteurs illisibles (le service renvoie une liste vide) : message', async () => {
    TestBed.configureTestingModule({
      imports: [FonctionnalitesTest],
      providers: [provideRouter([]), { provide: FonctionnalitesService, useValue: { etats: () => of({}) } }],
    });
    const fixture = TestBed.createComponent(FonctionnalitesTest);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Interrupteurs indisponibles.');
  });
});
