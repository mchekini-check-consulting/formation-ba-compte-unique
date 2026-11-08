# __SLUG__

__TITRE__ : backend Express (TypeScript, Node 24) du projet __DEPOT__, base PostgreSQL
`__BASE__` (Drizzle).

Via le gateway : `https://gateway.__DEPOT__.<env>.check-consulting.net/__SLUG__/...`
(préfixe `/__SLUG__` retiré par le gateway).

## Structure

```
src/
  server.ts      # point d'entrée (migrations puis écoute)
  app.ts         # application Express : routes / et /health, routeurs métier
  db/schema.ts   # tables (Drizzle) ; npm run db:generate -> drizzle/*.sql
  logging/       # logs métier (logMetier, logRoute ; skill logs-metier) : ne pas modifier
test/            # tests d'intégration (*.e2e-test.ts)
```

## En local

```bash
docker compose up -d   # base locale, port __PORT_DB_LOCAL__
npm install
npm run dev            # http://localhost:__PORT_LOCAL__/ (santé : /health)
```

## Contrôles (comme la CI)

`make lint` (oxlint + tsc), `make test-unit` (vitest + couverture), `make test-integration`
(PostgreSQL jetable, Docker requis).
