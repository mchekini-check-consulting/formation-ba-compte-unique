# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
# sh ./mvnw : fonctionne même si le droit d'exécution est perdu (commit depuis Windows).
.PHONY: lint test-unit test-integration

lint:
	sh ./mvnw -B org.apache.maven.plugins:maven-pmd-plugin:3.28.0:check

test-unit:
	sh ./mvnw -B test   # JaCoCo (pom.xml) -> target/site/jacoco/jacoco.xml

test-integration:
	sh ./mvnw -B test-compile failsafe:integration-test failsafe:verify   # classes *IT, Docker requis
