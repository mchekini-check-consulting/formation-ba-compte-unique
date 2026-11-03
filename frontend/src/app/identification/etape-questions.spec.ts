import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EtapeQuestions } from './etape-questions';
import { MESSAGE_ANNULATION, Reponses } from './regles';

describe('EtapeQuestions', () => {
  let fixture: ComponentFixture<EtapeQuestions>;
  let page: HTMLElement;
  let validees: Reponses | undefined;
  let annulation: string | undefined;

  const repondre = async (question: string, choix: string) => {
    page.querySelector<HTMLInputElement>(`input[name=${question}][value="${choix}"]`)!.click();
    await fixture.whenStable();
  };
  const saisir = async (selecteur: string, valeur: string) => {
    const champ = page.querySelector<HTMLInputElement>(selecteur)!;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };
  const continuer = async () => {
    page.querySelector<HTMLButtonElement>('.actions .pbtn:not(.ghost)')!.click();
    await fixture.whenStable();
  };
  const boutonInactif = () =>
    page.querySelector('.actions .pbtn:not(.ghost)')!.getAttribute('aria-disabled') === 'true';
  const toutRepondre = async (pep = 'Non') => {
    await repondre('origineFonds', 'Salaire');
    await repondre('objetRelation', 'Épargne');
    await repondre('pep', pep);
    await repondre('residenceEtranger', 'Non');
    page.querySelector<HTMLInputElement>('#consentement-identite')!.click();
    await fixture.whenStable();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [EtapeQuestions] }).compileComponents();
    fixture = TestBed.createComponent(EtapeQuestions);
    validees = undefined;
    annulation = undefined;
    fixture.componentInstance.valide.subscribe((r) => (validees = r));
    fixture.componentInstance.annule.subscribe((m) => (annulation = m));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  it('pose les 4 questions obligatoires et le consentement', () => {
    expect(page.querySelectorAll('legend').length).toBe(4);
    expect(page.textContent).toContain('personne politiquement exposée (PEP)');
    expect(page.textContent).toContain("J'ai lu et j'accepte le traitement de mes données d'identité (obligatoire).");
  });

  it('garde le bouton grisé et affiche un message sous chaque question sans réponse', async () => {
    expect(boutonInactif()).toBe(true);
    await continuer();
    expect(page.querySelectorAll('.msg-err').length).toBe(4);
    expect(page.textContent).toContain("Vous devez accepter le traitement de vos données d'identité pour continuer.");
    expect(validees).toBeUndefined();
  });

  it('enregistre les réponses complètes, sans le consentement facultatif', async () => {
    await toutRepondre();
    expect(boutonInactif()).toBe(false);
    await continuer();
    expect(validees).toMatchObject({
      origineFonds: 'Salaire',
      objetRelation: 'Épargne',
      pep: 'Non',
      residenceEtranger: 'Non',
      consentementIdentite: true,
      consentementSituation: false,
    });
  });

  it('annule la demande si la cliente est une personne politiquement exposée', async () => {
    await toutRepondre('Oui');
    expect(page.querySelector('.notice.err')?.textContent).toContain('annulée');
    await continuer();
    expect(annulation).toBe(MESSAGE_ANNULATION);
    expect(validees).toBeUndefined();
  });

  it("demande de préciser l'origine « Autre »", async () => {
    await toutRepondre();
    await repondre('origineFonds', 'Autre');
    expect(boutonInactif()).toBe(true);
    await saisir('#origine-autre', 'Donation');
    expect(boutonInactif()).toBe(false);
  });

  it('exige chaque pays et numéro fiscal en cas de résidence hors de France', async () => {
    await toutRepondre();
    await repondre('residenceEtranger', 'Oui');
    await continuer();
    expect(page.textContent).toContain('Indiquez chaque pays de résidence fiscale');
    expect(page.textContent).toContain('Ce champ est obligatoire');
    await saisir('#pays-0', 'Belgique');
    await saisir('#nif-0', 'BE123');
    [...page.querySelectorAll<HTMLButtonElement>('.lien')].find((b) => b.textContent?.includes('Ajouter'))!.click();
    await fixture.whenStable();
    expect(page.querySelector('#pays-1')).not.toBeNull();
    expect(boutonInactif()).toBe(true);
    [...page.querySelectorAll<HTMLButtonElement>('.lien')].filter((b) => b.textContent?.includes('Retirer')).at(-1)!.click();
    await fixture.whenStable();
    expect(page.querySelector('#pays-1')).toBeNull();
    await continuer();
    expect(validees?.residences).toEqual([{ pays: 'Belgique', nif: 'BE123' }]);
  });

  it('accepte le consentement facultatif et signale le retour', async () => {
    let retour = false;
    fixture.componentInstance.retour.subscribe(() => (retour = true));
    page.querySelector<HTMLInputElement>('#consentement-situation')!.click();
    await fixture.whenStable();
    page.querySelector<HTMLButtonElement>('.pbtn.ghost')!.click();
    expect(retour).toBe(true);
  });
});
