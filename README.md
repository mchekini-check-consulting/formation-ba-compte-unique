# formation-ba-compte-unique

Monorepo contenant les microservices backend et l'application frontend.

```
backend/                  # microservices Spring Boot (Maven multi-module)
  pom.xml                 # parent Maven
  compte-unique/           # microservice "Hello World"
frontend/                 # application Angular
```

## Versions

### Backend

| Composant    | Version |
|--------------|---------|
| Java         | 21      |
| Spring Boot  | 4.1.1   |
| Maven        | 3.9.16 (via `./mvnw`) |

### Frontend

| Composant    | Version |
|--------------|---------|
| Node.js      | 25.8.1  |
| npm          | 11.11.0 |
| Angular      | 22.2    |
| TypeScript   | 6.0     |

## Lancer en local

### Backend

```bash
cd backend
./mvnw -pl compte-unique spring-boot:run
```

Disponible sur http://localhost:8080/

### Frontend

```bash
cd frontend
npm install
npm start
```

Disponible sur http://localhost:4200/
