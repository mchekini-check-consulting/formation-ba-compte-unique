# __SLUG__

__TITRE__ : backend Spring Boot (Java 21, Maven) du projet __DEPOT__, base PostgreSQL `__BASE__`.

Via le gateway : `https://gateway.__DEPOT__.<env>.check-consulting.net/__SLUG__/...`
(préfixe `/__SLUG__` retiré par le gateway).

## Structure

```
src/main/java/__PAQUET_CHEMIN__/
  __CLASSE__Application.java   # point d'entrée
  resource/      # contrôleurs REST (routes HTTP)
  service/       # règles métier
  repository/    # accès aux données (Spring Data JPA)
  model/         # entités JPA (tables)
  logging/       # logs métier (@LogMetier, skill logs-metier) : ne pas modifier
src/main/resources/
  application.yaml          # configuration (base et port locaux par défaut)
  db/migration/             # migrations Flyway : V<n>__<description>.sql
```

## En local

```bash
docker compose up -d      # base locale, port __PORT_DB_LOCAL__
./mvnw spring-boot:run    # http://localhost:__PORT_LOCAL__/ (santé : /actuator/health)
```

## Contrôles (comme la CI)

`make lint` (PMD), `make test-unit` (JUnit + JaCoCo), `make test-integration`
(classes `*IT`, PostgreSQL jetable, Docker requis).
