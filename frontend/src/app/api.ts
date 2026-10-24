// URL du backend du projet, déduite du host du frontend :
//   https://<application>.<projet>.<env>.<domaine>
//   -> passerelle https://gateway.<projet>.<env>.<domaine>/backend (pod gateway du projet)
// Même image dans tous les environnements (promotion Kargo) : rien à configurer.
// En local (localhost) : /api, relayé vers le backend par le serveur de dev
// (proxy.conf.json).
export function apiBase(hostname: string = location.hostname): string {
  const [, projet, env, ...domaine] = hostname.split('.');
  if (!projet || !env || domaine.length === 0) {
    return '/api';
  }
  return `https://gateway.${projet}.${env}.${domaine.join('.')}/backend`;
}
