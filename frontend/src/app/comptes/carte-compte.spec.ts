import { TestBed } from '@angular/core/testing';
import { CarteCompte } from './carte-compte';
import { Compte, formaterDate, formaterSolde, libelleStatut } from './comptes';

const COMPTE: Compte = {
  id: 1,
  ibanMasque: 'FR76 •••• •••• 0105',
  statut: 'ACTIF',
  soldeCentimes: 124050,
  soldeMisAJourLe: '2026-11-05T15:10:00Z',
  premierVersementAttendu: false,
};

const NBSP = ' ';

async function afficher(compte: Compte | null): Promise<HTMLElement> {
  const fixture = TestBed.createComponent(CarteCompte);
  fixture.componentRef.setInput('compte', compte);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('formaterSolde', () => {
  it.each([
    [0, `0,00${NBSP}€`],
    [5, `0,05${NBSP}€`],
    [99, `0,99${NBSP}€`],
    [100, `1,00${NBSP}€`],
    [124050, `1${NBSP}240,50${NBSP}€`],
    [123456789, `1${NBSP}234${NBSP}567,89${NBSP}€`],
  ])('convertit %i centimes en euros', (centimes, attendu) => {
    expect(formaterSolde(centimes)).toBe(attendu);
  });
});

describe('formaterDate', () => {
  it("affiche l'heure de Paris, en hiver comme en été", () => {
    expect(formaterDate('2026-11-05T15:10:00Z')).toBe('05/11/2026 à 16:10');
    expect(formaterDate('2026-07-05T15:10:00Z')).toBe('05/07/2026 à 17:10');
    expect(formaterDate('2026-11-05T23:05:00Z')).toBe('06/11/2026 à 00:05');
  });
});

describe('libelleStatut', () => {
  it('traduit les statuts connus et garde les autres', () => {
    expect(libelleStatut('ACTIF')).toBe('Actif');
    expect(libelleStatut('CLOTURE')).toBe('Clôturé');
    expect(libelleStatut('AUTRE')).toBe('AUTRE');
  });
});

describe('CarteCompte', () => {
  it('affiche le solde, sa date et le statut ouvert, sans message', async () => {
    const carte = await afficher(COMPTE);
    expect(carte.querySelector('.solde')?.textContent).toBe(`1${NBSP}240,50${NBSP}€`);
    expect(carte.querySelector('.stamp')?.textContent).toBe('Mis à jour le 05/11/2026 à 16:10');
    expect(carte.querySelector('.pill')?.textContent).toBe('Compte ouvert');
    expect(carte.querySelector('.msg')).toBeNull();
  });

  it('affiche 0,00 € sans erreur et invite au premier versement', async () => {
    const carte = await afficher({ ...COMPTE, soldeCentimes: 0, premierVersementAttendu: true });
    expect(carte.querySelector('.solde')?.textContent).toBe(`0,00${NBSP}€`);
    expect(carte.querySelector('.err')).toBeNull();
    expect(carte.querySelector('.msg')?.textContent).toBe(
      'Votre compte est ouvert. Vous pouvez effectuer un premier versement quand vous le souhaitez.',
    );
  });

  it('affiche 0,00 € et « Compte clôturé » sans date pour un compte clôturé', async () => {
    const carte = await afficher({ ...COMPTE, statut: 'CLOTURE', soldeCentimes: 0, soldeMisAJourLe: null });
    expect(carte.querySelector('.solde')?.textContent).toBe(`0,00${NBSP}€`);
    expect(carte.querySelector('.pill.off')?.textContent).toBe('Compte clôturé');
    expect(carte.querySelector('.msg')?.textContent).toBe('Compte clôturé');
    expect(carte.querySelector('.stamp')).toBeNull();
  });

  it.each([
    ['un solde refusé', { ...COMPTE, soldeCentimes: null, soldeMisAJourLe: null }],
    ['une erreur de lecture', null],
  ])('affiche seulement le message pour %s', async (_cas, compte) => {
    const carte = await afficher(compte);
    expect(carte.querySelector('.solde.err')?.textContent).toBe('Votre solde est momentanément indisponible');
    expect(carte.textContent).not.toContain('€');
    expect(carte.querySelector('.stamp')).toBeNull();
    expect(carte.querySelector('.pill')).toBeNull();
    expect(carte.querySelector('.msg')).toBeNull();
  });
});
