import { defineConfig, devices } from '@playwright/test';

// Tests de bout en bout (e2e) du frontend : "make e2e-tests" (CI : ci-template/e2e-test, après
// les tests d'intégration). L'écran tourne avec "ng serve" ; les réponses des backends sont
// simulées dans chaque test (page.route), donc aucun backend ni base n'est nécessaire.
// En cas d'échec, le rapport est dans playwright-report/ (publié en artifact par la CI).
const CI = !!process.env['CI'];
const PORT = 4300; // différent de 4200 : ne gêne pas un "npm start" déjà lancé sur le poste

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts', // jamais *.spec.ts : réservé aux tests unitaires (Vitest, SonarQube)
  fullyParallel: true,
  forbidOnly: CI,
  retries: CI ? 1 : 0,
  // Rapport jamais ouvert automatiquement (sinon le terminal reste bloqué) : npx playwright show-report
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npx ng serve --port ${PORT}`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !CI,
    timeout: 180_000,
  },
});
