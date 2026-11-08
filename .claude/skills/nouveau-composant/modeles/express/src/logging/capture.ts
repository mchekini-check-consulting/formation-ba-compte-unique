import { createLogger } from './logger.js';

// Tests : logger qui garde les lignes JSON en mémoire
export function captureLogs(level = 'info') {
  const lignes: Record<string, unknown>[] = [];
  const log = createLogger({ write: (ligne: string) => lignes.push(JSON.parse(ligne)) }, level);
  return { log, lignes };
}
