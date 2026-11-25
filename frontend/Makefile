# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
.PHONY: lint test-unit test-integration e2e-tests

lint:
	npx --yes oxlint src

test-unit:
	npx ng test --watch=false --coverage --coverage-reporters=lcov   # -> coverage/app/lcov.info

test-integration:
	@echo "pas de tests d'intégration"

# Playwright (e2e/*.e2e.ts, playwright.config.ts) : installe Chromium s'il manque, puis lance les tests.
e2e-tests:
	npx playwright install --with-deps chromium
	npx playwright test   # échec -> rapport dans playwright-report/
