// Règles de saisie de l'étape 1 « M'identifier » (CUB-8, CUB-9, CUB-12), sans appel au
// backend : elles sont testées seules dans regles.spec.ts.

export const MESSAGE_ANNULATION =
  "Votre demande d'ouverture en ligne est annulée. Pour ouvrir un compte à la banque, contactez un conseiller.";
export const MESSAGE_INDISPONIBLE = 'Vérification impossible, réessayez plus tard';

/** Champs de « Mon identité » (CUB-8), tels que saisis. */
export interface Identite {
  civilite: string;
  nomNaissance: string;
  nomUsage: string;
  prenoms: string;
  dateNaissance: string;
  communeNaissance: string;
  paysNaissance: string;
  nationalite: string;
  voie: string;
  complement: string;
  codePostal: string;
  ville: string;
  paysAdresse: string;
  email: string;
  mobile: string;
  fixe: string;
  typePiece: string;
  numeroPiece: string;
  dateExpiration: string;
}

export type ChampIdentite = keyof Identite;
export type Erreurs = Partial<Record<ChampIdentite, string>>;

const OBLIGATOIRES: Partial<Record<ChampIdentite, string>> = {
  nomNaissance: 'Nom de naissance obligatoire',
  prenoms: 'Au moins un prénom est obligatoire',
  dateNaissance: 'Date de naissance obligatoire',
  communeNaissance: 'Commune de naissance obligatoire',
  voie: 'Numéro et voie obligatoires',
  codePostal: 'Code postal à 5 chiffres',
  ville: 'Ville obligatoire',
  email: 'Adresse e-mail obligatoire',
  mobile: 'Téléphone mobile obligatoire',
  numeroPiece: 'Numéro de pièce obligatoire',
  dateExpiration: "Date d'expiration obligatoire",
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MOBILE = /^(0|\+33\s?)[67]([\s.-]?\d{2}){4}$/;
const TELEPHONE = /^(0|\+33\s?)[1-9]([\s.-]?\d{2}){4}$/;

/** « JJ/MM/AAAA » -> date locale, ou null si le format ou la date est invalide. */
export function lireDate(texte: string): Date | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texte.trim());
  if (!m) {
    return null;
  }
  const [jour, mois, annee] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(annee, mois - 1, jour);
  return date.getDate() === jour && date.getMonth() === mois - 1 ? date : null;
}

/** « JJ/MM/AAAA » -> « AAAA-MM-JJ » (format attendu par le backend). */
export function versIso(texte: string): string {
  const [jour, mois, annee] = texte.trim().split('/');
  return `${annee}-${mois}-${jour}`;
}

const FORMAT_DATE = 'Format attendu : JJ/MM/AAAA';

/** Date du jour à minuit, décalée de « ans » années. */
function jourJ(aujourdhui: Date, ans = 0): Date {
  return new Date(aujourdhui.getFullYear() + ans, aujourdhui.getMonth(), aujourdhui.getDate());
}

/** Un contrôle de format : message d'erreur, ou null si la valeur est correcte. */
type Controle = (identite: Identite, aujourdhui: Date) => string | null;

// Contrôles de format, lancés seulement sur les champs qui ne sont pas déjà en erreur
// (champ obligatoire vide).
const CONTROLES: [ChampIdentite, Controle][] = [
  ['email', (i) => (EMAIL.test(i.email.trim()) ? null : 'Adresse e-mail mal formée')],
  ['mobile', (i) => (MOBILE.test(i.mobile.trim()) ? null : 'Numéro mobile mal formé')],
  ['fixe', (i) => (!i.fixe.trim() || TELEPHONE.test(i.fixe.trim()) ? null : 'Numéro de téléphone mal formé')],
  [
    'codePostal',
    (i) => (i.paysAdresse !== 'France' || /^\d{5}$/.test(i.codePostal.trim()) ? null : 'Code postal à 5 chiffres'),
  ],
  [
    'dateNaissance',
    (i, aujourdhui) => {
      const naissance = lireDate(i.dateNaissance);
      if (!naissance) {
        return FORMAT_DATE;
      }
      return naissance > jourJ(aujourdhui, -18) ? 'Vous devez avoir au moins 18 ans' : null;
    },
  ],
  [
    'dateExpiration',
    (i, aujourdhui) => {
      const expiration = lireDate(i.dateExpiration);
      if (!expiration) {
        return FORMAT_DATE;
      }
      return expiration <= jourJ(aujourdhui) ? "Date d'expiration dépassée : corrigez la date saisie" : null;
    },
  ],
];

/** Contrôles de CUB-8 ; aujourdhui est passé en paramètre pour les tests. */
export function validerIdentite(identite: Identite, aujourdhui = new Date()): Erreurs {
  const erreurs: Erreurs = {};
  for (const [champ, message] of Object.entries(OBLIGATOIRES) as [ChampIdentite, string][]) {
    if (!identite[champ].trim()) {
      erreurs[champ] = message;
    }
  }
  for (const [champ, controle] of CONTROLES) {
    const erreur = erreurs[champ] ? null : controle(identite, aujourdhui);
    if (erreur) {
      erreurs[champ] = erreur;
    }
  }
  return erreurs;
}

// Pièce d'identité (CUB-9) : refus avant envoi, avec le motif précis.
const TYPES_ACCEPTES = ['image/png', 'image/jpeg', 'image/bmp', 'image/gif', 'application/pdf'];
const EXTENSIONS_ACCEPTEES = /\.(png|jpe?g|bmp|gif|pdf)$/i;
export const TAILLE_MAX_OCTETS = 2 * 1024 * 1024;

export function tailleLisible(octets: number): string {
  return `${(octets / (1024 * 1024)).toFixed(1).replace('.', ',')} Mo`;
}

/** Motif de refus du fichier, ou null s'il peut être envoyé. */
export function refusFichier(fichier: { name: string; type: string; size: number }): string | null {
  if (!TYPES_ACCEPTES.includes(fichier.type) && !EXTENSIONS_ACCEPTEES.test(fichier.name)) {
    return 'Format non accepté : PNG, JPEG, BMP, GIF ou PDF uniquement';
  }
  if (fichier.size > TAILLE_MAX_OCTETS) {
    return `Fichier trop lourd (${tailleLisible(fichier.size)}) : 2 Mo maximum`;
  }
  return null;
}

// Questions obligatoires et consentement (CUB-12).
export interface ResidenceFiscale {
  pays: string;
  nif: string;
}

export interface Reponses {
  origineFonds: string;
  origineAutre: string;
  objetRelation: string;
  pep: string;
  residenceEtranger: string;
  residences: ResidenceFiscale[];
  consentementIdentite: boolean;
  consentementSituation: boolean;
}

export interface ErreursReponses {
  origineFonds?: string;
  objetRelation?: string;
  pep?: string;
  residenceEtranger?: string;
  residences?: { pays?: string; nif?: string }[];
  consentementIdentite?: string;
}

export function validerReponses(r: Reponses): ErreursReponses {
  const erreurs: ErreursReponses = {};
  if (!r.origineFonds) {
    erreurs.origineFonds = 'Choisissez une réponse';
  } else if (r.origineFonds === 'Autre' && !r.origineAutre.trim()) {
    erreurs.origineFonds = "Précisez l'origine des fonds";
  }
  if (!r.objetRelation) {
    erreurs.objetRelation = 'Choisissez une réponse';
  }
  if (!r.pep) {
    erreurs.pep = 'Choisissez une réponse';
  }
  if (!r.residenceEtranger) {
    erreurs.residenceEtranger = 'Choisissez une réponse';
  } else if (r.residenceEtranger === 'Oui') {
    const lignes = r.residences.map((l) => ({
      ...(l.pays.trim() ? {} : { pays: 'Indiquez chaque pays de résidence fiscale' }),
      ...(l.nif.trim() ? {} : { nif: 'Ce champ est obligatoire' }),
    }));
    if (lignes.some((l) => l.pays || l.nif)) {
      erreurs.residences = lignes;
    }
  }
  if (!r.consentementIdentite) {
    erreurs.consentementIdentite =
      "Vous devez accepter le traitement de vos données d'identité pour continuer.";
  }
  return erreurs;
}

export function sansErreur(erreurs: object): boolean {
  return Object.keys(erreurs).length === 0;
}
