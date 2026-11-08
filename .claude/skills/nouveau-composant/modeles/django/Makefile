# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
.PHONY: lint test-unit test-integration

lint:
	ruff check .

test-unit:
	coverage run manage.py test --exclude-tag=integration
	coverage xml -o coverage.xml

test-integration:
	python manage.py test --tag=integration --settings=config.settings_integration   # Docker requis
