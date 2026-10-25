---
name: nouveau-backend
description: Initialise un nouveau microservice backend minimal ("Hello World" uniquement) pour un Business Analyst (BA) qui ne maîtrise pas git — synchronise main, crée la branche, détecte la stack déjà utilisée dans CE dépôt (Node/Java/Python — ce skill est réutilisé tel quel dans plusieurs dépôts `formation-ba-*` qui n'ont pas tous la même stack), duplique un composant backend existant comme modèle vivant pour sa plomberie (Dockerfile/Makefile/sonar-project.properties/build), lui donne sa propre base de données, l'ajoute à `helm.yaml` et aux `values-<env>.yaml`, et ajoute sa route au gateway du projet (section `gateway` de helm.yaml) pour que ce backend soit joignable depuis l'extérieur. Ne commit ni ne push rien (c'est le rôle du skill `deploy`). À utiliser quand le BA veut créer un nouveau backend / microservice / service, en plus de ceux qui existent déjà.
---

# Initialiser un nouveau backend (microservice)

Public : un Business Analyst, pas un développeur. Expliquer chaque étape en
langage simple (pas de jargon git brut non expliqué). Ne jamais faire
d'action destructrice (écraser un dossier existant, écraser des
modifications en cours) sans confirmation explicite.

**Ce skill est copié tel quel dans plusieurs dépôts métier**
(`formation-ba-assurance-iard`, `formation-ba-compte-unique`,
`formation-ba-gestion-financiere`, `formation-ba-quali-formation`, ...).
Ils partagent la même convention de pipeline (`ci-template` :
tout dossier avec `Dockerfile` + `Makefile` (`lint`/`test-unit`/
`test-integration`) + `sonar-project.properties` devient un composant,
détecté automatiquement, rien à configurer côté GitHub Actions), **mais pas
forcément la même stack applicative** (Node, Java/Maven ou Python selon le
dépôt — cf. `ci-template/README.md`, action `setup`). Ce skill ne doit donc
jamais supposer une stack précise ni le nom d'un dossier précis : il
détecte celle du dépôt où il est lancé, et reste volontairement minimal
(objectif : un simple "Hello World" qui respecte le contrat CI — pas un
scaffold métier).

## Étapes

### 1. Vérifier l'état courant (comme `nouvelle-fonctionnalite`)

- `git rev-parse --is-inside-work-tree`. Si non : expliquer au BA qu'il doit
  ouvrir le dossier de son projet.
- `git status --porcelain` :
  - Modifications non enregistrées **et** pas sur `main` : s'arrêter,
    demander au BA ce qu'il veut faire de ce travail en cours. Ne jamais le
    supprimer sans réponse explicite.
  - Déjà sur une branche `feature/...` propre : informer le BA et demander
    s'il veut vraiment créer un nouvel espace de travail (à partir de main)
    ou continuer celui en cours.

### 2. Synchroniser main

```
git fetch origin
git switch main
git pull --ff-only origin main
```
Mêmes précautions que `nouvelle-fonctionnalite` si `switch` ou
`pull --ff-only` échouent (ne jamais `reset --hard` sans confirmation
explicite).

### 3. Demander le nom du nouveau backend

En langage simple et explicite sur le fait qu'il s'agit d'un backend (ce
skill ne crée que ça, pas un frontend) : "Quel nom voulez-vous donner à ce
nouveau backend ?" (ex. "Contrats", "Gestion des sinistres").

### 4. Construire le nom du dossier, de la branche et de la base de données

- Slug : minuscules, accents retirés, espaces/caractères spéciaux remplacés
  par des tirets (même règle que `nouvelle-fonctionnalite`). Ex. "Gestion
  des sinistres" → `gestion-des-sinistres`.
- Dossier du composant, à la racine du dépôt : `<slug>/`. Vérifier qu'il
  n'existe pas déjà (`test -d <slug>`) — sinon s'arrêter et proposer un
  autre nom plutôt que d'écraser quoi que ce soit.
- Branche : `feature/<slug>`.
- Nom de la base de données dédiée : `<slug>` avec les tirets remplacés par
  des underscores (identifiant SQL). Ex. `gestion_des_sinistres`.

### 5. Créer la branche

```
git switch -c feature/<slug>
```

### 6. Détecter la stack de CE dépôt, à partir d'un backend existant

Ne jamais supposer Node/NestJS ni un nom de dossier : regarder ce qui existe
réellement dans **ce** dépôt.

- Lister les composants backend existants : applications de `helm.yaml`
  (clés sous `applications:`) avec `type: backend` (en général `backend`,
  mais peut être nommé autrement, ou il peut y en avoir plusieurs déjà).
- S'il en existe au moins un, choisir le plus simple/représentatif comme
  **modèle vivant** — à la fois pour sa stack (Node, Java/Maven, Python...)
  et pour sa "plomberie" CI/plateforme (`Dockerfile`, `Makefile`,
  `sonar-project.properties`, fichiers de build/lint spécifiques à cette
  stack) : ces fichiers encodent des choix de la plateforme (image de base,
  outil de lint, wiring Sonar) qu'il ne faut pas réinventer.
- S'il n'en existe **aucun** (ce serait le premier backend du dépôt) : ce
  skill ne peut pas deviner la stack à utiliser. S'arrêter et demander au
  BA/à son encadrant technique quelle stack utiliser (node, maven ou
  python — cf. `ci-template/README.md`), plutôt que d'en imposer une.

### 7. Dupliquer ce modèle, réduit au strict minimum ("Hello World")

Copier uniquement les fichiers suivis par git du composant modèle choisi
(jamais `node_modules/`, `dist/`, `coverage/`, `target/`, `.venv/` même
s'ils existent sur le poste) :
```
mkdir -p <slug>
git ls-files <composant-modèle> | while read -r f; do
  dest="<slug>${f#<composant-modèle>}"
  mkdir -p "$(dirname "$dest")"
  cp "$f" "$dest"
done
```
Vérifier que le compte de fichiers copiés correspond
(`find <slug> -type f | wc -l` ≈ `git ls-files <composant-modèle> | wc -l`).

Puis, en lisant le code copié (peu importe le langage), réduire au minimum
"Hello World" :
- **Garder** : le point d'entrée de l'application, le branchement à la base
  de données (générique, lu depuis des variables d'environnement — cf.
  `helm.yaml`), **une seule route minimale qui répond "Hello World"**
  (ou équivalent déjà présent dans le modèle — ne pas en inventer une si le
  modèle en a déjà une), et **l'infrastructure de logging applicatif** du
  modèle (logger JSON structuré sur stdout respectant la variable
  d'environnement `LOG_LEVEL`, intercepteur/middleware de couche
  `controller`, mécanisme réutilisable de couche `service` — annotation,
  décorateur ou aspect selon la stack —, logging des requêtes DB lentes ou
  en erreur) : c'est de la plomberie générique au même titre que le
  `Dockerfile`, pas un exemple métier, même si elle vit sous `src/` — ne
  jamais la retirer ni la réinventer, quel que soit son nom dans la stack
  détectée.
- **Retirer** tout module/ressource métier d'exemple plus riche qu'un
  "Hello World" (ex. un module CRUD de démonstration, ses migrations, ses
  tests dédiés) — et toute référence à ce module dans le fichier
  d'assemblage de l'application. Ne pas confondre avec l'infrastructure de
  logging ci-dessus (à garder) : seul le module métier d'exemple et ses
  propres tests partent, pas la plomberie de logging qui l'entourait (ex.
  l'annotation/décorateur de logging posée sur son service disparaît avec
  lui, mais le mécanisme réutilisable qui la définit reste). L'objectif est
  un squelette qui démarre, répond, passe les tests, et que le BA remplira
  lui-même — pas un exemple métier à adapter.
- Adapter les quelques identifiants qui doivent changer, quel que soit le
  langage : nom du projet/paquet dans son fichier de métadonnées
  (`package.json`, `pom.xml`, `pyproject.toml`...), nom de la base de
  données par défaut dans la config locale/dev (`<nom-de-base-du-slug>` de
  l'étape 4).
- Remplacer le `README.md` par une version courte et propre pour ce
  service (nom, comment lancer `make lint`/`test-unit`/`test-integration`,
  rappel que c'est un squelette minimal). Ne pas copier un README par
  défaut du framework tel quel s'il contient des éléments trompeurs (ex. un
  faux jeton dans un badge, qui déclenche une fausse alerte gitleaks à
  chaque scan).
- Ne pas toucher aux fichiers de plomberie CI/plateforme identifiés à
  l'étape 6 (`Dockerfile`, `Makefile`, `sonar-project.properties`, config de
  lint/format/build de la stack) : ils sont déjà corrects pour cette
  plateforme et cette stack.

### 8. Vérifier que le squelette fonctionne avant de rendre la main

Depuis `<slug>/`, lancer les commandes d'installation puis `make lint` et
`make test-unit` (les commandes d'installation dépendent de la stack
détectée à l'étape 6 : `npm install` pour Node, `mvn install`/équivalent
pour Java, `pip install -r requirements.txt`/`poetry install` pour Python).

Vérifier aussi, en conditions réelles, que l'image Docker de **ce** nouveau
composant démarre et répond — tous les BA ont Docker installé, ce test ne
doit donc jamais être sauté silencieusement :

1. Démarrer la base de données du composant si le modèle en fournit une
   (`compose.yaml` copié à l'étape 7) : `cd <slug> && docker compose up -d`.
2. Construire l'image : `docker build -t <slug> .`
3. Lancer un conteneur de test, branché sur le **réseau créé par ce
   `docker compose`** (pas sur le port 5432 de l'hôte : il est souvent déjà
   pris par un autre PostgreSQL local, le conteneur de test échouerait à
   tort). Trouver ce réseau (`docker network ls | grep <slug>`, en général
   `<slug>_default`), puis joindre la base par son nom de service
   (`postgres`) à l'intérieur de ce réseau ; seul le port de l'app est
   exposé à l'hôte, sur un port libre (ex. `8099` — en choisir un autre
   s'il est déjà pris) :
   ```
   docker run -d --name <slug>-test \
     --network <slug>_default \
     -e DATABASE_URL="postgresql://app:app@postgres:5432/<nom-de-base-du-slug>" \
     -p 8099:<containerPort> <slug>
   ```
4. Laisser quelques secondes au conteneur pour démarrer, puis vérifier
   réellement la réponse sur `http://localhost:8099/` (`curl -sf
   http://localhost:8099/`, ou équivalent si le modèle répond sur une autre
   route "Hello World") : la commande doit réussir et renvoyer le contenu
   attendu, pas seulement démarrer sans erreur.
5. Nettoyer dans tous les cas (même en cas d'échec) :
   `docker rm -f <slug>-test` puis `docker compose down`.

Si `docker` n'est exceptionnellement pas disponible sur ce poste, le dire
simplement au BA — ce sera de toute façon vérifié par la CI (`build-push`)
dès le premier merge.

En cas d'échec à cette étape (squelette cassé, y compris le Dockerfile ou
la connexion à la base), c'est un problème de ce skill, pas du travail du
BA : corriger directement, ne pas demander au BA de déboguer un code qu'il
n'a pas encore touché.

`make test-integration` peut nécessiter des services (base de données via
Docker, etc.) et être lent : le lancer si les prérequis sont disponibles,
sinon le dire simplement au BA plutôt que de bloquer dessus.

### 9. Ajouter le service à `helm.yaml` et aux `values-<env>.yaml`

Dans `helm.yaml` (ce qui est identique dans tous les environnements), ajouter
une entrée sous `applications:`, sur le même modèle que l'application backend
utilisée comme modèle à l'étape 6 :

```yaml
  <slug>:
    type: backend
    image: 205493924920.dkr.ecr.eu-west-3.amazonaws.com/<nom-du-dépôt>/<slug>
    containerPort: <identique au modèle : le port qu'écoute réellement l'app copiée>
    service:
      port: 80
    database: <nom-de-base-du-slug>
    env:
      <mêmes variables que le modèle, mêmes valeurs communes>
```

Ajouter aussi la base sous `database.databases` de `helm.yaml`
(`<nom-de-base-du-slug>: {}`, à la suite des bases existantes ; ne pas toucher
au reste du bloc `database`).

Nom du dépôt = `gh repo view --json name -q .name` (pas une valeur à recopier
d'un autre dépôt). Pas d'`ingress` (backend = exposé par le gateway). Reprendre
les commentaires du modèle (consigne `DATABASE_URL`, etc.) en les adaptant au
nouveau nom.

Dans **chaque** `values-<env>.yaml` présent à la racine (`values-dev.yaml`,
`values-qualif.yaml`, ...), ajouter la même application avec un tag vide (aucune
image construite encore : Kargo l'écrira après le premier merge ; tant qu'il
est vide, l'application n'est pas déployée dans cet environnement), les mêmes
`replicas` que le modèle dans ce fichier, et ses variables propres à
l'environnement s'il en a :

```yaml
  <slug>:
    tag: ""
    replicas: <identique au modèle dans ce fichier>
```

### 10. Ajouter la route du gateway

Un backend `type: backend` dans `helm.yaml` est **automatiquement**
surveillé par Kargo dès le premier merge (aucune action externe, cf.
`kargo-pipelines` de `formation-ba-gitops`). Pour qu'il soit **joignable
depuis l'extérieur**, il lui faut une route dans la section `gateway` de
`helm.yaml` :

- `gateway.enabled: true` : ajouter sous `gateway.routes`, à la suite des
  routes existantes et sur le même modèle (mêmes commentaires) :
  ```yaml
      <slug>:
        path: /<slug>
        service: <slug>
        stripPrefix: true
        cors: true
        middlewares: []
  ```
  Le backend sera alors joignable sur
  `https://gateway.<nom-du-dépôt>.<env>.check-consulting.net/<slug>/...`
  (préfixe retiré, CORS selon `gateway.cors.origins`). Ne pas toucher aux
  autres routes, à `cors` ni aux `middlewares`.
- `gateway.enabled: false` ou section absente : rien à faire ici. Le dire
  simplement au BA : ce backend tourne et est surveillé par Kargo, mais n'est
  joignable que depuis l'intérieur du cluster tant que le gateway du projet
  n'est pas activé (activer le gateway est un choix à part, pas automatique).

Relire ensuite `helm.yaml` et les `values-<env>.yaml` modifiés en entier
(indentation : 2 espaces par niveau). Si le dépôt `formation-ba-gitops` est
présent à côté de ce dépôt (`../formation-ba-gitops`), vérifier le rendu :

```
helm dependency build ../formation-ba-gitops/chart-base/charts-ba-formations/<version>
helm template test ../formation-ba-gitops/chart-base/charts-ba-formations/<version> \
  -f helm.yaml -f values-dev.yaml --set env=dev --set project=<nom-du-dépôt>
```
(`<version>` : `business.charts.app` de `../formation-ba-gitops/gitops/values.yaml`).
Sinon, le dire simplement : ce sera vérifié par ArgoCD après le merge.

### 11. Conclure en langage simple

Résumer : le dossier `<slug>/` est prêt (squelette "Hello World" vérifié),
la base de données dédiée est déclarée, l'espace de travail "<nom>" est
ouvert, et la route du gateway (étape 10) a été ajoutée si le gateway est
activé. Le BA peut maintenant développer sa logique métier dans `<slug>/`.
Le logging JSON (couches `controller`/`db`) fonctionne déjà sans rien
faire ; pour la couche `service`, dire simplement au BA d'appliquer à ses
futures classes métier le même mécanisme que celui déjà utilisé dans le
modèle (ex. une annotation/décorateur à poser sur la classe) pour que ses
méthodes soient loguées automatiquement.
**Ce skill n'a rien commité ni poussé** — quand le BA aura une première
version, `deploy` s'occupera du commit, du push, de la PR et du suivi.

## Ce que ce skill ne fait jamais

- Ne suppose jamais une stack ou un nom de dossier fixe — détecte toujours
  à partir de ce qui existe réellement dans le dépôt où il est lancé
  (étape 6), et s'arrête pour demander plutôt que d'en inventer une s'il
  n'y a encore aucun backend.
- Ne copie jamais un exemple métier complet (type CRUD de démonstration) —
  s'en tient à "Hello World", quelle que soit la stack.
- Ne commit, ne push et n'ouvre jamais de PR — 100% local, comme
  `nouvelle-fonctionnalite`. C'est `deploy` qui s'occupe de tout envoi vers
  GitHub.
- N'écrase jamais un dossier de composant existant portant le même nom —
  s'arrête et demande un autre nom.
- N'active jamais lui-même le gateway (`gateway.enabled`) si le projet ne
  l'a pas déjà — c'est une décision à part, pas une conséquence
  automatique de la création d'un backend (étape 10).
- Ne supprime jamais de travail en cours sans réponse explicite du BA.
- Ne modifie jamais le dépôt `formation-ba-gitops` — n'en a pas besoin pour
  un nouveau composant d'un projet déjà existant (tout est dans le dépôt du
  projet : `helm.yaml`, `values-<env>.yaml`).
- Ne force jamais un `reset --hard` sur `main` sans confirmation explicite.
