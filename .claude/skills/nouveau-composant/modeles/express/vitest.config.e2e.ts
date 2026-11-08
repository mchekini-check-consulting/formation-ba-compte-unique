import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['test/**/*.e2e-test.ts'],
    // Démarrage du conteneur PostgreSQL (téléchargement de l'image compris)
    hookTimeout: 120_000,
  },
});
