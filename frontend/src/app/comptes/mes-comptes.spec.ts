import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { MesComptes } from './mes-comptes';

describe('MesComptes', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MesComptes],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function afficher(reponse: unknown, options?: { status: number; statusText: string }) {
    const fixture = TestBed.createComponent(MesComptes);
    const requete = http.expectOne('/api/backend/comptes');
    expect(requete.request.withCredentials).toBe(true);
    requete.flush(reponse as never, options);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche les comptes reçus avec un lien vers leur détail', async () => {
    const page = await afficher([{ id: 5, ibanMasque: 'FR76 •••• •••• 0105', statut: 'ACTIF' }]);
    expect(page.textContent).toContain('FR76 •••• •••• 0105');
    expect(page.textContent).toContain('Actif');
    expect(page.querySelector('a.compte')?.getAttribute('href')).toBe('/comptes/5');
  });

  it("invite à ouvrir un compte quand la cliente n'en a pas", async () => {
    const page = await afficher([]);
    expect(page.textContent).toContain("Vous n'avez pas encore de compte.");
    expect(page.textContent).toContain('Ouvrir mon compte');
  });

  it('renvoie vers la connexion si la cliente n’est pas connectée', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await afficher('', { status: 401, statusText: 'Unauthorized' });
    expect(navigation).toHaveBeenCalledWith('/connexion');
  });

  it('affiche un message en cas de panne, sans renvoyer vers la connexion', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const page = await afficher('', { status: 500, statusText: 'Erreur' });
    expect(page.textContent).toContain('Vos comptes sont momentanément indisponibles.');
    expect(navigation).not.toHaveBeenCalled();
  });
});
