import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ItemsPage } from './items-page';

describe('ItemsPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ItemsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('affiche les items du backend', async () => {
    const fixture = TestBed.createComponent(ItemsPage);
    http.expectOne('/api/items').flush([{ id: 1, nom: 'a', description: 'd', creeLe: '' }]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('li')?.textContent).toContain('a');
    expect(page.querySelector('li')?.textContent).toContain('d');
  });

  it('ajoute un item', async () => {
    const fixture = TestBed.createComponent(ItemsPage);
    http.expectOne('/api/items').flush([]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const nom = page.querySelector<HTMLInputElement>('input[name=nom]')!;
    nom.value = 'Premier';
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const requete = http.expectOne({ method: 'POST', url: '/api/items' });
    expect(requete.request.body).toEqual({ nom: 'Premier', description: '' });
    requete.flush({ id: 2, nom: 'Premier', description: null, creeLe: '' });
    await fixture.whenStable();
    expect(page.querySelector('li')?.textContent).toContain('Premier');
    expect(nom.value).toBe('');
  });

  it('signale une erreur du backend', async () => {
    const fixture = TestBed.createComponent(ItemsPage);
    http.expectOne('/api/items').flush('', { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role=alert]')?.textContent).toContain('503');
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne({ method: 'POST', url: '/api/items' }).flush('', { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('400');
  });
});
