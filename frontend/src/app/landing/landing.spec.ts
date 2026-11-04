import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { EtapeIdentite } from '../identification/etape-identite';
import { EtapePiece } from '../identification/etape-piece';
import { EtapeQuestions } from '../identification/etape-questions';
import { EtapeSelfie } from '../identification/etape-selfie';
import { IDENTITE_EXEMPLE } from '../identification/exemple';
import { EtapeCompteOuvert } from '../ouverture/etape-compte-ouvert';
import { EtapeSignature } from '../ouverture/etape-signature';
import { CLE_DOSSIER_EN_SIGNATURE } from '../ouverture/ouverture';
import { Landing } from './landing';

// Étapes 1 à 6 branchées sur les backends : leurs écrans sont testés à part, ici on simule
// seulement leur résultat.
const ETAPES_IDENTIFICATION: Type<unknown>[] = [
  EtapeIdentite,
  EtapePiece,
  EtapeSelfie,
  EtapeQuestions,
  EtapeSignature,
  EtapeCompteOuvert,
];

describe('Landing', () => {
  let fixture: ComponentFixture<Landing>;
  let page: HTMLElement;

  const cliquer = async (selecteur: string) => {
    page.querySelector<HTMLElement>(selecteur)!.click();
    await fixture.whenStable();
  };
  const etapeVisible = () => page.querySelector('.pv.on');
  const etapeIdentification = () =>
    ETAPES_IDENTIFICATION.map((type) => fixture.debugElement.query(By.directive(type))).find(Boolean)
      ?.componentInstance;
  // Passe l'étape affichée : résultat simulé.
  const avancer = async () => {
    const etape = etapeIdentification();
    if (etape) {
      etape.valide.emit(IDENTITE_EXEMPLE);
      await fixture.whenStable();
    } else {
      await cliquer('.pv.on .pbtn:not(.ghost)');
    }
  };

  beforeEach(async () => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Landing],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
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
    await avancer();
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 2 sur 6');
    expect(etapeVisible()?.textContent).toContain('Déposez votre pièce');
    await cliquer('.pv.on .pbtn.ghost');
    expect(etapeVisible()?.textContent).toContain('Mon identité');
    expect(page.querySelector<HTMLInputElement>('#id-nomNaissance')?.value).toBe('Dupont');
  });

  it('passe par la pièce, le selfie et les questions avant la signature', async () => {
    await cliquer('.top .btn.p');
    const titres = [];
    for (let etape = 1; etape <= 4; etape++) {
      titres.push(etapeVisible()?.querySelector('h2')?.textContent);
      await avancer();
    }
    expect(titres).toEqual(['Mon identité', 'Déposez votre pièce', 'Vérification de présence', 'Quelques questions']);
    expect(etapeVisible()?.textContent).toContain('Signer ma convention de compte');
  });

  it('arrête la demande annulée par un contrôle et ramène au site', async () => {
    await cliquer('.top .btn.p');
    await avancer();
    etapeIdentification().annule.emit("Votre demande d'ouverture en ligne est annulée.");
    await fixture.whenStable();
    expect(etapeVisible()?.querySelector('h2')?.textContent).toBe('Ouverture annulée');
    expect(etapeVisible()?.textContent).toContain("Votre demande d'ouverture en ligne est annulée.");
    expect(etapeIdentification()).toBeUndefined();
    await cliquer('.pv.on .pbtn');
    expect(page.querySelector('.hero')).not.toBeNull();
    await cliquer('.top .btn.p');
    expect(etapeVisible()?.querySelector('h2')?.textContent).toBe('Mon identité');
  });

  it('affiche le statut Annulé et le contrôle en échec quand sanctions ou PEP annulent la demande', async () => {
    await cliquer('.top .btn.p');
    etapeIdentification().annule.emit({
      message: "Votre demande d'ouverture en ligne est annulée.",
      controles: { sanctions: 'NOK', pep: 'OK' },
    });
    await fixture.whenStable();
    expect(etapeVisible()?.querySelector('h2')?.textContent).toBe('Ouverture annulée');
    expect(etapeVisible()?.querySelector('.status')?.textContent).toContain('Annulé');
    const pastilles = [...etapeVisible()!.querySelectorAll('.checklist .pill')];
    expect(pastilles.map((p) => p.textContent)).toEqual(['NOK', 'OK']);
    expect(pastilles[0].classList).toContain('ko');
    expect(pastilles[1].classList).toContain('ok');
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
      await avancer();
    }
    expect(etapeVisible()?.textContent).toContain('Votre convention est signée');
    await avancer();
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
      await avancer();
    }
    const fin = etapeVisible()?.textContent ?? '';
    expect(fin).toContain("lien d'activation");
    expect(fin).toContain('Livret A');
    expect(fin).not.toContain('mot de passe provisoire');
  });

  it('ramène le visiteur à sa position de défilement en revenant au site', async () => {
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(1234);
    await cliquer('#compte .btn.p');
    await avancer();
    await cliquer('.pbar a');
    expect(page.querySelector('.hero')).not.toBeNull();
    expect(window.scrollTo).toHaveBeenLastCalledWith(0, 1234);
  });

  it('indique que le visiteur garde la décision', () => {
    expect(page.querySelector('#optimisation')?.textContent).toContain('Vous gardez la décision');
    expect(page.querySelector('#optimisation')?.textContent).toContain('Règles validées');
  });

  it('donne accès aux pages légales depuis le bas de page', () => {
    const liens = [...page.querySelectorAll<HTMLAnchorElement>('footer a')].map((a) => [
      a.textContent?.trim(),
      a.getAttribute('href'),
    ]);
    expect(liens).toEqual([
      ['Mentions légales', '/mentions-legales'],
      ['Politique de protection des données', '/protection-des-donnees'],
      ['Accessibilité', '/accessibilite'],
    ]);
  });

  it("reprend la signature au même endroit après une fermeture de l'espace (CU-12)", async () => {
    localStorage.setItem(CLE_DOSSIER_EN_SIGNATURE, 'd-repris');
    await cliquer('.top .btn.p');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/backend/dossiers/d-repris/signature')
      .flush({ etat: 'ENVOYEE', enveloppeId: 'e', codesFaux: 1, essaisRestants: 2, convention: '', empreinteDocument: '' });
    await fixture.whenStable();
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 5 sur 6');
  });

  it('repart de zéro si la signature gardée est terminée ou introuvable', async () => {
    localStorage.setItem(CLE_DOSSIER_EN_SIGNATURE, 'd-fini');
    await cliquer('.top .btn.p');
    TestBed.inject(HttpTestingController).expectOne('/api/backend/dossiers/d-fini/signature').flush({ etat: 'SIGNEE' });
    await fixture.whenStable();
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 1 sur 6');
    expect(localStorage.getItem(CLE_DOSSIER_EN_SIGNATURE)).toBeNull();
    await cliquer('.pbar a');
    localStorage.setItem(CLE_DOSSIER_EN_SIGNATURE, 'd-inconnu');
    await cliquer('.top .btn.p');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/backend/dossiers/d-inconnu/signature')
      .flush('', { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 1 sur 6');
  });

  it("repart de l'étape 1 à chaque ouverture", async () => {
    await cliquer('.top .btn.p');
    await avancer();
    await cliquer('.pbar a');
    await cliquer('.top .btn.p');
    expect(page.querySelector('#pstep')?.textContent).toContain('Étape 1 sur 6');
  });
});
