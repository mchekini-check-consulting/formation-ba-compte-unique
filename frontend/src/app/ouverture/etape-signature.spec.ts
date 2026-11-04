import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MESSAGE_INDISPONIBLE } from '../identification/regles';
import { EtapeSignature, messageCodeFaux } from './etape-signature';
import { CLE_DOSSIER_EN_SIGNATURE, SignatureReponse } from './ouverture';

const BACKEND = '/api/backend/dossiers/d1/signature';
const DOCUSIGN = '/api/bouchons-identite/docusign/enveloppes';

function signature(etat: SignatureReponse['etat'], autres: Partial<SignatureReponse> = {}): SignatureReponse {
  return {
    etat,
    enveloppeId: etat === 'A_SIGNER' ? null : 'env-1',
    codesFaux: 0,
    essaisRestants: 3,
    nouveauCodePossible: false,
    motifAnnulation: null,
    signeeLe: null,
    convention: 'Convention de compte courant Hi-Tech Bank',
    empreinteDocument: 'a'.repeat(64),
    statutDossier: 'EN_COURS_DE_VERIFICATION',
    ...autres,
  };
}

describe('EtapeSignature (CU-12)', () => {
  let fixture: ComponentFixture<EtapeSignature>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let signee: boolean;
  let annulation: string | undefined;

  const bouton = (texte: string) =>
    [...page.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(texte));
  const cliquer = async (texte: string) => {
    bouton(texte)!.click();
    await fixture.whenStable();
  };
  const saisir = async (code: string) => {
    const champ = page.querySelector<HTMLInputElement>('#code-signature')!;
    champ.value = code;
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };
  const ouvrir = async (etat: SignatureReponse) => {
    http.expectOne(BACKEND).flush(etat);
    await fixture.whenStable();
  };
  /** Saisie du code : le bouchon Docusign répond, puis le backend applique les règles. */
  const signerAvec = async (code: string, docusign: string, backend: SignatureReponse) => {
    await saisir(code);
    await cliquer('Signer');
    const appel = http.expectOne(`${DOCUSIGN}/env-1/code`);
    expect(appel.request.body).toEqual({ code });
    appel.flush({ enveloppeId: 'env-1', resultat: docusign, message: '' });
    const resultat = http.expectOne(`${BACKEND}/resultat`);
    expect(resultat.request.body).toEqual({ resultat: docusign });
    resultat.flush(backend);
    await fixture.whenStable();
  };

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [EtapeSignature],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EtapeSignature);
    fixture.componentRef.setInput('dossierId', 'd1');
    signee = false;
    annulation = undefined;
    fixture.componentInstance.valide.subscribe(() => (signee = true));
    fixture.componentInstance.annule.subscribe((m) => (annulation = m));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  it('affiche la convention puis envoie le code par SMS et garde le dossier pour une reprise', async () => {
    await ouvrir(signature('A_SIGNER'));
    expect(page.textContent).toContain('Convention de compte courant Hi-Tech Bank');
    await cliquer('Recevoir mon code par SMS');
    const enveloppe = http.expectOne(DOCUSIGN);
    expect(enveloppe.request.body).toEqual({ dossierId: 'd1' });
    enveloppe.flush({ enveloppeId: 'env-1', resultat: 'ENVELOPPE_ENVOYEE', message: '' });
    const backend = http.expectOne(`${BACKEND}/enveloppe`);
    expect(backend.request.body).toEqual({ enveloppeId: 'env-1' });
    backend.flush(signature('ENVOYEE'));
    await fixture.whenStable();
    expect(page.querySelector('#code-signature')).not.toBeNull();
    expect(page.textContent).toContain('111111');
    expect(localStorage.getItem(CLE_DOSSIER_EN_SIGNATURE)).toBe('d1');
  });

  it('signe avec le bon code et passe à la suite', async () => {
    localStorage.setItem(CLE_DOSSIER_EN_SIGNATURE, 'd1');
    await ouvrir(signature('ENVOYEE'));
    await signerAvec('111111', 'SIGNATURE_VALIDE', signature('SIGNEE', { statutDossier: 'CONVENTION_SIGNEE' }));
    expect(signee).toBe(true);
    expect(localStorage.getItem(CLE_DOSSIER_EN_SIGNATURE)).toBeNull();
  });

  it('compte les codes faux et prévient avant la dernière tentative', async () => {
    await ouvrir(signature('ENVOYEE'));
    await signerAvec('000000', 'CODE_FAUX', signature('ENVOYEE', { codesFaux: 1, essaisRestants: 2 }));
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Il vous reste 2 essais');
    expect(signee).toBe(false);
    expect(messageCodeFaux(1)).toContain('1 essai.');
    expect(messageCodeFaux(0)).toContain('annulera la signature');
  });

  it('annule après la quatrième tentative avec le motif', async () => {
    await ouvrir(signature('ENVOYEE', { codesFaux: 3, essaisRestants: 0 }));
    await signerAvec(
      '000000',
      'CODE_FAUX',
      signature('ANNULEE', { motifAnnulation: '3 tentatives dépassées', statutDossier: 'ANNULE' }),
    );
    expect(annulation).toContain('3 tentatives dépassées');
  });

  it('propose un nouveau code quand le code a expiré', async () => {
    await ouvrir(signature('ENVOYEE'));
    await signerAvec('999999', 'CODE_EXPIRE', signature('EXPIREE', { nouveauCodePossible: true }));
    expect(page.textContent).toContain('Code expiré');
    await cliquer('Recevoir un nouveau code');
    http.expectOne(DOCUSIGN).flush({ enveloppeId: 'env-2', resultat: 'ENVELOPPE_ENVOYEE', message: '' });
    http.expectOne(`${BACKEND}/enveloppe`).flush(signature('ENVOYEE', { enveloppeId: 'env-2' }));
    await fixture.whenStable();
    expect(page.querySelector('#code-signature')).not.toBeNull();
  });

  it("demande une confirmation avant d'annuler la signature", async () => {
    await ouvrir(signature('ENVOYEE'));
    await cliquer('Annuler la signature');
    await cliquer('Non, continuer');
    expect(page.querySelector('[role="alertdialog"]')).toBeNull();
    await cliquer('Annuler la signature');
    await cliquer('Oui, annuler');
    http.expectOne(`${DOCUSIGN}/env-1/annulation`).flush({ enveloppeId: 'env-1', resultat: 'ANNULATION_DEMANDEE', message: '' });
    const resultat = http.expectOne(`${BACKEND}/resultat`);
    expect(resultat.request.body).toEqual({ resultat: 'ANNULATION_DEMANDEE' });
    resultat.flush(signature('ANNULEE', { motifAnnulation: 'Annulation demandée', statutDossier: 'ANNULE' }));
    await fixture.whenStable();
    expect(annulation).toContain('Annulation demandée');
  });

  it('refuse un code incomplet sans rien appeler', async () => {
    await ouvrir(signature('ENVOYEE'));
    await saisir('12');
    await cliquer('Signer');
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('6 chiffres');
  });

  it("signale un service indisponible", async () => {
    await ouvrir(signature('ENVOYEE'));
    await saisir('111111');
    await cliquer('Signer');
    http.expectOne(`${DOCUSIGN}/env-1/code`).flush('', { status: 503, statusText: 'Indisponible' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(MESSAGE_INDISPONIBLE);
  });

  it('signale une convention impossible à charger', async () => {
    http.expectOne(BACKEND).flush('', { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(MESSAGE_INDISPONIBLE);
  });
});
