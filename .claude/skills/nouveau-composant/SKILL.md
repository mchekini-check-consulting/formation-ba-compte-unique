---
name: nouveau-composant
description: Crée un nouveau composant (backend ou frontend) pour un Business Analyst (BA) qui ne maîtrise pas git — demande s'il s'agit d'un frontend ou d'un backend, puis le framework (backend - Spring Boot, Express, Django ; frontend - Angular, React), le nom et, pour un frontend, les backends qu'il appelle, toujours sous forme de choix tirés du contexte du dépôt ; génère le composant uniquement à partir des modèles du skill (jamais par copie d'un autre composant) avec toutes les conventions de la plateforme - Dockerfile, Makefile (lint, test-unit, test-integration), sonar-project.properties, tests initialisés ; backend - logs métier JSON (layer, LOG_LEVEL, @LogMetier), base PostgreSQL et migrations, route et strip dans gateway.yaml ; frontend - api.ts (apiBase vers le gateway en ligne), relais locaux vers les backends appelés, CORS du gateway ; vérifie lint, tests et image Docker, puis l'ajoute à la fin de helm.yaml. Ne commit ni ne push rien (rôle du skill deploy). À utiliser quand le BA veut créer un nouveau backend, microservice, API, frontend, écran ou application dans le dépôt.
---

# Nouveau composant (backend ou frontend)

Public : un Business Analyst, pas un développeur. Expliquer chaque étape en
langage simple. Ne jamais écraser un dossier ni des modifications en cours.

Deux règles propres à ce skill :
- **Tout vient du skill.** Le composant est généré par `instancier.sh` à partir
  de `modeles/<framework>/` (dans le dossier de ce skill), qui contient tout le
  nécessaire. **Ne jamais copier un fichier d'un autre composant du dépôt** ;
  les autres composants ne servent qu'à lire le contexte (noms, ports, stacks).
- **Chaque question est un choix.** Toute information demandée au BA est posée
  avec l'outil de question à choix (AskUserQuestion), avec des options tirées
  du contexte du dépôt (option recommandée en premier) ; le BA peut toujours
  répondre autre chose (« Autre »). Jamais de question ouverte sans
  propositions.

Contrat de la CI (`formation-ba-gitops/ci-template`) : tout dossier racine avec
`Dockerfile` + `Makefile` (`lint`, `test-unit`, `test-integration`) +
`sonar-project.properties` est un composant, construit, testé et analysé
automatiquement (projet Sonar `<dépôt>-<composant>`, dépôt d'images ECR créé au
premier envoi, warehouse Kargo lu dans `helm.yaml`) : rien à configurer dans
GitHub Actions ni dans `formation-ba-gitops`.

## Modèles

| Type | Framework | Modèle | Port dans l'image | Port local (1er libre) | Base |
|---|---|---|---|---|---|
| backend | Spring Boot 4 (Java 21, Maven, JPA, Flyway) | `spring` | 8080 | 8080, 8081… | oui |
| backend | Express 5 (TypeScript, Node 24, Drizzle) | `express` | 8080 | 3000, 3001… | oui |
| backend | Django 6 (Python 3.13, DRF) | `django` | 8080 | 8000, 8001… | oui |
| frontend | Angular 22 | `angular` | 8080 (nginx) | 4200, 4201… | non |
| frontend | React 19 (Vite) | `react` | 8080 (nginx) | 5173, 5174… | non |

Chaque modèle contient : `Dockerfile` (image distroless non-root), `Makefile`,
`sonar-project.properties`, `.gitignore`, `.dockerignore`, `README.md`, une
page ou route « Hello World » et ses tests. En plus :
- **backend** : `compose.yaml` (PostgreSQL 16 local), connexion lue dans les
  variables injectées par la plateforme (`SPRING_DATASOURCE_*`,
  `DATABASE_URL`), migration initiale (Flyway `V1__init.sql`, Drizzle
  `drizzle/0000_init.sql`, migrations Django) appliquée au démarrage, route de
  santé (`/actuator/health` Spring, `/health` Express et Django), test
  d'intégration sur un PostgreSQL jetable (Testcontainers) ; **logs** : une ligne
  JSON par log sur stdout, niveau `LOG_LEVEL`, et la plomberie des logs métier
  (`@LogMetier` Spring, `logMetier`/`logRoute` Express, `@log_metier` Django,
  champs `layer`, contrôle des données personnelles) — aucune méthode n'est
  journalisée tant que le BA ne l'a pas choisie (skill `logs-metier`) ;
- **frontend** : `nginx.conf` (application monopage, port 8080),
  `apiBase('<backend>')` (Angular `src/app/core/config/api.ts`, React
  `src/api.js`) : en ligne le gateway du projet
  `https://gateway.<dépôt>.<env>.check-consulting.net/<backend>`, en local
  `/api/<backend>` ; relais du serveur de dev vers chaque backend appelé
  (Angular `proxy.conf.json`, React `server.proxy` de `vite.config.js`).

## Étapes

### 1. Vérifier l'état courant et synchroniser main

Comme le skill `nouvelle-fonctionnalite` (étapes 1 à 3) : travail en cours à
ranger ou mettre en ligne d'abord, puis
```
git fetch origin
git switch main
git pull --ff-only origin main
```
Si le BA est déjà sur une branche `feature/...` non mise en ligne : lui
demander (choix) s'il ajoute le composant **à cette branche** (y intégrer
d'abord `main` : `git merge --no-edit origin/main`, conflits traités comme
dans le skill `deploy`, étape 3) ou sur **une nouvelle branche**.

### 2. Lire le contexte du dépôt

Avant toute question, relever (sans rien modifier) :
- les applications de `helm.yaml` : nom, `type`, `image` (registre), `replicas` ;
- la stack de chaque composant : `pom.xml` → Spring Boot ; `package.json`
  avec `express` → Express, avec `@angular/core` → Angular, avec `react` →
  React ; `manage.py` → Django ;
- les **ports locaux déjà pris** :
  - bases : `ports` des `compose.yaml` / `docker-compose.yaml` (dossiers des
    composants et racine) ;
  - backends : `server.port` de `application.yaml` (sinon 8080), port par
    défaut de `src/server.ts` ou `PORT` du README (Express), port de
    `runserver` du README (Django, sinon 8000) ;
  - frontends : `port` de `angular.json` (sinon 4200), `server.port` de
    `vite.config.js` (sinon 5173) ;
- le nom du dépôt : `gh repo view --json name -q .name` (sinon le dernier
  segment de `git remote get-url origin`, sans `.git`) ;
- `gateway.yaml` (routes, middleware `cors`) et `CLAUDE.md` (table
  d'architecture) ;
- le vocabulaire métier (entités, routes, écrans, US citées dans le code et
  les README) : il sert à proposer des noms.

### 3. Questions au BA (toujours des choix)

1. **Type** : « Backend (API, règles métier, données) » ou « Frontend
   (écran dans le navigateur) ».
2. **Framework** :
   - backend : Spring Boot (Java), Express (Node/TypeScript), Django
     (Python) ;
   - frontend : Angular, React.

   Indiquer pour chaque option si elle est déjà utilisée dans ce dépôt
   (« comme `backend/` ») et recommander la plus utilisée (cohérence,
   entraide entre BA).
3. **Nom** : 2 ou 3 propositions tirées du vocabulaire métier relevé à
   l'étape 2 (ex. « Contrats », « Sinistres », « Espace client »), non encore
   prises. Le BA peut en donner un autre.
4. **Frontend seulement — backends appelés** (choix multiple) : les
   applications `type: backend` de `helm.yaml`, avec leur port local. Aucun
   est possible (à brancher plus tard, dans une fonctionnalité : skill
   `nouvelle-fonctionnalite`).

Puis afficher un **récapitulatif** (type, framework, nom, dossier, URL en
ligne, ports locaux, base, backends appelés) et demander la confirmation
(Créer / Modifier un choix).

### 4. Calculer les valeurs du modèle

| Jeton | Valeur | Exemple |
|---|---|---|
| `SLUG` | nom en minuscules, sans accents, tirets à la place des espaces et caractères spéciaux | `gestion-des-sinistres` |
| `TITRE` | nom affiché, tel que choisi | `Gestion des sinistres` |
| `DEPOT` | nom du dépôt (étape 2) | `formation-ba-assurance-iard` |
| `PORT_LOCAL` | premier port libre de la plage du framework (tableau des modèles) | `8081` |
| `BASE` (backend) | slug avec `_` | `gestion_des_sinistres` |
| `PORT_DB_LOCAL` (backend) | premier port libre à partir de 5432 | `5433` |
| `PAQUET`, `PAQUET_CHEMIN`, `CLASSE` (Spring) | `net.checkconsulting.<slug sans tirets>`, même chemin avec `/`, slug en PascalCase | `net.checkconsulting.gestiondessinistres`, `net/checkconsulting/gestiondessinistres`, `GestionDesSinistres` |
| `MODULE` (Django) | slug avec `_` | `gestion_des_sinistres` |
| `PREFIXE` (Angular) | initiales du slug, 2 ou 3 lettres minuscules (préfixe des composants) | `gds` |
| `INITIALE` (frontend) | première lettre du titre, en majuscule (icône de l'onglet) | `G` |
| `BACKENDS` (frontend) | backends appelés et leur port local, `nom:port` séparés par des virgules (vide si aucun) | `backend:8080,contrats:8081` |

Le slug ne doit être ni un dossier existant, ni un `name` de `helm.yaml`, ni
`gateway`, `api` ou `health` : sinon revenir à la question du nom.

### 5. Créer la branche et générer le composant

```
git switch -c feature/<slug>          # sauf si le BA reste sur sa branche (étape 1)
bash .claude/skills/nouveau-composant/instancier.sh <modèle> <slug> \
  SLUG=<slug> TITRE="<titre>" DEPOT=<dépôt> PORT_LOCAL=<port> ...
```
Le script copie le modèle, renomme les fichiers (`dot-gitignore` →
`.gitignore`, `__PAQUET_CHEMIN__`…), remplace les jetons et **échoue** si un
jeton n'a pas de valeur ou si le dossier existe déjà (rien n'est écrasé).
Ne rien recopier à la main depuis un autre composant.

Puis, dans `<slug>/` :
- Node (Express, Angular, React) : `npm install` — crée `package-lock.json`,
  indispensable à l'image (`npm ci`) et à la CI ; le garder.
- Django : environnement virtuel `.venv` (ignoré par git) puis
  `pip install -r requirements-dev.txt`.
- Spring : rien (`./mvnw` télécharge Maven et les dépendances).

### 6. Vérifier que le composant fonctionne

Depuis `<slug>/` : `make lint`, `make test-unit`, puis pour un backend
`make test-integration` (Docker requis ; sinon le dire). Sous Windows sans
`make` : lancer les commandes du `Makefile` directement.

Puis l'image Docker, en conditions réelles :
- **backend** :
  1. `docker compose up -d` (base du composant) ;
  2. `docker build -t <slug> .` ;
  3. `docker run -d --name <slug>-test --network <slug>_default -p 8099:8080`
     avec les variables de la plateforme : Spring
     `-e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/<base>
     -e SPRING_DATASOURCE_USERNAME=app -e SPRING_DATASOURCE_PASSWORD=app`,
     Express et Django `-e DATABASE_URL=postgresql://app:app@postgres:5432/<base>`
     (Django : `-e ALLOWED_HOSTS=localhost`), toujours `-e LOG_LEVEL=info`
     (réseau : `docker network ls | grep <slug>`) ;
  4. après quelques secondes : `curl -sf http://localhost:8099/` répond
     « Hello, World! » et la route de santé répond `UP` / `ok` ;
  5. `docker logs <slug>-test` : des lignes JSON (`"level"`, `"msg"`).
- **frontend** : `docker build -t <slug> .`, `docker run -d --name
  <slug>-test -p 8099:8080 <slug>`, `curl -sf http://localhost:8099/` renvoie
  la page (`<title>`), et une route inconnue (`/une/page`) aussi.

Nettoyer dans tous les cas : `docker rm -f <slug>-test`, `docker compose down`.
Un échec ici est un problème de ce skill, pas du BA : corriger directement.

### 7. Ajouter le composant à `helm.yaml`

Une entrée **à la fin** de `applications:` — jamais au milieu (Kargo repère
chaque application par sa position et écrit `applications.<position>.tag.<env>`).
Registre d'image et `replicas` : ceux des entrées existantes.

Backend :
```yaml
  - name: <slug>
    type: backend
    image: <registre>/<dépôt>/<slug>
    # Écrit par Kargo (dev automatique ; qualif/preprod/prod : promotion par clic).
    # Garder une valeur par ligne, sans commentaire en fin de ligne : Kargo
    # remplace tout jusqu'à la fin de la ligne.
    tag:
      dev: ""
      qualif: ""
      preprod: ""
      prod: ""
    containerPort: 8080
    replicas: {dev: 1, qualif: 1, preprod: 2, prod: 2}
    service:
      port: 80
    # Base PostgreSQL <base> : créée et sauvegardée automatiquement
    database:
      name: <base>
    envList:
      - key: LOG_LEVEL
        value: {dev: debug, qualif: info, preprod: info, prod: warn}
```
Django, en plus dans `envList` (le gateway relaie avec son propre nom d'hôte) :
```yaml
      - key: ALLOWED_HOSTS
        value:
          dev: gateway.<dépôt>.dev.check-consulting.net,<slug>
          qualif: gateway.<dépôt>.qualif.check-consulting.net,<slug>
          preprod: gateway.<dépôt>.preprod.check-consulting.net,<slug>
          prod: gateway.<dépôt>.prod.check-consulting.net,<slug>
```
Pas d'`ingress` pour un backend : il n'est exposé que par le gateway.

Frontend :
```yaml
  - name: <slug>
    type: frontend
    image: <registre>/<dépôt>/<slug>
    # (mêmes commentaires et tag en bloc que ci-dessus)
    tag:
      dev: ""
      qualif: ""
      preprod: ""
      prod: ""
    containerPort: 8080
    replicas: {dev: 1, qualif: 1, preprod: 2, prod: 2}
    service:
      port: 80
    ingress:
      enabled: true
      path: /
      stripPrefix: false
```

- `tag` **toujours en bloc, une valeur par ligne**, jamais sur une ligne ;
  jamais de valeur inventée. Tags vides = pas encore déployé : la plateforme
  ignore ce composant dans un environnement tant qu'il n'a pas de tag (le reste
  du projet continue d'être déployé) ; la base d'un backend est créée dès le
  merge.
- Relire le fichier en entier (indentation YAML).

### 8. Brancher le gateway (`gateway.yaml`)

- Pas d'application `type: gateway` dans `helm.yaml` : le dire simplement (un
  backend ne sera joignable que depuis l'intérieur du cluster) ; créer un
  gateway est une décision du formateur, ne pas le faire.
- Gateway sans `gateway.yaml` : rien à faire, le routage par défaut inclut
  automatiquement chaque backend déployé et autorise les frontends du projet.
- `gateway.yaml` présent (il fige le routage) :
  - **backend** : ajouter son router, son middleware de retrait du préfixe et
    son service, et réutiliser le middleware `cors` existant :
    ```yaml
        routers:
          <slug>:
            rule: "PathPrefix(`/<slug>`)"
            service: <slug>
            middlewares: [<slug>-strip, cors]
        middlewares:
          <slug>-strip:
            stripPrefix: {prefixes: ["/<slug>"]}
        services:
          <slug>:
            loadBalancer: {servers: [{url: "http://<slug>:80"}]}
    ```
  - **frontend** : ajouter son origine à
    `cors.headers.accessControlAllowOriginList` (jamais `*`) :
    `- https://<slug>.{{ .project }}.{{ .env }}.{{ .domain }}`

  Ne pas toucher aux autres routes ni aux middlewares d'authentification.
  Relire le fichier en entier (bloc `gatewayConfig: |`). Tant que le backend
  n'est pas déployé dans un environnement, sa route y répond 502 : c'est
  normal.

### 9. Documenter

- `CLAUDE.md`, table d'architecture : une ligne pour le composant (dossier,
  stack, port dans l'image, commande et URL locales).
- Le `README.md` du composant est déjà rempli par le modèle.

### 10. Conclure en langage simple

Résumer : le dossier `<slug>/` est prêt et vérifié (lint, tests, image qui
démarre), ajouté à `helm.yaml` (et au gateway), avec ses ports locaux. Son
adresse en ligne :
- backend : `https://gateway.<dépôt>.<env>.check-consulting.net/<slug>/` ;
- frontend : `https://<slug>.<dépôt>.<env>.check-consulting.net`.

Il apparaîtra sur dev quelques minutes après la mise en ligne (skill
`deploy`), puis sur qualif après le clic « Promote » dans Kargo.

Suite possible :
- backend : ses tables et ses routes (`nouvelle-fonctionnalite`), ses logs métier
  (`logs-metier` : aucun n'est encore activé) ;
- frontend : ses écrans et ses appels aux backends (`nouvelle-fonctionnalite`) ;
- tout lancer ensemble : `lancer-en-local`.

**Rien n'a été commité ni poussé** : c'est le rôle de `deploy`.

## Ce que ce skill ne fait jamais

- Ne copie jamais un fichier d'un autre composant du dépôt : tout vient de
  `modeles/` via `instancier.sh`.
- Ne pose jamais de question sans proposer de choix tirés du contexte.
- N'écrase jamais un dossier existant ; ne réutilise jamais un nom pris.
- N'ajoute jamais un composant au milieu de `applications:`, n'écrit jamais
  `tag` sur une seule ligne, n'invente jamais une valeur de tag.
- Ne change jamais le port d'écoute dans l'image (8080).
- N'active aucun log métier sans le BA (skill `logs-metier`).
- Ne crée jamais d'application `type: gateway`, ne modifie jamais
  `formation-ba-gitops`.
- Ne commit, ne push et n'ouvre jamais de PR.
