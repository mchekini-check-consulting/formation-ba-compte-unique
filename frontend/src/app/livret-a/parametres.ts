// CU-27 critère 5 : paramètres réglementés du Livret A, à un seul endroit. Quand le taux change, on
// modifie TAUX et DATE_EFFET ici : la page Livret A, son simulateur, la page d'accueil et l'espace
// client les reprennent tous.
export const LIVRET_A = {
  /** Taux net annuel, en %. Sans impôt ni prélèvements sociaux. */
  taux: 1.7,
  /** Date d'effet du taux (AAAA-MM-JJ). */
  dateEffet: '2026-08-01',
  /** Plafond de versements, hors intérêts capitalisés, en euros. */
  plafond: 22950,
  /** Versement initial minimum, en euros. */
  versementMinimum: 10,
} as const;

const MOIS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

/** « 22 950 » : chiffres groupés par trois, séparés par un espace. */
function grouper(entier: string): string {
  return entier.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
}

/** « 1,70 % » */
export function pourcentage(taux: number): string {
  return `${taux.toFixed(2).replace('.', ',')} %`;
}

/** « 22 950 € » pour un montant entier, « 390,15 € » sinon. */
export function euros(montant: number): string {
  return Number.isInteger(montant) ? `${grouper(montant.toFixed(0))} €` : eurosAuCentime(montant);
}

/** « 85,00 € » : montant toujours affiché au centime (résultat du simulateur). */
export function eurosAuCentime(montant: number): string {
  const [entier, decimales] = montant.toFixed(2).split('.');
  return `${grouper(entier)},${decimales} €`;
}

/** « 1er août 2026 » pour une date AAAA-MM-JJ. */
export function dateLongue(date: string): string {
  const [annee, mois, jour] = date.split('-').map(Number);
  return `${jour === 1 ? '1er' : jour} ${MOIS[mois - 1]} ${annee}`;
}

/** Taux affiché partout sur le site : « 1,70 % ». */
export const TAUX_LIVRET_A = pourcentage(LIVRET_A.taux);
/** Libellé qui suit le taux, identique partout (CU-27 critère 2). */
export const DETAIL_TAUX_LIVRET_A = 'net / an';

export const PLAFOND_ATTEINT = `Le montant est ramené au plafond du Livret A, ${euros(LIVRET_A.plafond)}.`;
export const SOUS_LE_MINIMUM = `Le versement minimum à l’ouverture est de ${euros(LIVRET_A.versementMinimum)}.`;

export interface Simulation {
  /** Montant pris en compte, plafonné ; null sous le minimum. */
  montantRetenu: number | null;
  /** Intérêts sur un an, arrondis au centime ; null sous le minimum. */
  interets: number | null;
  /** Explication à afficher quand le montant saisi n'est pas pris tel quel. */
  message: string | null;
}

/**
 * CU-27 critère 4 : estimation simple des intérêts sur un an (montant × taux), validée par le métier.
 * Au-delà du plafond, le calcul se fait sur le plafond ; sous le minimum, pas de résultat. Rien de
 * saisi (ou pas un nombre) : null.
 */
export function simuler(montant: number): Simulation | null {
  if (!Number.isFinite(montant) || montant <= 0) {
    return null;
  }
  if (montant < LIVRET_A.versementMinimum) {
    return { montantRetenu: null, interets: null, message: SOUS_LE_MINIMUM };
  }
  const montantRetenu = Math.min(montant, LIVRET_A.plafond);
  const interets = Math.round(montantRetenu * LIVRET_A.taux) / 100;
  return { montantRetenu, interets, message: montant > LIVRET_A.plafond ? PLAFOND_ATTEINT : null };
}
