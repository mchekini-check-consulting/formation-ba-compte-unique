import {
  DETAIL_TAUX_LIVRET_A,
  LIVRET_A,
  PLAFOND_ATTEINT,
  SOUS_LE_MINIMUM,
  TAUX_LIVRET_A,
  dateLongue,
  euros,
  eurosAuCentime,
  pourcentage,
  simuler,
} from './parametres';

// CU-27 : paramètres uniques du Livret A et simulateur d'intérêts.
describe('Paramètres du Livret A', () => {
  it('affiche le taux en vigueur en net (critère 2)', () => {
    expect(LIVRET_A.taux).toBe(1.7);
    expect(TAUX_LIVRET_A).toBe('1,70 %');
    expect(DETAIL_TAUX_LIVRET_A).toBe('net / an');
    expect(dateLongue(LIVRET_A.dateEffet)).toBe('1er août 2026');
  });

  it.each([
    [1.7, '1,70 %'],
    [3, '3,00 %'],
    [0.5, '0,50 %'],
  ])('pourcentage(%s) = %s', (taux, texte) => expect(pourcentage(taux)).toBe(texte));

  it.each([
    [22950, '22 950 €'],
    [10, '10 €'],
    [1234567, '1 234 567 €'],
    [390.15, '390,15 €'],
    [85, '85 €'],
    [0.5, '0,50 €'],
  ])('euros(%s) = %s', (montant, texte) => expect(euros(montant)).toBe(texte));

  it.each([
    ['2026-02-01', '1er février 2026'],
    ['2026-12-15', '15 décembre 2026'],
  ])('dateLongue(%s) = %s', (date, texte) => expect(dateLongue(date)).toBe(texte));

  it.each([
    [85, '85,00 €'],
    [390.15, '390,15 €'],
    [1234.5, '1 234,50 €'],
  ])('eurosAuCentime(%s) = %s', (montant, texte) => expect(eurosAuCentime(montant)).toBe(texte));});

describe('Simulateur du Livret A (critère 4)', () => {
  it('calcule les intérêts sur un an : montant × taux', () => {
    expect(simuler(5000)).toEqual({ montantRetenu: 5000, interets: 85, message: null });
    expect(simuler(1000.5)).toEqual({ montantRetenu: 1000.5, interets: 17.01, message: null });
  });

  it('ramène un montant trop élevé au plafond, avec un message', () => {
    expect(simuler(30000)).toEqual({ montantRetenu: 22950, interets: 390.15, message: PLAFOND_ATTEINT });
    expect(PLAFOND_ATTEINT).toContain('22 950 €');
  });

  it('accepte exactement le plafond et le minimum', () => {
    expect(simuler(22950)?.message).toBeNull();
    expect(simuler(10)).toEqual({ montantRetenu: 10, interets: 0.17, message: null });
  });

  it('sous le minimum de 10 €, rappelle le versement minimum sans résultat', () => {
    expect(simuler(5)).toEqual({ montantRetenu: null, interets: null, message: SOUS_LE_MINIMUM });
    expect(SOUS_LE_MINIMUM).toContain('10 €');
  });

  it.each([0, -20, Number.NaN, Number.POSITIVE_INFINITY])('sans montant valable (%s), pas de simulation', (montant) => {
    expect(simuler(montant)).toBeNull();
  });
});
