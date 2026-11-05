import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  erreurDeSaisie,
  MESSAGE_CONFIRMATION_DIFFERENTE,
  MESSAGE_CONFIRMATION_VIDE,
  MESSAGE_TROP_COURT,
  SaisieMotDePasse,
  SaisieMotDePasseComponent,
} from './saisie-mot-de-passe';

const CONFORME = 'Abcdefghij1!';

// Complément de l'US-09 : saisie et confirmation du mot de passe.
describe('SaisieMotDePasseComponent', () => {
  let fixture: ComponentFixture<SaisieMotDePasseComponent>;
  let page: HTMLElement;
  let emis: SaisieMotDePasse[];

  const champ = (nom: string) => page.querySelector<HTMLInputElement>(`input[name="${nom}"]`)!;
  const valider = async (motDePasse: string, confirmation: string) => {
    champ('motDePasse').value = motDePasse;
    champ('confirmation').value = confirmation;
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  const alerte = () => page.querySelector('[role="alert"]')?.textContent ?? '';

  beforeEach(async () => {
    fixture = TestBed.createComponent(SaisieMotDePasseComponent);
    fixture.componentRef.setInput('libelle', 'Enregistrer');
    emis = [];
    fixture.componentInstance.valider.subscribe((saisie) => emis.push(saisie));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  it('affiche les deux champs, caractères masqués', () => {
    expect(page.textContent).toContain('Nouveau mot de passe');
    expect(page.textContent).toContain('Confirmer le mot de passe');
    expect(champ('motDePasse').type).toBe('password');
    expect(champ('confirmation').type).toBe('password');
    expect(page.querySelector('button[type=submit]')?.textContent).toContain('Enregistrer');
  });

  it('transmet les deux saisies quand elles sont identiques et conformes', async () => {
    await valider(CONFORME, CONFORME);
    expect(emis).toEqual([{ motDePasse: CONFORME, confirmation: CONFORME }]);
    expect(alerte()).toBe('');
  });

  it('signale une confirmation différente, sans rien transmettre et sans vider les champs', async () => {
    await valider(CONFORME, 'Abcdefghij1?');
    expect(alerte()).toContain(MESSAGE_CONFIRMATION_DIFFERENTE);
    expect(emis).toEqual([]);
    expect(champ('motDePasse').value).toBe(CONFORME);
    await valider(CONFORME, CONFORME);
    expect(emis).toHaveLength(1);
    expect(alerte()).toBe('');
  });

  it('signale une confirmation vide', async () => {
    await valider(CONFORME, '');
    expect(alerte()).toContain(MESSAGE_CONFIRMATION_VIDE);
    expect(emis).toEqual([]);
  });

  it('signale les règles quelle que soit la confirmation', async () => {
    await valider('Court1!', 'autre');
    expect(alerte()).toContain(MESSAGE_TROP_COURT);
    expect(erreurDeSaisie({ motDePasse: 'abcdefghij1!', confirmation: '' })).toBe('Au moins une majuscule');
    expect(erreurDeSaisie({ motDePasse: 'ABCDEFGHIJ1!', confirmation: '' })).toBe('Au moins une minuscule');
    expect(erreurDeSaisie({ motDePasse: 'Abcdefghijk!', confirmation: '' })).toBe('Au moins un chiffre');
    expect(erreurDeSaisie({ motDePasse: 'Abcdefghijk1', confirmation: '' })).toBe('Au moins un caractère spécial');
    expect(emis).toEqual([]);
  });

  it("n'empêche pas de coller un mot de passe (gestionnaires de mots de passe)", () => {
    for (const nom of ['motDePasse', 'confirmation']) {
      const collage = new Event('paste', { bubbles: true, cancelable: true });
      champ(nom).dispatchEvent(collage);
      expect(collage.defaultPrevented).toBe(false);
    }
  });

  it('affiche le message du serveur', async () => {
    fixture.componentRef.setInput('erreurServeur', 'Ce mot de passe est trop courant');
    await fixture.whenStable();
    expect(alerte()).toContain('Ce mot de passe est trop courant');
  });

  it('affiche puis masque chaque champ séparément avec son bouton « Afficher »', async () => {
    const oeil = (nom: string) => page.querySelector<HTMLButtonElement>(`button.eye[aria-controls="${nom}"]`)!;
    oeil('motDePasse').click();
    await fixture.whenStable();
    expect(champ('motDePasse').type).toBe('text');
    expect(champ('confirmation').type).toBe('password');
    expect(oeil('motDePasse').textContent).toContain('Masquer');
    expect(oeil('motDePasse').getAttribute('aria-pressed')).toBe('true');
    oeil('confirmation').click();
    oeil('motDePasse').click();
    await fixture.whenStable();
    expect(champ('motDePasse').type).toBe('password');
    expect(champ('confirmation').type).toBe('text');
    expect(emis).toEqual([]);
  });
});
