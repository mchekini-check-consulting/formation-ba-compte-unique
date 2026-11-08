// URL d'un backend du projet, déduite du host du frontend :
//   https://<application>.<projet>.<env>.<domaine>
//   -> gateway https://gateway.<projet>.<env>.<domaine>/<backend> (pod gateway du projet)
// Même image dans tous les environnements (promotion Kargo) : rien à configurer.
// En local (localhost) : /api/<backend>, relayé vers le backend du poste par le serveur
// de dev (proxy.conf.json). <backend> = nom de l'application dans helm.yaml.
export function apiBase(backend: string, hostname: string = location.hostname): string {
  const [, projet, env, ...domaine] = hostname.split('.');
  if (!projet || !env || domaine.length === 0) {
    return `/api/${backend}`;
  }
  return `https://gateway.${projet}.${env}.${domaine.join('.')}/${backend}`;
}
