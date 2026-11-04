import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MESSAGE_INDISPONIBLE } from '../identification/regles';
import { DELAI_RENVOI_MS, EtapeCompteOuvert, INTERVALLE_SUIVI_MS } from './etape-compte-ouvert';
import { OuvertureReponse } from './ouverture';

const URL = '/api/backend/dossiers/d1/ouverture';

function ouverture(autres: Partial<OuvertureReponse> = {}): OuvertureReponse {
  return {
    statutDossier: 'COMPTE_OUVERT',
    ibanMasque: 'FR76 •••• •••• 1234',
    emailMasque: 'm•••@exemple.fr',
    mobileMasque: '06 •• •• •• 78',
    envoiEmail: 'ENVOYE',
    envoiSms: 'ENVOYE',
    ...autres,
  };
}

describe('EtapeCompteOuvert (CU-13)', () => {
  let fixture: ComponentFixture<EtapeCompteOuvert>;
  let page: HTMLElement;
  let http: HttpTestingController;

  const bouton = (texte: string) =>
    [...page.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(texte));

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [EtapeCompteOuvert],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EtapeCompteOuvert);
    fixture.componentRef.setInput('dossierId', 'd1');
    fixture.detectChanges();
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.useRealTimers();
  });

  const afficher = (reponse: OuvertureReponse) => {
    http.expectOne(URL).flush(reponse);
    fixture.detectChanges();
  };

  it('confirme le compte ouvert avec IBAN, adresse et numéro masqués', () => {
    afficher(ouverture());
    expect(page.querySelector('h2')?.textContent).toContain('Votre compte courant est ouvert');
    expect(page.textContent).toContain('FR76 •••• •••• 1234');
    expect(page.textContent).toContain(
      'Votre identifiant vous a été envoyé par e-mail à m•••@exemple.fr.',
    );
    expect(page.textContent).toContain("Votre lien d'activation vous a été envoyé par SMS au 06 •• •• •• 78.");
    expect(bouton('Renvoyer')).toBeUndefined();
    expect(page.textContent).toContain('Livret A');
  });

  it('propose le renvoi du canal en échec et désactive le bouton 60 secondes', () => {
    afficher(ouverture({ envoiSms: 'ECHEC' }));
    expect(bouton('Renvoyer mon identifiant')).toBeUndefined();
    bouton('Renvoyer mon lien')!.click();
    fixture.detectChanges();
    const renvoi = http.expectOne({ method: 'POST', url: '/api/backend/dossiers/d1/envois/SMS' });
    renvoi.flush(ouverture({ envoiSms: 'EN_ECHEC' }));
    fixture.detectChanges();
    expect(bouton('Renvoyer mon lien')!.disabled).toBe(true);
    expect(page.textContent).toContain('Le renvoi a échoué');
    vi.advanceTimersByTime(DELAI_RENVOI_MS);
    fixture.detectChanges();
    expect(bouton('Renvoyer mon lien')!.disabled).toBe(false);
  });

  it("confirme le renvoi de l'identifiant", () => {
    afficher(ouverture({ envoiEmail: 'EN_ECHEC' }));
    bouton('Renvoyer mon identifiant')!.click();
    http.expectOne('/api/backend/dossiers/d1/envois/EMAIL').flush(ouverture());
    fixture.detectChanges();
    expect(page.textContent).toContain('Votre identifiant vous a été renvoyé par e-mail.');
  });

  it("suit la création en cours jusqu'au compte ouvert", () => {
    afficher(ouverture({ statutDossier: 'CONVENTION_SIGNEE', ibanMasque: null, envoiEmail: null, envoiSms: null }));
    expect(page.textContent).toContain('Création de votre compte courant en cours');
    vi.advanceTimersByTime(INTERVALLE_SUIVI_MS);
    afficher(ouverture());
    expect(page.querySelector('h2')?.textContent).toContain('Votre compte courant est ouvert');
    vi.advanceTimersByTime(INTERVALLE_SUIVI_MS * 2);
    http.expectNone(URL);
  });

  it('prévient quand la création est à relancer', () => {
    afficher(ouverture({ statutDossier: 'CREATION_A_RELANCER', ibanMasque: null }));
    expect(page.textContent).toContain('a pris du retard');
  });

  it('signale un service indisponible et termine', () => {
    let termine = false;
    fixture.componentInstance.valide.subscribe(() => (termine = true));
    http.expectOne(URL).flush('', { status: 500, statusText: 'Erreur' });
    fixture.detectChanges();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(MESSAGE_INDISPONIBLE);
    bouton('Terminer')!.click();
    expect(termine).toBe(true);
  });
});
