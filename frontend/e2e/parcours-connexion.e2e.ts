import { expect, Page, test } from '@playwright/test';

// Parcours de connexion vus par la cliente, dans un vrai navigateur (CU-15, CU-18).
// Les backends sont simulés : chaque test fixe lui-même les réponses dont il a besoin.

/** Aucune session ouverte : le backend répond 401 à GET /connexion/session. */
async function sansSession(page: Page): Promise<void> {
  // Tout autre appel aux backends : 404, jamais de vrai backend appelé.
  await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
  await page.route('**/api/backend/connexion/session', (route) =>
    route.fulfill({ status: 401, json: {} }),
  );
}

test.beforeEach(async ({ page }) => {
  await sansSession(page);
});

test("l'accueil mène à la page de connexion", async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    'Tout votre argent, enfin au même endroit.',
  );
  await page.getByRole('link', { name: 'Me connecter' }).click();

  await expect(page).toHaveURL(/\/connexion$/);
  await expect(page.getByRole('heading', { name: 'Me connecter' })).toBeVisible();
});

test('sans session, une page de l’espace renvoie à la connexion', async ({ page }) => {
  await page.goto('/comptes');

  // CU-18 / CU-27 : retour à la connexion, avec la page demandée à rouvrir ensuite.
  await expect(page).toHaveURL(/\/connexion\?suite=%2Fcomptes$/);
  await expect(page.getByRole('heading', { name: 'Me connecter' })).toBeVisible();
});

test('des identifiants refusés affichent le message du backend', async ({ page }) => {
  await page.route('**/api/backend/connexion', (route) =>
    route.fulfill({
      status: 401,
      json: { message: 'Identifiant ou mot de passe incorrect.', invitation: null },
    }),
  );
  await page.goto('/connexion');

  await page.getByLabel('Identifiant de connexion').fill('12345678');
  await page.getByLabel('Mot de passe', { exact: true }).fill('mauvais-mot-de-passe');
  await page.getByRole('button', { name: 'Me connecter' }).click();

  const alerte = page.getByRole('alert');
  await expect(alerte).toContainText('Connexion impossible');
  await expect(alerte).toContainText('Identifiant ou mot de passe incorrect.');
  await expect(page.getByLabel('Mot de passe', { exact: true })).toHaveValue(''); // effacé après un refus
});
