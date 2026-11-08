import express from 'express';
import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { captureLogs } from './capture.js';
import { logMetier, logRoute, SEUIL_DB_LENT_MS } from './log-metier.js';

function attendre(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

describe('logMetier (service, db)', () => {
  const service = {
    calculer(formule: string, _email: string) {
      if (formule === 'inconnue') {
        throw new RangeError('formule inconnue');
      }
      return { formule, prix: 12 };
    },
    sansLog() {
      return this.calculer('confort', '');
    },
  };

  it('écrit les champs obligatoires et approuvés des seules fonctions activées', () => {
    const { log, lignes } = captureLogs();
    const journalise = logMetier(
      'service',
      'TarifService',
      service,
      { calculer: { parametres: ['formule', 'email'], champs: ['formule', 'resultat.prix'] } },
      log,
    );

    expect(journalise.calculer('confort', 'marie.dupont@exemple.fr')).toEqual({
      formule: 'confort',
      prix: 12,
    });
    expect(lignes).toEqual([
      expect.objectContaining({
        level: 'info',
        msg: 'service call completed',
        layer: 'service',
        method: 'TarifService.calculer',
        durationMs: expect.any(Number),
        formule: 'confort',
        'resultat.prix': '12',
      }),
    ]);

    // sansLog n'est pas journalisée ; calculer, qu'elle appelle, l'est
    expect(journalise.sansLog()).toEqual({ formule: 'confort', prix: 12 });
    expect(lignes.map((ligne) => ligne.method)).toEqual([
      'TarifService.calculer',
      'TarifService.calculer',
    ]);
    expect(JSON.stringify(lignes)).not.toContain('marie.dupont');
  });

  it('échec : warn avec le type de l’erreur, sans le résultat', () => {
    const { log, lignes } = captureLogs();
    const journalise = logMetier(
      'service',
      'TarifService',
      service,
      { calculer: { parametres: ['formule'], champs: ['formule', 'resultat.prix'] } },
      log,
    );

    expect(() => journalise.calculer('inconnue', '')).toThrow(RangeError);
    expect(lignes).toEqual([
      expect.objectContaining({
        level: 'warn',
        msg: 'service call failed',
        err: 'RangeError',
        formule: 'inconnue',
      }),
    ]);
    expect(lignes[0]).not.toHaveProperty(['resultat.prix']);
  });

  it('db : fonctions asynchrones, appel lent en warn, échec asynchrone', async () => {
    const { log, lignes } = captureLogs();
    const depot = {
      async trouver(formule: string) {
        if (formule === 'lente') {
          await attendre(SEUIL_DB_LENT_MS + 10);
        }
        return [formule];
      },
      async supprimer() {
        throw new Error('base indisponible');
      },
    };
    const journalise = logMetier(
      'db',
      'ContratDepot',
      depot,
      { trouver: { parametres: ['formule'], champs: ['resultat.taille'] }, supprimer: {} },
      log,
    );

    await journalise.trouver('confort');
    await journalise.trouver('lente');
    await expect(journalise.supprimer()).rejects.toThrow('base indisponible');

    expect(lignes.map((ligne) => [ligne.level, ligne.msg])).toEqual([
      ['info', 'db call completed'],
      ['warn', 'slow db call'],
      ['warn', 'db call failed'],
    ]);
    expect(lignes[0]).toMatchObject({
      layer: 'db',
      method: 'ContratDepot.trouver',
      'resultat.taille': '1',
    });
  });

  it('refuse au démarrage un champ non conforme ou une fonction inconnue', () => {
    expect(() =>
      logMetier('service', 'TarifService', service, {
        calculer: { parametres: ['email'], champs: ['email'] },
      }),
    ).toThrow(/donnée personnelle/);
    expect(() =>
      logMetier('service', 'TarifService', { a: 1 } as Record<string, unknown>, { a: {} }),
    ).toThrow(/a n'est pas une fonction/);
  });
});

describe('logRoute (controller)', () => {
  function application(log: ReturnType<typeof captureLogs>['log']) {
    const app = express();
    app.use(express.json());
    const routeur = express.Router();
    routeur.post(
      '/contrats/:reference',
      logRoute({ champs: ['params.reference', 'body.formule', 'resultat.statut'] }, log),
      (req, res) => {
        if (req.body.formule === 'inconnue') {
          res.status(404).json({ statut: 'introuvable' });
          return;
        }
        res.status(201).json({ statut: 'cree' });
      },
    );
    routeur.get('/panne', logRoute({}, log), () => {
      throw new Error('panne');
    });
    routeur.get('/sans-log', (_req, res) => {
      res.send('ok');
    });
    app.use('/api', routeur);
    return app;
  }

  it('écrit route, statut, durée et champs approuvés des seules routes activées', async () => {
    const { log, lignes } = captureLogs();
    const app = application(log);

    await request(app)
      .post('/api/contrats/ref-1')
      .send({ formule: 'confort', email: 'marie.dupont@exemple.fr' })
      .expect(201);
    await request(app).post('/api/contrats/ref-2').send({ formule: 'inconnue' }).expect(404);
    await request(app).get('/api/sans-log').expect(200);

    expect(lignes).toEqual([
      expect.objectContaining({
        level: 'info',
        msg: 'http request handled',
        layer: 'controller',
        method: 'POST',
        route: '/api/contrats/:reference',
        status: 201,
        durationMs: expect.any(Number),
        'params.reference': 'ref-1',
        'body.formule': 'confort',
        'resultat.statut': 'cree',
      }),
      expect.objectContaining({ status: 404, 'body.formule': 'inconnue' }),
    ]);
    expect(lignes[1]).not.toHaveProperty(['resultat.statut']);
    expect(JSON.stringify(lignes)).not.toContain('marie.dupont');
  });

  it('erreur non gérée : 500 en warn', async () => {
    const { log, lignes } = captureLogs();
    await request(application(log)).get('/api/panne').expect(500);
    expect(lignes).toEqual([
      expect.objectContaining({
        level: 'warn',
        msg: 'http request failed',
        status: 500,
        route: '/api/panne',
      }),
    ]);
  });

  it('refuse au démarrage un champ non conforme', () => {
    expect(() => logRoute({ champs: ['headers.authorization'] })).toThrow(/donnée personnelle/);
    expect(() => logRoute({ champs: ['cookies.x'] })).toThrow(/racine inconnue/);
  });
});
