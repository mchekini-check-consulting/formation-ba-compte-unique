# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
.PHONY: lint test-unit test-integration

lint:
	npm run lint

test-unit:
	npm run test:cov -- --coverage.reporter=lcov   # -> coverage/lcov.info

test-integration:
	npm run test:e2e   # test/*.e2e-test.ts, Docker requis
