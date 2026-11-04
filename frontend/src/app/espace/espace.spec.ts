import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Espace } from './espace';

describe('Espace', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Espace],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("affiche l'espace quand la session est valide", async () => {
    const fixture = TestBed.createComponent(Espace);
    const requete = http.expectOne('/api/backend/connexion/session');
    expect(requete.request.withCredentials).toBe(true);
    requete.flush({ identifiant: 'ABC' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('ABC');
  });

  it('renvoie vers la connexion sans rien afficher si la session est absente', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Espace);
    http.expectOne('/api/backend/connexion/session').flush('', { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(navigation).toHaveBeenCalledWith('/connexion');
    expect((fixture.nativeElement as HTMLElement).querySelector('main')).toBeNull();
  });
});
