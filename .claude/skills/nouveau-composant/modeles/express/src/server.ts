import { sql } from 'drizzle-orm';
import { createApp } from './app.js';
import { createDb, createPool } from './db/index.js';
import { logger } from './logging/logger.js';
import { migrate } from './migrate.js';

// Migrations au démarrage, puis écoute sur PORT : __PORT_LOCAL__ en local,
// 8080 dans l'image (Dockerfile ; helm.yaml : containerPort)
const pool = createPool();
await migrate(pool);
const db = createDb(pool);

const port = Number(process.env.PORT ?? __PORT_LOCAL__);
createApp(() => db.execute(sql`select 1`), logger).listen(port, () =>
  logger.info({ port }, 'backend démarré'),
);
