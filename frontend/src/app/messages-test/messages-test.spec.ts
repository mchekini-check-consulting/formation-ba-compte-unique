import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MessagesTest } from './messages-test';

function creer(canal: 'emails' | 'sms') {
  TestBed.configureTestingModule({
    imports: [MessagesTest],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { data: { canal } } } },
    ],
  });
  return TestBed.createComponent(MessagesTest);
}

describe('MessagesTest (CU-87)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche le dernier SMS et rend son lien d\'activation cliquable', async () => {
    const fixture = creer('sms');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/backend/test/sms')
      .flush({
        canal: 'SMS',
        destinataire: '0612345678',
        objet: null,
        texte: 'Activez votre espace : http://localhost:4200/activation?jeton=abc',
        lien: 'http://localhost:4200/activation?jeton=abc',
        envoyeLe: '2026-11-04T10:00:00Z',
      });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('SMS de test');
    expect(page.querySelector<HTMLAnchorElement>('a.lien')?.href).toBe('http://localhost:4200/activation?jeton=abc');
    expect(page.textContent).toContain('0612345678');
  });

  it("affiche le dernier e-mail avec son objet, sans lien", async () => {
    const fixture = creer('emails');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/backend/test/emails')
      .flush({
        canal: 'EMAIL',
        destinataire: 'marie@example.com',
        objet: 'Votre identifiant',
        texte: 'Votre identifiant : 12345678',
        lien: null,
        envoyeLe: '2026-11-04T10:00:00Z',
      });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('E-mails de test');
    expect(page.textContent).toContain('Votre identifiant : 12345678');
    expect(page.querySelector('a.lien')).toBeNull();
  });

  it('indique qu\'il n\'y a aucun message, puis actualise', async () => {
    const fixture = creer('sms');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/backend/test/sms').flush('', { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Aucun message');
    page.querySelector('button')?.click();
    http.expectOne('/api/backend/test/sms').flush('', { status: 404, statusText: 'Not Found' });
  });
});
