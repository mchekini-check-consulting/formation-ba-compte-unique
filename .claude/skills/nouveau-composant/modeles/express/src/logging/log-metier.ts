import type { RequestHandler } from 'express';
import type { Logger } from 'pino';
import { extraire, RESULTAT, verifier } from './champs.js';
import { dureeMs, logger, nomErreur } from './logger.js';

// Logs métier activés méthode par méthode (skill logs-metier). Rien n'est journalisé
// sans activation. Champs obligatoires de la plateforme :
//   controller : layer, method (GET...), route (modèle, jamais ses valeurs), status, durationMs
//   service/db : layer, method (Classe.fonction), durationMs, err (type d'erreur en échec)
// puis les champs métier approuvés par le BA (champs.ts).

export type Couche = 'service' | 'db';

// parametres : noms des paramètres de la fonction, dans l'ordre (racines des champs)
export type Activation = { parametres?: readonly string[]; champs?: readonly string[] };

// Au-delà, un appel db est écrit en warn (visible même avec LOG_LEVEL=warn)
export const SEUIL_DB_LENT_MS = 200;

type Fonction = (...args: never[]) => unknown;

// Couches service et db : renvoie l'objet avec les seules fonctions activées enveloppées
// (y compris quand une autre fonction de l'objet les appelle par this).
//   export const contratService = logMetier('service', 'ContratService', { lister, creer }, {
//     creer: { parametres: ['demande'], champs: ['demande.formule', 'resultat.reference'] },
//   });
export function logMetier<T extends object>(
  couche: Couche,
  classe: string,
  cible: T,
  activations: { [K in keyof T]?: Activation },
  log: Logger = logger,
): T {
  const enveloppe = Object.create(cible) as Record<string, unknown>;
  for (const [nom, activation] of Object.entries(activations) as [string, Activation][]) {
    const fonction = (cible as Record<string, unknown>)[nom];
    if (typeof fonction !== 'function') {
      throw new Error(`Logs métier de ${classe} : ${nom} n'est pas une fonction`);
    }
    const parametres = activation.parametres ?? [];
    const champs = activation.champs ?? [];
    verifier(`${classe}.${nom}`, champs, [...parametres, RESULTAT]);

    enveloppe[nom] = (...args: unknown[]) => {
      const debut = process.hrtime.bigint();
      const racines = Object.fromEntries(parametres.map((parametre, i) => [parametre, args[i]]));
      const ecrire = (erreur: unknown, resultat?: unknown) => {
        const duree = dureeMs(debut);
        const valeurs = extraire(
          champs,
          erreur === undefined ? { ...racines, [RESULTAT]: resultat } : racines,
        );
        const ligne = { layer: couche, method: `${classe}.${nom}`, durationMs: duree, ...valeurs };
        if (erreur !== undefined) {
          log.warn({ ...ligne, err: nomErreur(erreur) }, `${couche} call failed`);
        } else if (couche === 'db' && duree >= SEUIL_DB_LENT_MS) {
          log.warn(ligne, 'slow db call');
        } else {
          log.info(ligne, `${couche} call completed`);
        }
      };
      const succes = (resultat: unknown) => {
        ecrire(undefined, resultat);
        return resultat;
      };
      const echec = (erreur: unknown): never => {
        ecrire(erreur ?? new Error('échec'));
        throw erreur;
      };
      try {
        const resultat = (fonction as Fonction).apply(cible, args as never[]);
        return resultat instanceof Promise ? resultat.then(succes, echec) : succes(resultat);
      } catch (erreur) {
        return echec(erreur);
      }
    };
  }
  return enveloppe as T;
}

// Couche controller : middleware à poser sur chaque route activée, avant son handler.
//   router.post('/contrats', logRoute({ champs: ['body.formule', 'resultat.reference'] }), creer);
// Racines : params, query, body (requête), resultat (corps JSON de la réponse).
export function logRoute(
  activation: { champs?: readonly string[] } = {},
  log: Logger = logger,
): RequestHandler {
  const champs = activation.champs ?? [];
  verifier('route', champs, ['params', 'query', 'body', RESULTAT]);

  return (req, res, next) => {
    const debut = process.hrtime.bigint();
    // Lue ici : après une erreur, Express a déjà quitté le routeur (baseUrl perdu)
    const route = req.route ? `${req.baseUrl}${req.route.path}` : 'unmatched';
    let resultat: unknown;
    const json = res.json.bind(res);
    res.json = (corps: unknown) => {
      resultat = corps;
      return json(corps);
    };
    res.on('finish', () => {
      const status = res.statusCode;
      const ligne = {
        layer: 'controller',
        method: req.method,
        route,
        status,
        durationMs: dureeMs(debut),
        ...extraire(champs, {
          params: req.params,
          query: req.query,
          body: req.body,
          ...(status < 400 && { [RESULTAT]: resultat }),
        }),
      };
      if (status >= 500) {
        log.warn(ligne, 'http request failed');
      } else {
        log.info(ligne, 'http request handled');
      }
    });
    next();
  };
}
