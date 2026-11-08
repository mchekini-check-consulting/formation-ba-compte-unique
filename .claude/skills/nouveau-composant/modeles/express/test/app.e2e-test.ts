import { PostgreSqlContainer, type StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { sql } from 'drizzle-orm';
import type { Pool } from 'pg';
import request from 'supertest';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createApp } from '../src/app.js';
import { createDb, createPool } from '../src/db/index.js';
import { migrate } from '../src/migrate.js';

// Application complète sur un PostgreSQL 16 jetable (migrations comprises).
// make test-integration ; Docker requis.
async function migrationsAppliquees(pool: Pool): Promise<number> {
  const { rows } = await pool.query('select count(*) as n from drizzle.__drizzle_migrations');
  return Number(rows[0].n);
}

describe('backend (e2e)', () => {
  let postgres: StartedPostgreSqlContainer;
  let pool: Pool;

  beforeAll(async () => {
    postgres = await new PostgreSqlContainer('postgres:16').start();
    pool = createPool(postgres.getConnectionUri());
    await migrate(pool);
  });

  afterAll(async () => {
    await pool?.end();
    await postgres?.stop();
  });

  it('GET /health : ok avec une vraie base', async () => {
    const db = createDb(pool);
    const res = await request(createApp(() => db.execute(sql`select 1`)))
      .get('/health')
      .expect(200);
    expect(res.body).toEqual({ status: 'ok' });
  });

  it('migrations appliquées une seule fois, rejouables sans effet', async () => {
    const avant = await migrationsAppliquees(pool);
    expect(avant).toBeGreaterThan(0);
    await Promise.all([migrate(pool), migrate(pool)]); // deux réplicas en même temps
    expect(await migrationsAppliquees(pool)).toBe(avant);
  });
});
