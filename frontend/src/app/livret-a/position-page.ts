// CU-27 critère 8 : position de défilement de la page Livret A au moment d'ouvrir le parcours. Le
// « Retour au site » du parcours ramène la cliente à cet endroit, même après la connexion (la
// navigation reste dans l'application : la valeur est gardée en mémoire, rien n'est stocké).
let position: number | null = null;

export function memoriserPosition(defilement: number): void {
  position = defilement;
}

/** Position mémorisée (0 sinon), oubliée une fois lue : une nouvelle visite part du haut. */
export function reprendrePosition(): number {
  const valeur = position ?? 0;
  position = null;
  return valeur;
}
