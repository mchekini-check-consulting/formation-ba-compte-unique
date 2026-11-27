// D4 (PO, 27/11/2026) : aucun choix de bouchon n'est affiché à la cliente. Les testeuses choisissent le résultat simulé
// des bouchons sur /test/scenarios (local, dev et qualif) ; les parcours le lisent au moment de l'envoi. Sans choix,
// ou si la valeur enregistrée n'existe plus : le cas nominal (premier de la liste). Mémorisé dans ce navigateur.
export type CleScenario =
  | 'ouverture.identite'
  | 'ouverture.piece'
  | 'ouverture.selfie'
  | 'livret-a.sanctions'
  | 'livret-a.fiscal'
  | 'livret-a.piece'
  | 'livret-a.selfie';

const PREFIXE = 'scenario-test.';

/** Code choisi pour ce bouchon, parmi ceux de la liste ; le premier (nominal) sinon. */
export function scenarioChoisi<T extends string>(cle: CleScenario, liste: readonly { code: T }[]): T {
  let choisi: string | null = null;
  try {
    choisi = localStorage.getItem(PREFIXE + cle);
  } catch {
    // stockage indisponible (navigation privée) : cas nominal
  }
  return liste.find((s) => s.code === choisi)?.code ?? liste[0].code;
}

export function choisirScenario(cle: CleScenario, code: string): void {
  try {
    localStorage.setItem(PREFIXE + cle, code);
  } catch {
    // stockage indisponible : le cas nominal reste appliqué
  }
}
