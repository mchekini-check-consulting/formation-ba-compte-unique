---
name: nouveau-backend
description: Initialise un nouveau microservice backend minimal ("Hello World") pour un Business Analyst (BA) qui ne maîtrise pas git — synchronise main, crée la branche, détecte la stack du dépôt (Node, Java ou Python) à partir d'un backend existant, le duplique comme modèle (Dockerfile, Makefile, Sonar, logging structuré controller/service/db), lui donne sa base de données et ses ports locaux, vérifie que l'image Docker démarre, l'ajoute à la fin de helm.yaml et au gateway (gateway.yaml). Ne commit ni ne push rien (rôle du skill deploy). À utiliser quand le BA veut créer un nouveau backend / microservice / service / API, en plus de ceux qui existent déjà.
---

# Initialiser un nouveau backend (microservice)

Public : un Business Analyst, pas un développeur. Expliquer chaque étape en
langage simple. Ne jamais faire d'action destructrice (écraser un dossier,
des modifications en cours) sans confirmation explicite.

Ce skill est le même dans tous les dépôts `formation-ba-*`, qui n'ont pas tous
la même stack (Node/NestJS, Java/Spring Boot, Python/Django). Il ne suppose
jamais une stack ni un nom de dossier : il part d'un backend qui existe déjà
dans **ce** dépôt. Contrat CI (`ci-template`) : tout dossier racine avec
`Dockerfile` + `Makefile` (`lint`/`test-unit`/`test-integration`) +
`sonar-project.properties` est un composant, construit et testé
automatiquement — rien à configurer dans GitHub Actions ni dans
`formation-ba-gitops`. Les conventions du dépôt sont résumées dans `CLAUDE.md`.

## Étapes

### 1. Vérifier l'état courant et synchroniser main

Comme le skill `nouvelle-fonctionnalite` (étapes 1 à 3) : travail en cours à
ranger ou mettre en ligne d'abord, puis
```
git fetch origin
git switch main
git pull --ff-only origin main
```
Si le BA est déjà sur une branche `feature/...` non mise en ligne et veut y
ajouter ce backend : rester sur cette branche (pas de nouvelle branche), mais
y intégrer d'abord `main` (`git merge --no-edit origin/main`, conflits traités
comme dans le skill `deploy`, étape 3).

### 2. Demander le nom du nouveau backend

« Quel nom voulez-vous donner à ce nouveau backend ? » (ex. « Contrats »,
« Gestion des sinistres »). Ce skill ne crée qu'un backend, pas un frontend.

### 3. Construire les noms

- Slug : minuscules, sans accents, tirets à la place des espaces et
  caractères spéciaux. « Gestion des sinistres » → `gestion-des-sinistres`.
- Dossier : `<slug>/` à la racine. S'il existe déjà, ou si `<slug>` est déjà
  un `name` dans `helm.yaml` : s'arrêter et proposer un autre nom.
- Branche : `feature/<slug>` (sauf cas de l'étape 1 où l'on reste sur la
  branche en cours) → `git switch -c feature/<slug>`.
- Base de données : `<slug>` avec des underscores (`gestion_des_sinistres`).
- Chemin public (via le gateway) : `/<slug>`, donc
  `https://gateway.<dépôt>.<env>.check-consulting.net/<slug>/...`.

### 4. Choisir le modèle et détecter la stack

- Les backends existants sont les `applications` de `helm.yaml` avec
  `type: backend` (en général `backend/`). Prendre le plus simple comme
  **modèle**.
- Aucun backend dans le dépôt : ne pas deviner. S'arrêter et demander au
  formateur la stack à utiliser.
- Stack : `package.json` → Node ; `pom.xml` → Java/Maven ; `manage.py` →
  Python/Django.

### 5. Dupliquer le modèle, réduit à « Hello World »

Copier uniquement les fichiers suivis par git du modèle (jamais
`node_modules/`, `dist/`, `coverage/`, `target/`, `.venv/`) :
```
git ls-files <modèle> | while read -r f; do
  dest="<slug>${f#<modèle>}"
  mkdir -p "$(dirname "$dest")"
  cp "$f" "$dest"
done
```
Vérifier le nombre de fichiers copiés (`find <slug> -type f | wc -l` ≈
`git ls-files <modèle> | wc -l`).

Puis réduire, quel que soit le langage :
- **Garder** : le point d'entrée, la connexion à la base (lue depuis les
  variables d'environnement), la route « Hello World » du modèle, et **toute
  la plomberie** : `Dockerfile`, `Makefile`, `sonar-project.properties`,
  config de lint/build, `compose.yaml`, et le **logging structuré** (dossier
  `logging/` ou équivalent, config du logger comme `logback-spring.xml`).
  Le logging n'est pas un exemple métier : ne jamais le retirer.
- **Retirer** : la ressource d'exemple (`item`/`items` : entité, contrôleur,
  service, tests dédiés) et ses références dans l'assemblage de
  l'application.
- **Migrations** : retirer celles qui créent la ressource d'exemple ; garder
  la migration initiale si elle existe (Flyway `V1__init.sql`, TypeORM
  `...-init.ts`), Django : supprimer les migrations de l'exemple et garder
  `migrations/__init__.py`. Le modèle de données se fera ensuite avec le
  skill `modifier-le-modele-de-donnees`.
- **Renommer** : nom du projet/paquet (`package.json` `name`, `pom.xml`
  `artifactId`/`name`, `spring.application.name`, paquet Java
  `com.example.<ancien>` → `com.example.<slug sans tirets>`, application
  Django), nom de la base locale par défaut → `<base>` (étape 3).
- Remplacer le `README.md` du composant par une version courte : nom, rôle,
  ports locaux (étape 6), commandes `make lint`/`test-unit`/`test-integration`.

### 6. Ports locaux : faire tourner ce backend à côté des autres

Sur le poste du BA, plusieurs backends doivent pouvoir tourner en même temps.
Ne **jamais** changer le port d'écoute de l'application dans l'image (il doit
rester égal au `containerPort` du modèle) ; on ne change que les valeurs
locales :
- **Base locale** : dans `<slug>/compose.yaml`, publier PostgreSQL sur un port
  libre différent de ceux des autres `compose.yaml` du dépôt (5432 → `5433`,
  puis `5434`...), `POSTGRES_DB: <base>`. Mettre le même port dans l'URL locale
  par défaut du code (`postgresql://app:app@localhost:5433/<base>`,
  `jdbc:postgresql://localhost:5433/<base>` dans `application.yaml`, exemple
  `DATABASE_URL` de `compose.yaml` pour Django). Sur le cluster, ces valeurs
  sont remplacées par les variables injectées (`DATABASE_URL`,
  `SPRING_DATASOURCE_URL`...).
- **Port de l'application en local** : le port du modèle + 1 (puis + 2...),
  passé au lancement, sans changer le code : Node `PORT=3001 npm run start:dev`,
  Java `SERVER_PORT=8081 ./mvnw spring-boot:run`, Django
  `python manage.py runserver 8001`. L'écrire dans le README du composant.

### 7. Vérifier le logging structuré

Convention de la plateforme (Alloy/Loki) : une ligne JSON par log sur stdout
(`time`, `level`, `msg`), niveau minimal `LOG_LEVEL`, et un champ `layer`
(`controller`, `service` ou `db`) sur les seuls logs métier — les autres
lignes ne sont pas collectées dans le dashboard métier.
- Vérifier que la plomberie copiée est toujours branchée après la réduction :
  intercepteur/middleware global (controller), mécanisme réutilisable de la
  couche service (`@Loggable` en NestJS et Java, décorateur `loggable` en
  Python) appliqué au service qui répond « Hello World » s'il y en a un,
  logger des requêtes lentes ou en erreur (db).
- Si le modèle n'a pas cette plomberie : ne pas l'inventer, le signaler au
  formateur.

### 8. Vérifier que le squelette fonctionne

Depuis `<slug>/` : installation selon la stack (`npm install`,
`./mvnw -B -q dependency:resolve`, `pip install -r requirements-dev.txt`),
puis `make lint` et `make test-unit`.

Puis l'image Docker, en conditions réelles (tous les BA ont Docker) :
1. `docker compose up -d` (base du composant).
2. `docker build -t <slug> .`
3. Lancer le conteneur sur le **réseau** du compose (pas le port PostgreSQL de
   l'hôte), base jointe par son nom de service `postgres` :
   ```
   docker run -d --name <slug>-test --network <slug>_default \
     -e DATABASE_URL="postgresql://app:app@postgres:5432/<base>" \
     -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/<base>" \
     -e SPRING_DATASOURCE_USERNAME=app -e SPRING_DATASOURCE_PASSWORD=app \
     -e LOG_LEVEL=info \
     -p 8099:<containerPort> <slug>
   ```
   (réseau : `docker network ls | grep <slug>` ; port 8099 pris → en prendre
   un autre).
4. Après quelques secondes : `curl -sf http://localhost:8099/` doit renvoyer
   le « Hello World » du modèle.
5. `docker logs <slug>-test` doit contenir une ligne JSON avec
   `"layer":"controller"` pour cet appel. Absente : le logging n'a pas
   survécu à la copie, corriger avant de continuer.
6. Nettoyer dans tous les cas : `docker rm -f <slug>-test`, `docker compose down`.

Un échec ici est un problème de ce skill, pas du BA : corriger directement.
`make test-integration` : le lancer si Docker est disponible, sinon le dire.

### 9. Ajouter le backend à `helm.yaml`

Ajouter une entrée **à la fin** de la liste `applications:` — jamais au
milieu : Kargo repère chaque application par sa position et écrit
`applications.<position>.tag.<env>`. Recopier la forme et les commentaires de
l'entrée du modèle, adaptés au nouveau nom :

```yaml
  - name: <slug>
    type: backend
    image: 205493924920.dkr.ecr.eu-west-3.amazonaws.com/<dépôt>/<slug>
    # Écrit par Kargo (dev automatique ; qualif/preprod/prod : promotion par clic).
    # Garder une valeur par ligne, sans commentaire en fin de ligne : Kargo
    # remplace tout jusqu'à la fin de la ligne.
    tag:
      dev: ""
      qualif: ""
      preprod: ""
      prod: ""
    containerPort: <containerPort du modèle>
    replicas: <identique au modèle>
    service:
      port: 80
    database:
      name: <base>
    envList:
      - key: LOG_LEVEL
        value: <identique au modèle>
```

- `<dépôt>` : `gh repo view --json name -q .name`, jamais recopié d'ailleurs.
- `tag` **toujours en bloc, une valeur par ligne**, jamais
  `tag: {dev: "", ...}` sur une ligne. Ne jamais inventer de valeur de tag.
- Pas d'`ingress` : un backend n'est exposé que par le gateway.
- Tags vides = pas encore déployé, c'est normal : la plateforme ignore ce
  backend dans un environnement tant qu'il n'a pas de tag (le reste du projet
  continue d'être déployé normalement). Sa base est créée dès le merge.

### 10. Ajouter le backend au gateway

- Pas d'application `type: gateway` dans `helm.yaml` : le dire simplement (ce
  backend ne sera joignable que depuis l'intérieur du cluster) ; créer un
  gateway est une décision du formateur, ne pas le faire.
- Gateway sans `gateway.yaml` : rien à faire, le routage par défaut inclut
  automatiquement chaque backend déployé.
- `gateway.yaml` présent (cas de tous les dépôts de formation) : il fige le
  routage, il faut y ajouter le backend en recopiant le motif de `backend` :
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
  Réutiliser le middleware `cors` existant (ne pas en créer un second). Relire
  le fichier en entier (indentation YAML, bloc `gatewayConfig: |`). Tant que
  le backend n'est pas déployé dans un environnement, sa route y répond 502 :
  c'est normal.

### 11. Conclure en langage simple

Résumer : le dossier `<slug>/` est prêt et vérifié (« Hello World » qui répond,
logs structurés en place), sa base est déclarée, il est ajouté à `helm.yaml`
et au gateway. Ses ports locaux (étape 6). Il apparaîtra sur dev quelques
minutes après la mise en ligne (skill `deploy`), puis sur qualif après le clic
« Promote » dans Kargo.

Suite possible :
- ajouter ses tables : skill `modifier-le-modele-de-donnees` ;
- l'appeler depuis le frontend : skill `appeler-un-backend` ;
- le lancer avec le reste de l'application : skill `lancer-en-local`.

Pour la couche service, rappeler au BA d'appliquer à ses futures classes ou
fonctions métier le même mécanisme de log que le modèle (ex. `@Loggable()`).

**Rien n'a été commité ni poussé** : c'est le rôle de `deploy`.

## Ce que ce skill ne fait jamais

- Ne suppose jamais une stack ni un nom de dossier ; s'arrête s'il n'y a aucun
  backend modèle.
- Ne copie jamais l'exemple métier (`items`) ; ne retire jamais le logging.
- N'ajoute jamais un backend au milieu de `applications:`, n'écrit jamais
  `tag` sur une seule ligne, n'invente jamais une valeur de tag.
- Ne change jamais le port d'écoute de l'application dans l'image.
- Ne crée jamais d'application `type: gateway`.
- Ne commit, ne push et n'ouvre jamais de PR.
- N'écrase jamais un dossier existant ; ne modifie jamais `formation-ba-gitops`.
