import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AccesLayout } from './acces-layout';

@Component({
  imports: [AccesLayout],
  template: `
    <app-acces-layout titre="Mon titre" texte="Mon texte" [points]="points" retour="/connexion" libelleRetour="Retour">
      <p class="contenu">Formulaire</p>
    </app-acces-layout>
  `,
})
class Hote {
  points: [string, string][] = [
    ['Un', 'premier argument'],
    ['Deux', 'second argument'],
  ];
}

describe('AccesLayout', () => {
  it('affiche la présentation, les arguments, le lien de retour et le contenu de la carte', async () => {
    TestBed.configureTestingModule({ imports: [Hote], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('app-acces-layout')?.classList).toContain('acces');
    expect(page.querySelector('.intro h1')?.textContent).toBe('Mon titre');
    expect(page.querySelector('.intro p')?.textContent).toBe('Mon texte');
    const points = page.querySelectorAll('.points li');
    expect(points.length).toBe(2);
    expect(points[0].textContent).toContain('Un : premier argument');
    expect(page.querySelector('.logo')?.getAttribute('href')).toBe('/');
    expect(page.querySelector('.help')?.getAttribute('href')).toBe('/connexion');
    expect(page.querySelector('.help')?.textContent).toBe('Retour');
    expect(page.querySelector('.card .contenu')?.textContent).toBe('Formulaire');
  });
});
