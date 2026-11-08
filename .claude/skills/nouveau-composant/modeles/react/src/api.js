// URL d'un backend du projet, déduite du host du frontend :
//   https://<application>.<projet>.<env>.<domaine>
//   -> gateway https://gateway.<projet>.<env>.<domaine>/<backend> (pod gateway du projet)
// Même image dans tous les environnements (promotion Kargo) : rien à configurer.
// En local (localhost) : /api/<backend>, relayé vers le backend du poste par le serveur
// de dev (server.proxy de vite.config.js). <backend> = nom de l'application dans helm.yaml.
export function apiBase(backend, hostname = window.location.hostname) {
  const [, projet, env, ...domaine] = hostname.split('.')
  if (!projet || !env || domaine.length === 0) {
    return `/api/${backend}`
  }
  return `https://gateway.${projet}.${env}.${domaine.join('.')}/${backend}`
}

// Appel JSON d'un backend : erreur si le code HTTP n'est pas 2xx
export async function appeler(backend, chemin, options) {
  const reponse = await fetch(`${apiBase(backend)}${chemin}`, options)
  if (!reponse.ok) {
    throw new Error(`HTTP ${reponse.status}`)
  }
  return reponse.json()
}
