import { drizzle } from 'drizzle-orm/node-postgres';
import pg from 'pg';
import * as schema from './schema.js';

// bigint renvoyé en number, et non en string
pg.types.setTypeParser(20, Number);

// DATABASE_URL injectée par la plateforme (helm.yaml : database) ;
// sans elle (poste de dev) : la base de compose.yaml
export function createPool(
  url = process.env.DATABASE_URL ?? 'postgresql://app:app@localhost:__PORT_DB_LOCAL__/__BASE__',
): pg.Pool {
  return new pg.Pool({ connectionString: url });
}

export function createDb(pool: pg.Pool) {
  return drizzle(pool, { schema });
}

export type Db = ReturnType<typeof createDb>;
