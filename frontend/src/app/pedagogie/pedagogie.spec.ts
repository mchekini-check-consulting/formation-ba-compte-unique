import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SupportsService } from '../supports/supports';
import { GLOSSAIRE, InfoBulle } from './info-bulle';
import { Mention } from './mention';

@Component({
  imports: [InfoBulle, Mention],
  template: `<label><input type="radio" /> Gestion pilotée <app-info terme="gestion-pilotee" /></label>
    <app-mention cle="perte-capital-uc" /><app-mention cle="non-cote" />`,
})
class Hote {}

// Lot 7 (PO, 28/11/2026) : info-bulles d'une phrase et mentions réglementaires en paramètres.
describe('Pédagogie (lot 7)', () => {
  async function page() {
    TestBed.configureTestingModule({
      imports: [Hote],
      providers: [
        { provide: SupportsService, useValue: { parametresPublics: () => of({ mentions: { 'perte-capital-uc': 'Texte du paramètre.' } }) } },
      ],
    });
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('les huit termes demandés ont une phrase, chacune terminée par un point', () => {
    expect(Object.keys(GLOSSAIRE)).toEqual([
      'unite-de-compte', 'valeur-liquidative', 'niveau-risque', 'non-cote', 'fonds-euros', 'gestion-pilotee', 'gestion-libre', 'plafond-retraite',
    ]);
    for (const e of Object.values(GLOSSAIRE)) {
      expect(e.phrase.endsWith('.')).toBe(true);
    }
  });

  it('info-bulle : phrase reliée au bouton, affichée au clic sans cocher l’option, refermée par Échap', async () => {
    const { fixture, page: p } = await page();
    const bouton = p.querySelector<HTMLButtonElement>('[data-test="info"]')!;
    const texte = p.querySelector('[role="tooltip"]')!;
    expect(bouton.getAttribute('aria-describedby')).toBe(texte.id);
    expect(texte.textContent).toBe(GLOSSAIRE['gestion-pilotee'].phrase);
    bouton.click();
    await fixture.whenStable();
    expect(texte.classList.contains('visible')).toBe(true);
    expect(p.querySelector<HTMLInputElement>('input[type="radio"]')!.checked).toBe(false);
    bouton.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();
    expect(texte.classList.contains('visible')).toBe(false);
  });

  it('mention : texte du paramètre affiché ; paramètre vide : rien', async () => {
    const { page: p } = await page();
    expect(p.querySelector('[data-mention="perte-capital-uc"]')?.textContent).toBe('Texte du paramètre.');
    expect(p.querySelector('[data-mention="non-cote"]')).toBeNull();
  });
});
