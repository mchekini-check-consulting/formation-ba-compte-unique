# syntax=docker/dockerfile:1
# Backend Spring Boot (Java 21, Maven) -> image distroless, non-root.

# ---- Build : Maven + JDK, jamais livré en production
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Le pom d'abord : la couche des dépendances reste en cache tant qu'il ne change pas
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/__SLUG__-*.jar /app.jar

# ---- Runtime : JRE seul, sans shell ni gestionnaire de paquets, utilisateur 65532
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# La JVM prend 75 % de la limite mémoire du conteneur ; port de l'image : 8080
# (helm.yaml : containerPort), le port local par défaut est dans application.yaml
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" \
    SERVER_PORT=8080
USER nonroot
EXPOSE 8080
# ENTRYPOINT de l'image : ["java", "-jar"]
CMD ["/app/app.jar"]
# Image construite par la CI (.github/workflows/ci.yaml), taguée avec le sha court du commit
