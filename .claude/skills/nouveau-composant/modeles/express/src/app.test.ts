import express from 'express';
import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';
import { createApp, errorHandler } from './app.js';
import { captureLogs } from './logging/capture.js';

describe('app', () => {
  it('GET / répond Hello', async () => {
    await request(createApp(vi.fn(), captureLogs().log)).get('/').expect(200, 'Hello, World!');
  });

  it('GET /health : ok quand la base répond', async () => {
    const ping = vi.fn().mockResolvedValue(undefined);
    const res = await request(createApp(ping, captureLogs().log)).get('/health').expect(200);
    expect(res.body).toEqual({ status: 'ok' });
    expect(ping).toHaveBeenCalledOnce();
  });

  it('GET /health : 503 quand la base ne répond pas', async () => {
    const ping = vi.fn().mockRejectedValue(new Error('connexion refusée'));
    const res = await request(createApp(ping, captureLogs().log)).get('/health').expect(503);
    expect(res.body).toEqual({ status: 'ko' });
  });

  it('branche les routeurs métier', async () => {
    const routeur = express.Router().get('/contrats', (_req, res) => {
      res.json([]);
    });
    await request(createApp(vi.fn(), captureLogs().log, [routeur])).get('/contrats').expect(200, []);
  });

  it('erreur non gérée : 500 sans détail, type de l’erreur en log', async () => {
    const { log, lignes } = captureLogs();
    const app = express();
    app.get('/panne', () => {
      throw new TypeError('marie.dupont@exemple.fr');
    });
    app.use(errorHandler(log));
    const res = await request(app).get('/panne').expect(500);
    expect(res.body).toEqual({ message: 'erreur interne' });
    expect(lignes).toEqual([
      expect.objectContaining({ level: 'error', err: 'TypeError', msg: 'erreur non gérée' }),
    ]);
    expect(JSON.stringify(lignes)).not.toContain('marie.dupont');
  });
});
