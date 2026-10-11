# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
.PHONY: lint test-unit test-integration

lint:
	./mvnw -B org.apache.maven.plugins:maven-pmd-plugin:3.28.0:check

test-unit:
	./mvnw -B test   # JaCoCo (pom.xml) -> */target/site/jacoco/jacoco.xml

test-integration:
	./mvnw -B test-compile failsafe:integration-test failsafe:verify   # classes *IT
