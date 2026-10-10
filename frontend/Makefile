# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
.PHONY: lint test-unit test-integration

lint:
	npx --yes oxlint src

test-unit:
	npx ng test --watch=false --coverage --coverage-reporters=lcov   # -> coverage/app/lcov.info

test-integration:
	@echo "pas de tests d'intégration"
