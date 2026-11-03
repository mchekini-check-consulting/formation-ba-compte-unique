import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EtapePiece } from './etape-piece';
import { IDENTITE_EXEMPLE } from './exemple';
import { MESSAGE_ANNULATION, MESSAGE_INDISPONIBLE } from './regles';

const URL = '/api/bouchons-identite/stripe-identity/verifications-document';

describe('EtapePiece', () => {
  let fixture: ComponentFixture<EtapePiece>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let validee: boolean;
  let annulation: string | undefined;

  const fichier = (nom: string, type: string, taille: number) =>
    new File([new Uint8Array(taille)], nom, { type });
  const choisir = async (f: File) => {
    const champ = page.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(champ, 'files', { value: [f], configurable: true });
    champ.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  };
  const envoyer = async () => {
    page.querySelector<HTMLButtonElement>('.actions .pbtn:not(.ghost)')!.click();
    await fixture.whenStable();
  };
  const reponse = (suite: string, motif: string | null, message: string | null) => ({
    statut: 'X',
    motif,
    suite,
    statutDossier: suite === 'ANNULER' ? 'ANNULE' : 'EN_COURS_DE_VERIFICATION',
    message,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EtapePiece],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EtapePiece);
    fixture.componentRef.setInput('dossierId', 'dossier-1');
    fixture.componentRef.setInput('identite', IDENTITE_EXEMPLE);
    validee = false;
    annulation = undefined;
    fixture.componentInstance.valide.subscribe(() => (validee = true));
    fixture.componentInstance.annule.subscribe((m) => (annulation = m));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  it("n'envoie rien tant qu'aucun fichier accepté n'est choisi", async () => {
    expect(page.querySelector<HTMLButtonElement>('.actions .pbtn:not(.ghost)')!.disabled).toBe(true);
    await choisir(fichier('piece.docx', 'application/msword', 10));
    expect(page.querySelector('.badge.err')?.textContent).toBe('Refusé');
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Format non accepté');
    expect(page.querySelector<HTMLButtonElement>('.actions .pbtn:not(.ghost)')!.disabled).toBe(true);
    http.expectNone(URL);
  });

  it('envoie la pièce avec la saisie et passe à la suite', async () => {
    await choisir(fichier('recto.jpg', 'image/jpeg', 1000));
    expect(page.querySelector('.badge.ok')?.textContent).toBe('Format valide');
    await envoyer();
    const requete = http.expectOne(URL);
    expect(requete.request.body).toEqual({
      dossierId: 'dossier-1',
      recto: 'PIECE_CONFORME_A_LA_SAISIE',
      verso: 'PIECE_CONFORME_A_LA_SAISIE',
      saisie: { nomNaissance: 'Dupont', prenoms: 'Marie Claire', dateNaissance: '1990-05-14', dateExpiration: '2031-06-30' },
    });
    requete.flush(reponse('POURSUIVRE', null, null));
    expect(validee).toBe(true);
  });

  it('utilise le scénario de test choisi', async () => {
    const select = page.querySelector<HTMLSelectElement>('#scenario-piece')!;
    select.value = 'PIECE_EXPIREE';
    select.dispatchEvent(new Event('change'));
    await choisir(fichier('recto.png', 'image/png', 1000));
    await envoyer();
    const requete = http.expectOne(URL);
    expect(requete.request.body.recto).toBe('PIECE_EXPIREE');
    requete.flush(reponse('ANNULER', 'document expiré', MESSAGE_ANNULATION));
    expect(annulation).toBe(`Motif : document expiré. ${MESSAGE_ANNULATION}`);
  });

  it('demande une autre photo si la pièce est illisible', async () => {
    await choisir(fichier('recto.png', 'image/png', 1000));
    await envoyer();
    http.expectOne(URL).flush(reponse('REPRENDRE', 'image illisible : photo floue, reflet ou bords coupés', null));
    await fixture.whenStable();
    expect(page.querySelector('.notice.warn')?.textContent).toContain('image illisible');
    expect(validee).toBe(false);
    expect(annulation).toBeUndefined();
  });

  it('indique une vérification impossible si le service ne répond pas', async () => {
    await choisir(fichier('recto.png', 'image/png', 1000));
    await envoyer();
    http.expectOne(URL).flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('.notice.warn')?.textContent).toContain(MESSAGE_INDISPONIBLE);
  });

  it('accepte un fichier déposé par glisser-déposer', async () => {
    const zone = page.querySelector('.drop')!;
    zone.dispatchEvent(new Event('dragover'));
    await fixture.whenStable();
    expect(zone.classList).toContain('survol');
    const depot = new Event('drop') as Event & { dataTransfer: unknown };
    depot.dataTransfer = { files: [fichier('recto.gif', 'image/gif', 10)] };
    zone.dispatchEvent(depot);
    await fixture.whenStable();
    expect(zone.classList).not.toContain('survol');
    expect(page.querySelector('.row')?.textContent).toContain('recto.gif');
  });
});
