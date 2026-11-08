# __SLUG__

__TITRE__ : frontend React (Vite) du projet __DEPOT__, servi par nginx.

En ligne : `https://__SLUG__.__DEPOT__.<env>.check-consulting.net`

## Appels aux backends

`apiBase('<backend>')` et `appeler(...)` (`src/api.js`) : le gateway du projet en ligne,
`/api/<backend>` en local, relayé vers le backend du poste par `server.proxy` de
`vite.config.js` (une entrée par backend ; skill `nouvelle-fonctionnalite`).

## En local

```bash
npm install
npm run dev    # http://localhost:__PORT_LOCAL__
```

## Contrôles (comme la CI)

`make lint` (oxlint), `make test-unit` (vitest + couverture).
