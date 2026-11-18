import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ConnexionService, SessionEnCours } from '../connexion';
import { PLAFOND_ATTEINT, SOUS_LE_MINIMUM } from './parametres';
import { memoriserPosition, reprendrePosition } from './position-page';
import { PresentationLivretA } from './presentation-livret-a';

const SESSION: SessionEnCours = { identifiant: '12345678', expireDansSecondes: 600 };
const SANS_SESSION = () => throwError(() => new HttpErrorResponse({ status: 401 }));

// CU-27 (US-18) : page publique de présentation du Livret A.
describe('PresentationLivretA', () => {
  let fixture: ComponentFixture<PresentationLivretA>;
  let page: HTMLElement;
  let session: () => Observable<SessionEnCours>;
  let defilement: ReturnType<typeof vi.spyOn>;

  const creer = async () => {
    TestBed.configureTestingModule({
      imports: [PresentationLivretA],
      providers: [provideRouter([]), { provide: ConnexionService, useValue: { session: () => session() } }],
    });
    fixture = TestBed.createComponent(PresentationLivretA);
    page = fixture.nativeElement;
    await fixture.whenStable();
  };
  const texte = () => page.textContent!.replace(/\s+/g, ' ');
  const liens = (libelle: string) =>
    Array.from(page.querySelectorAll<HTMLAnchorElement>('a')).filter((a) => a.textContent!.trim() === libelle);
  const saisir = async (selecteur: string, valeur: string) => {
    const champ = page.querySelector<HTMLInputElement>(selecteur)!;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };
  const resultat = () => page.querySelector('.resultat')!.textContent!.replace(/\s+/g, ' ');

  beforeEach(() => {
    session = SANS_SESSION;
    reprendrePosition();
    defilement = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
  });

  afterEach(() => vi.restoreAllMocks());

  it("propose « Ouvrir mon Livret A » en haut, dans le bandeau, la barre latérale et en bas (critère 1)", async () => {
    await creer();
    const ouvrir = liens('Ouvrir mon Livret A');
    expect(ouvrir.length).toBe(4);
    ouvrir.forEach((a) => expect(a.getAttribute('href')).toBe('/livret-a'));
    expect(page.querySelector('.entete')!.textContent).toContain('Ouvrir mon Livret A');
    expect(page.querySelector('.bandeau')!.textContent).toContain('Ouvrir mon Livret A');
    expect(page.querySelector('aside.lateral')!.textContent).toContain('Ouvrir mon Livret A');
    expect(page.querySelector('.appel')!.textContent).toContain('Ouvrir mon Livret A');
  });

  it('non connectée : « Me connecter » mène à la connexion puis au parcours (critère 7)', async () => {
    await creer();
    const connecter = liens('Me connecter');
    expect(connecter.length).toBe(2);
    connecter.forEach((a) => expect(a.getAttribute('href')).toBe('/connexion?suite=%2Flivret-a'));
  });

  it('connectée : pas de bouton « Me connecter »', async () => {
    session = () => of(SESSION);
    await creer();
    expect(liens('Me connecter').length).toBe(0);
  });

  it('affiche taux net, date d’effet, plafond, exonération, disponibilité et minimum (critère 2)', async () => {
    await creer();
    const contenu = texte();
    expect(contenu).toContain('Taux en vigueur depuis le 1er août 2026');
    expect(contenu).toContain('1,70 % net / an, depuis le 1er août 2026');
    expect(contenu).toContain('22 950 € de versements, hors intérêts capitalisés');
    expect(contenu).toContain('exonérés d’impôt sur le revenu et de prélèvements sociaux');
    expect(contenu).toContain('disponible à tout moment');
    expect(contenu).toContain('10 € minimum à l’ouverture');
    expect(contenu).not.toContain('brut / an');
  });

  it('rappelle la règle du Livret A unique et la révision des taux (critère 3)', async () => {
    await creer();
    expect(texte()).toContain('Un seul Livret A par personne, toutes banques confondues.');
    expect(texte()).toContain('révisables au 1er février et au 1er août');
  });

  it('simule les intérêts annuels avec la mention « estimation indicative » (critère 4)', async () => {
    await creer();
    expect(resultat()).toContain('85,00 €');
    expect(resultat()).toContain('Estimation indicative');
    await saisir('#montant', '10000');
    expect(resultat()).toContain('170,00 €');
    expect(page.querySelector<HTMLInputElement>('input[type=range]')!.value).toBe('10000');
  });

  it('le curseur met aussi à jour le résultat', async () => {
    await creer();
    await saisir('input[type=range]', '2000');
    expect(resultat()).toContain('34,00 €');
  });

  it('ramène un montant au-dessus du plafond à 22 950 €, avec un message', async () => {
    await creer();
    await saisir('#montant', '50000');
    expect(resultat()).toContain(PLAFOND_ATTEINT);
    expect(resultat()).toContain('390,15 €');
  });

  it('sous 10 €, rappelle le minimum sans résultat ; vide, invite à saisir', async () => {
    await creer();
    await saisir('#montant', '5');
    expect(resultat()).toContain(SOUS_LE_MINIMUM);
    expect(resultat()).not.toContain('Intérêts estimés');
    await saisir('#montant', '');
    expect(resultat()).toContain('Saisissez un montant');
  });

  it('« Ouvrir mon Livret A » mémorise la position puis ouvre le parcours (critères 6 et 8)', async () => {
    await creer();
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(640);
    liens('Ouvrir mon Livret A')[2].click();
    expect(navigation).toHaveBeenCalledWith('/livret-a');
    expect(reprendrePosition()).toBe(640);
  });

  it('« Me connecter » mémorise aussi la position', async () => {
    await creer();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(320);
    liens('Me connecter')[0].click();
    expect(reprendrePosition()).toBe(320);
  });

  it('au retour du parcours, revient à la position mémorisée (critère 8)', async () => {
    memoriserPosition(640);
    await creer();
    expect(defilement).toHaveBeenCalledWith(0, 640);
    expect(reprendrePosition()).toBe(0);
  });

  it("une nouvelle visite s'ouvre en haut de page", async () => {
    await creer();
    expect(defilement).toHaveBeenCalledWith(0, 0);
  });

  it('les liens du menu font défiler la page sans la quitter', async () => {
    await creer();
    const cible = page.querySelector<HTMLElement>('#simulateur')!;
    cible.scrollIntoView = vi.fn();
    document.body.appendChild(page);
    const lien = page.querySelector<HTMLAnchorElement>('.entete nav a[href="#simulateur"]')!;
    const evenement = new MouseEvent('click', { cancelable: true });
    lien.dispatchEvent(evenement);
    expect(evenement.defaultPrevented).toBe(true);
    expect(cible.scrollIntoView).toHaveBeenCalled();
    page.remove();
  });

  it('donne accès aux mentions légales, à la protection des données et à l’accessibilité (critère 10)', async () => {
    await creer();
    const pied = Array.from(page.querySelectorAll('footer .liens-legaux a')).map((a) => a.getAttribute('href'));
    expect(pied).toEqual(['/mentions-legales', '/protection-des-donnees', '/accessibilite']);
  });

  it('compare le Livret A aux autres comptes, avec ses paramètres', async () => {
    await creer();
    const ligne = page.querySelector('tr.livret-a')!.textContent!;
    expect(ligne).toContain('1,70 % net');
    expect(ligne).toContain('22 950 €');
    expect(page.querySelectorAll('tbody tr').length).toBe(5);
  });

  it('ne présente pas le Livret Jeune dans le comparatif', async () => {
    await creer();
    expect(page.querySelector('.comparatif')!.textContent).not.toContain('Livret Jeune');
  });

  it('ne montre pas la mention « maquette de validation »', async () => {
    await creer();
    expect(texte().toLowerCase()).not.toContain('maquette');
  });
});
