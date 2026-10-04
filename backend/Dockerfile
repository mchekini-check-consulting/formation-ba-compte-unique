# syntax=docker/dockerfile:1
# Backend Spring Boot (Java 21, module Maven compte-unique) -> image distroless, non-root.

# ---- Build : Maven + JDK, jamais livré en production
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Les pom d'abord : la couche des dépendances reste en cache tant qu'ils ne changent pas
COPY pom.xml .
COPY compte-unique/pom.xml compte-unique/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl compte-unique -am dependency:go-offline

COPY compte-unique/src compte-unique/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl compte-unique -am package -DskipTests \
 && cp compte-unique/target/compte-unique-*.jar /app.jar

# ---- Runtime : JRE seul, sans shell ni gestionnaire de paquets, utilisateur 65532
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# La JVM prend 75 % de la limite mémoire du conteneur
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
USER nonroot
EXPOSE 8080
# ENTRYPOINT de l'image : ["java", "-jar"]
CMD ["/app/app.jar"]
# Image construite par la CI (.github/workflows/ci.yaml), taguée avec le sha court du commit
