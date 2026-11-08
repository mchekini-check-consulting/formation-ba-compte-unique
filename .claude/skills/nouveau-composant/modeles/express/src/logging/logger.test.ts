import { describe, expect, it } from 'vitest';
import { captureLogs } from './capture.js';
import { dureeMs, nomErreur } from './logger.js';

describe('logger', () => {
  it('écrit une ligne JSON : time, level, msg et les champs', () => {
    const { log, lignes } = captureLogs();
    log.info({ layer: 'service' }, 'service call completed');
    expect(lignes).toEqual([
      { level: 'info', time: expect.any(String), layer: 'service', msg: 'service call completed' },
    ]);
  });

  it('respecte le niveau minimal (LOG_LEVEL)', () => {
    const { log, lignes } = captureLogs('WARN');
    log.info('ignoré');
    log.warn('gardé');
    expect(lignes.map((ligne) => ligne.msg)).toEqual(['gardé']);
  });

  it('nomme une erreur par son type, jamais par son message', () => {
    class ContratInconnu extends Error {}
    expect(nomErreur(new ContratInconnu('x'))).toBe('ContratInconnu');
    expect(nomErreur(Object.assign(new Error('x'), { name: 'DrizzleQueryError' }))).toBe(
      'DrizzleQueryError',
    );
    expect(nomErreur('texte')).toBe('Error');
  });

  it('mesure une durée en millisecondes', () => {
    expect(dureeMs(process.hrtime.bigint())).toBeGreaterThanOrEqual(0);
  });
});
