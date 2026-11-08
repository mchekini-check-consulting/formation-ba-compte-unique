import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    coverage: {
      include: ['src/**/*.ts'],
      // Démarrage / câblage : mêmes exclusions que sonar.coverage.exclusions
      exclude: ['src/server.ts', 'src/db/**', 'src/migrate.ts'],
    },
  },
});
