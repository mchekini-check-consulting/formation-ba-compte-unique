import { pino, type DestinationStream, type Logger } from 'pino';

// Convention de la plateforme (Alloy/Loki) : une ligne JSON par log sur stdout,
// champs time/level/msg, niveau minimal LOG_LEVEL (helm.yaml, envList).
// Seules les lignes avec un champ layer (controller/service/db) sont collectées dans
// Loki (logs métier : log-metier.ts) ; les autres restent visibles avec kubectl logs.
export function createLogger(
  destination: DestinationStream = pino.destination(1),
  level = process.env.LOG_LEVEL ?? 'info',
): Logger {
  return pino(
    {
      level: level.toLowerCase(),
      base: undefined, // ni pid ni hostname
      timestamp: pino.stdTimeFunctions.isoTime,
      formatters: { level: (label) => ({ level: label }) },
    },
    destination,
  );
}

export const logger = createLogger();

// Type d'une erreur (jamais son message : il peut contenir des données personnelles)
export function nomErreur(erreur: unknown): string {
  if (erreur instanceof Error) {
    return erreur.constructor.name === 'Error' ? erreur.name : erreur.constructor.name;
  }
  return 'Error';
}

export function dureeMs(debut: bigint): number {
  return Number((process.hrtime.bigint() - debut) / 1_000_000n);
}
