import express, { type ErrorRequestHandler, type Express, type RequestHandler, type Router } from 'express';
import type { Logger } from 'pino';
import { logger, nomErreur } from './logging/logger.js';

// Vérifie que la base répond (select 1) ; injectée pour les tests unitaires
export type Ping = () => Promise<unknown>;

// Erreur non rattrapée par une route : 500, et son type en log (sans layer : kubectl logs)
export function errorHandler(log: Logger): ErrorRequestHandler {
  return (err, _req, res, _next) => {
    log.error({ err: nomErreur(err) }, 'erreur non gérée');
    res.status(500).json({ message: 'erreur interne' });
  };
}

// Application sans listen : testable avec supertest. Routes métier : routeurs
// (un fichier routes.ts par ressource), logs métier par route : logRoute (logging/).
export function createApp(ping: Ping, log: Logger = logger, routeurs: Router[] = []): Express {
  const app = express();
  // Ne pas annoncer Express (en-tête X-Powered-By, hotspot SonarQube)
  app.disable('x-powered-by');
  app.use(express.json());

  // Point de départ : GET / (via le gateway : /__SLUG__/)
  const hello: RequestHandler = (_req, res) => {
    res.send('Hello, World!');
  };
  const health: RequestHandler = async (_req, res) => {
    try {
      await ping();
      res.json({ status: 'ok' });
    } catch {
      res.status(503).json({ status: 'ko' });
    }
  };
  app.get('/', hello);
  app.get('/health', health);
  for (const routeur of routeurs) {
    app.use(routeur);
  }

  app.use(errorHandler(log));
  return app;
}
