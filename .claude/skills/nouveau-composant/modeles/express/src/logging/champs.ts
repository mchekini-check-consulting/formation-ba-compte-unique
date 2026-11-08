// Champs métier d'un log (skill logs-metier) : chemins <racine>.<propriété>... approuvés
// par le BA. "taille" = nombre d'éléments d'une liste. Contrôlés dès la déclaration
// (verifier : l'application refuse de démarrer, les tests échouent) ; lus sans jamais
// faire échouer l'appel (extraire : un chemin illisible est ignoré).

export const RESULTAT = 'resultat';
const TAILLE = 'taille';

// Écrits par la plateforme : un champ approuvé ne peut pas porter ces noms
export const RESERVES = new Set([
  'time',
  'level',
  'msg',
  'layer',
  'method',
  'route',
  'status',
  'durationMs',
  'err',
]);

// Mots (découpage camelCase) qui signalent une donnée personnelle ou un secret :
// refusés même si le BA les approuve. "code" couvre code postal, de vérification...
const INTERDITS = [
  'nom',
  'prenom',
  'email',
  'mail',
  'telephone',
  'portable',
  'adresse',
  'code',
  'naissance',
  'iban',
  'bic',
  'mot de passe',
  'password',
  'jeton',
  'token',
  'authorization',
  'secret',
  'signature',
  'cookie',
  'ip',
  'secu',
];

export function personnel(segment: string): boolean {
  const mots = ` ${segment.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase()} `;
  return INTERDITS.some((interdit) => mots.includes(` ${interdit} `));
}

// Problèmes des champs déclarés pour ces racines (paramètres, resultat...) ; vide si conforme
export function erreurs(champs: readonly string[], racines: readonly string[]): string[] {
  const problemes: string[] = [];
  for (const champ of champs) {
    const segments = champ.split('.');
    const interdit = segments.find(personnel);
    if (RESERVES.has(champ)) {
      problemes.push(`${champ} : nom réservé à la plateforme`);
    } else if (interdit) {
      problemes.push(`${champ} : donnée personnelle ou secret (${interdit}), interdit dans les logs`);
    } else if (!racines.includes(segments[0])) {
      problemes.push(`${champ} : racine inconnue (${segments[0]}), attendu : ${racines.join(', ')}`);
    }
  }
  return problemes;
}

export function verifier(contexte: string, champs: readonly string[], racines: readonly string[]): void {
  const problemes = erreurs(champs, racines);
  if (problemes.length > 0) {
    throw new Error(`Logs métier de ${contexte} :\n${problemes.join('\n')}`);
  }
}

function simple(valeur: unknown): valeur is string | number | boolean | bigint | Date {
  return ['string', 'number', 'boolean', 'bigint'].includes(typeof valeur) || valeur instanceof Date;
}

function texte(valeur: string | number | boolean | bigint | Date): string {
  return valeur instanceof Date ? valeur.toISOString() : String(valeur);
}

function lire(racine: unknown, proprietes: string[]): string | undefined {
  let courant = racine;
  for (const propriete of proprietes) {
    if (courant === null || courant === undefined) {
      return undefined;
    }
    courant =
      Array.isArray(courant) && propriete === TAILLE
        ? courant.length
        : (courant as Record<string, unknown>)[propriete];
  }
  if (simple(courant)) {
    return texte(courant);
  }
  if (Array.isArray(courant) && courant.every(simple)) {
    return courant.map(texte).join(',');
  }
  return undefined; // absent ou valeur non simple
}

export function extraire(
  champs: readonly string[],
  racines: Record<string, unknown>,
): Record<string, string> {
  const valeurs: Record<string, string> = {};
  for (const champ of champs) {
    const [racine, ...proprietes] = champ.split('.');
    if (!(racine in racines)) {
      continue;
    }
    try {
      const valeur = lire(racines[racine], proprietes);
      if (valeur !== undefined) {
        valeurs[champ] = valeur;
      }
    } catch {
      // getter en erreur : champ ignoré, l'appel métier continue
    }
  }
  return valeurs;
}
