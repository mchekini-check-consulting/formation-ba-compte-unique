import { defineConfig } from 'drizzle-kit';

// drizzle-kit (poste de dev uniquement) : npm run db:generate après une modification
// de src/db/schema.ts. Les migrations sont appliquées par le backend au démarrage.
export default defineConfig({
  dialect: 'postgresql',
  schema: './src/db/schema.ts',
  out: './drizzle',
});
