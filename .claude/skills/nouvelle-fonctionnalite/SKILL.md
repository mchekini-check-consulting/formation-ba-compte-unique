---
name: nouvelle-fonctionnalite
description: Démarre puis accompagne le développement d'une fonctionnalité pour un Business Analyst (BA) qui ne maîtrise ni git ni le développement — met de côté proprement le travail en cours, synchronise main avec GitHub, crée la branche feature/<nom> en local ; puis, pendant le développement frontend ou backend, fait évoluer le modèle de données quand la fonctionnalité modifie une partie métier existante ou en ajoute une nouvelle (toujours par une nouvelle migration - Flyway, Drizzle, Django), et demande toujours au BA s'il faut relier l'écran et le backend avant de faire la liaison (appel via le gateway avec apiBase, relais local /api/<backend>, CORS, tests). Pas de push ni de PR (rôle du skill deploy). À utiliser quand le BA veut commencer / développer une nouvelle fonctionnalité, créer une branche, repartir sur un nouveau sujet après une mise en ligne, ajouter ou modifier une table, un champ, une information à stocker, ou afficher / envoyer des données depuis l'écran vers un backend, appeler une API.
---

# Nouvelle fonctionnalité

Public : un Business Analyst, pas un développeur. Ne jamais afficher de jargon
git ou technique (rebase, HEAD, migration, CORS...) sans l'expliquer en une
phrase simple. Ne jamais faire d'action destructrice (reset --hard, suppression
de branche, écrasement de modifications, suppression de données) sans demander
confirmation d'abord.

Règle de travail à rappeler au BA quand c'est utile : **une fonctionnalité =
une branche**, créée depuis une version à jour de `main`. Une fois la
fonctionnalité mise en ligne (skill `deploy`), on ne continue pas sur la même
branche : on relance ce skill pour le sujet suivant. `main` bouge souvent sans
que le BA y touche (Kargo y écrit les versions déployées dans `helm.yaml`,
plusieurs fois par jour, et les autres BA y fusionnent leur travail) : c'est
pour ça que chaque branche part d'un `main` fraîchement synchronisé.

Le skill a deux parties :
- **A. Ouvrir l'espace de travail** (étapes 1 à 7) : la branche `feature/<nom>`.
- **B. Développer la fonctionnalité** (étapes 8 à 14) : données métier,
  backend, frontend et **liaison entre les deux**.

Si le BA est déjà sur une branche `feature/...` pas encore mise en ligne et
demande une évolution (« ajoute un champ », « affiche les contrats dans
l'écran »), passer directement à la partie B.

**Les décisions reviennent au BA.** Chaque question est posée avec l'outil de
question à choix (AskUserQuestion), avec des options tirées du contexte du
dépôt (option recommandée en premier) ; le BA peut toujours répondre autre
chose (« Autre »). Seule la description du besoin, en langage métier, est
libre.

## A. Ouvrir l'espace de travail

1. **Vérifier qu'on est dans un dépôt git** (`git rev-parse --is-inside-work-tree`).
   Si non : expliquer au BA qu'il doit ouvrir le dossier de son projet.

2. **Vérifier l'état courant** (`git branch --show-current`, `git status --porcelain`).
   - **Modifications non enregistrées sur une branche `feature/...`** :
     s'arrêter et demander au BA (choix) ce qu'il veut en faire :
     - « les mettre en ligne d'abord » → proposer le skill `deploy`, et
       s'arrêter là ;
     - « les garder pour plus tard » → les enregistrer **sur cette branche,
       en local uniquement** (rien n'est envoyé sur GitHub) :
       `git add -A` puis `git commit -m "Travail en cours : <résumé>"`. Dire
       au BA que son travail l'attend sur cette branche et qu'il suffira de
       demander à reprendre « <nom> » pour y revenir (`git switch <branche>`) ;
     - « les abandonner » → demander une **deuxième confirmation explicite**
       (« ces modifications seront définitivement perdues »), puis
       `git restore --staged --worktree .` et `git clean -fd` uniquement sur
       les fichiers listés au BA.
     Ne jamais choisir à sa place.
   - **Modifications non enregistrées sur `main`** (cas anormal : le BA a
     modifié du code sans branche) : ne rien perdre. Les emporter dans la
     nouvelle branche : passer directement à l'étape 4, puis à l'étape 6
     faire `git switch -c feature/<slug>` (les modifications suivent), et
     **sauter** la synchronisation de l'étape 3 ; le dire au BA (« votre
     travail est maintenant rangé dans la fonctionnalité <nom> ; il sera mis à
     jour avec la version de GitHub au moment de la mise en ligne »).
   - **Branche `feature/...` propre** : vérifier si elle a déjà été mise en
     ligne (`gh pr list --head <branche> --state merged --json number -q length`).
     - Déjà mise en ligne : le dire (« la fonctionnalité <nom> est terminée et
       en ligne ») et continuer normalement : on repart de `main`.
     - Pas encore mise en ligne : dire au BA qu'il a déjà un espace de travail
       ouvert et lui demander (choix) s'il en crée un nouveau ou continue
       celui-ci (partie B directement).

3. **Synchroniser main avec GitHub** :
   ```
   git fetch origin
   git switch main
   git pull --ff-only origin main
   ```
   - Si `switch` échoue à cause de modifications en cours : revenir à l'étape 2.
   - Si `pull --ff-only` échoue (le `main` local contient des changements qui
     n'existent pas sur GitHub, cas anormal) : s'arrêter, l'expliquer
     simplement, lister ces changements (`git log --oneline origin/main..main`)
     et proposer de les ranger dans une branche avant toute remise à niveau
     (`git branch sauvegarde/main-<date>`). Seulement après cette sauvegarde
     et avec un accord explicite : `git reset --hard origin/main`.
   - Vérifier ensuite `git rev-parse main` == `git rev-parse origin/main`.

4. **Demander le nom de la fonctionnalité** en langage simple, par exemple :
   « Quel est le nom de la fonctionnalité que vous allez développer ? »
   (ne pas parler de « nom de branche » ni de « PR »). Proposer 2 ou 3 noms
   si le BA a déjà décrit son besoin.

5. **Construire le nom de branche** : minuscules, accents retirés, espaces et
   caractères spéciaux remplacés par des tirets, préfixe `feature/`.
   Exemple : « Filtre par date » → `feature/filtre-par-date`.
   Vérifier qu'elle n'existe pas déjà, ni en local
   (`git rev-parse --verify --quiet feature/<slug>`) ni sur GitHub
   (`git ls-remote --heads origin feature/<slug>`). Si elle existe :
   demander au BA (choix) s'il reprend ce travail (`git switch feature/<slug>`)
   ou choisit un autre nom.

6. **Créer la branche, en local uniquement** :
   ```
   git switch -c feature/<slug>
   ```
   Ne pas pousser ni créer de pull request ici : tant qu'il n'y a aucun
   commit, il n'y a rien à envoyer. C'est le skill `deploy` qui s'en charge.

7. **Annoncer** que l'espace de travail « <nom> » est prêt, puis enchaîner sur
   la partie B si le BA a déjà décrit son besoin ; sinon lui demander ce que
   doit faire la fonctionnalité.

## B. Développer la fonctionnalité

### 8. Comprendre le besoin et repérer ce qu'il touche

Lire le contexte, sans rien modifier :
- les applications de `helm.yaml` (`name`, `type`) et la stack de chaque
  dossier : `pom.xml` → Spring Boot ; `package.json` avec `express` → Express,
  avec `@angular/core` → Angular, avec `react` → React ; `manage.py` → Django ;
- les **données métier existantes** de chaque backend : migrations
  (`src/main/resources/db/migration/`, `drizzle/`, `<app>/migrations/`),
  entités et modèles (`@Entity`, `src/db/schema.ts`, `models.py`) ;
- les **routes** des backends (Spring `@GetMapping`/`@PostMapping`…, routeurs
  Express `router.get/post`, Django `urls.py`) et la forme de leur JSON ;
- les **écrans** des frontends (routes Angular `app.routes.ts`, composants
  React) et leurs appels existants (`apiBase(...)`).

Faire décrire le besoin au BA en langage métier, puis lui **reformuler un plan**
et le faire valider (choix : « C'est ça » / « Modifier ») :
- les composants touchés (choix multiple parmi les applications de
  `helm.yaml`) ; si la fonctionnalité a besoin d'un composant qui n'existe pas
  (nouveau backend, nouvel écran séparé) : skill `nouveau-composant` d'abord,
  puis revenir ici ;
- les données à stocker ou à changer (étape 9) ;
- les routes backend à créer ou à modifier (étape 10) ;
- les écrans à créer ou à modifier (étape 11) ;
- les liaisons écran → backend envisagées (étape 12).

### 9. Données métier : faire évoluer le modèle de données (si besoin)

À traiter dès que la fonctionnalité **modifie une partie métier existante**
(nouveau champ, champ modifié, nouveau lien entre deux informations) ou
**ajoute une nouvelle partie métier** (nouvelle table, nouvel ensemble
d'informations à conserver). Parler au BA de « tables » et de « champs ».

**Décision du BA.** Présenter ce qui a été relevé (tables et champs existants
qui ressemblent au besoin) et demander (choix) :
- « Modifier une partie métier existante : <table> » (une option par table
  concernée) ;
- « Ajouter une nouvelle partie métier » (nouvelle table) ;
- « Pas de changement de données » (la fonctionnalité n'utilise que des
  données déjà stockées) → passer à l'étape 10.

Pour une nouvelle partie métier assez grande pour vivre seule (son propre
cycle de vie, ses propres écrans, une autre équipe) : demander (choix) si elle
va dans un backend existant (recommandé par défaut) ou dans un nouveau backend
(skill `nouveau-composant`).

Puis reformuler la structure proposée (tables, champs, type, obligatoire ou
non, liens) et attendre l'accord du BA.

#### Règles (toutes les stacks)

- **Le schéma ne change que par une nouvelle migration.** Chaque backend
  applique ses migrations au démarrage, dans l'ordre, sur sa base (dev,
  qualif, preprod, prod). L'application ne crée ni ne modifie jamais son
  schéma elle-même (Spring `ddl-auto: validate`).
- **Ne jamais modifier ni supprimer une migration qui est déjà sur `main`** :
  elle a déjà été appliquée en dev ou qualif. La modifier fait échouer le
  démarrage (Flyway et Drizzle comparent une empreinte de chaque fichier) ou
  laisse les bases dans des états différents. On écrit une nouvelle migration
  qui corrige. Une migration créée sur la branche en cours et jamais mise en
  ligne peut, elle, être modifiée.
- **Préférer les changements qui ajoutent** (table, champ). Un nouveau champ
  obligatoire sur une table qui a déjà des lignes doit avoir une valeur par
  défaut, sinon la migration échoue en dev et qualif.
- **Compatible avec la version précédente du code** : en preprod et prod,
  2 réplicas tournent pendant un déploiement (ancien et nouveau code sur la
  même base). Un renommage se fait en plusieurs fonctionnalités (ajout, copie,
  puis suppression plus tard).
- **Suppression ou renommage** de table ou de champ : prévenir le BA que les
  données existantes en dev et qualif seront perdues ou à reprendre, et
  obtenir son accord explicite (choix « Je confirme la suppression » /
  « Garder le champ »).
- Noms en minuscules avec underscores (`date_effet`), clé primaire `id
  bigint generated by default as identity`, horodatages `timestamp with time
  zone`, comme les migrations existantes du backend.

#### Écrire la migration et l'entité, selon la stack

**Spring Boot / Flyway**
1. Fichier `src/main/resources/db/migration/V<n>__<nom>.sql`, avec `<n>` = plus
   grand numéro existant + 1 (`V3__contrat.sql`). Deux tirets bas.
2. Entité JPA `@Entity` (paquet `model`) dont les colonnes correspondent
   exactement à la migration : au démarrage, `ddl-auto: validate` refuse toute
   différence. Repository Spring Data dans `repository`.

**Express / Drizzle**
1. Modifier `src/db/schema.ts` (une table par export `pgTable`).
2. Générer la migration : `npm run db:generate` (crée `drizzle/<n>_<nom>.sql`
   et met à jour `drizzle/meta/`) — jamais écrite ni renommée à la main ;
   la relire.
3. Elle est appliquée au démarrage (`src/server.ts` → `migrate`) ; à la main :
   `npm run db:migrate`.

**Django**
1. Modifier ou ajouter le modèle dans `models.py` de l'application.
2. Générer la migration : `python manage.py makemigrations <application>`
   (jamais écrite à la main), et la relire.
3. Elle est appliquée au démarrage du conteneur (`gunicorn.conf.py`) ; en
   local : `python manage.py migrate`.

#### Vérifier contre une vraie base

1. `docker compose up -d` dans le dossier du backend (ou à la racine, selon le
   README) : base locale.
2. Démarrer le backend en local (skill `lancer-en-local`, ou la commande du
   README du composant) : la migration doit s'appliquer sans erreur et
   l'application démarrer. En Spring, une erreur `Schema-validation` signifie
   que l'entité ne correspond pas à la migration.
3. `make test-unit`, puis `make test-integration` (base réelle jetable) si
   Docker est disponible.
4. Si une migration de la branche est fausse et jamais mise en ligne : la
   corriger, puis repartir d'une base locale vide (`docker compose down -v` —
   prévenir le BA que les données locales seront effacées).

### 10. Backend : routes et règles métier

Pour chaque route à créer ou à modifier (plan de l'étape 8) :
- respecter les couches du backend : contrôleur (HTTP) → service (règles
  métier) → accès aux données (repository Spring Data, requêtes Drizzle,
  ORM Django) ; conventions de `CLAUDE.md` ;
- renvoyer du JSON et des codes HTTP clairs (404 si absent, 400 si la
  demande est invalide) ;
- un test unitaire pour chaque nouvelle règle ou route ;
- ne jamais mettre de données personnelles dans les logs. Pour suivre les
  nouvelles méthodes dans Grafana, proposer au BA (choix) d'activer leurs logs
  métier avec le skill `logs-metier` (« Oui, maintenant » / « Plus tard »).

### 11. Frontend : écrans

Créer ou modifier les écrans du plan, dans l'organisation du frontend
(`CLAUDE.md`) : chargement, affichage, message d'erreur compréhensible si les
données ne viennent pas. Un test unitaire pour chaque écran dont la logique
change.

### 12. Liaison frontend ↔ backend : toujours demander au BA

Dès que la fonctionnalité crée ou modifie un écran qui affiche ou envoie des
données, ou une route backend qu'un écran pourrait utiliser, **demander au BA
avant de faire la liaison** — même si elle paraît évidente, et même si le BA
n'a parlé que du frontend ou que du backend. Une question par liaison
envisagée, avec le contexte relevé à l'étape 8 :

> « L'écran <écran> doit-il <afficher / envoyer> <données> via le backend
> <backend> (route <méthode> /<route>) ? »
> - « Oui, relier maintenant » (recommandé si les deux existent)
> - « Oui, mais avec un autre backend ou une autre route » (lister ceux de
>   `helm.yaml`)
> - « Non, pas dans cette fonctionnalité »

Plusieurs écrans ou backends possibles : choix multiple. Ne jamais relier sans
réponse, ne jamais sauter la question. Une liaison refusée est rappelée dans
la conclusion (étape 14).

#### L'architecture à respecter (à expliquer au BA en une phrase si utile)

```
navigateur ──> https://<frontend>.<dépôt>.<env>.check-consulting.net   (l'écran)
navigateur ──> https://gateway.<dépôt>.<env>.check-consulting.net/<backend>/<route>
                 └─ gateway : retire /<backend>, vérifie l'origine (CORS) ──> backend
```

- Le navigateur n'appelle **que le gateway**. Un backend n'a pas d'adresse
  publique (pas d'Ingress) ; il n'est joignable que par
  `/<backend>/...` sur le gateway, avec `<backend>` = son `name` dans
  `helm.yaml`.
- L'adresse du gateway est **calculée** à partir de l'adresse de la page
  (fonction `apiBase`) : la même image tourne en dev, qualif, preprod et prod.
  Ne jamais écrire en dur une URL, un nom d'environnement, `localhost` ou un
  nom de dépôt dans le code du frontend.
- En local, il n'y a pas de gateway : `apiBase` renvoie `/api/<backend>` et
  le serveur de dev du frontend relaie vers le backend lancé sur le poste, en
  retirant le préfixe — exactement comme le gateway.
- Le gateway n'accepte que les appels venant des frontends du projet
  (middleware `cors` de `gateway.yaml`).

#### a. La route du backend

La lire dans le code du backend (étape 8) avec la forme du JSON renvoyé ou
attendu. Si elle n'existe pas : la créer (étape 10) ; si elle a besoin de
nouvelles données : étape 9.

#### b. `apiBase` : une adresse par backend

Ouvrir le fichier qui contient `apiBase` (React : `src/api.js` ou `src/api.ts` ;
Angular : `src/app/core/config/api.ts` ou `src/app/api.ts` — chercher
`apiBase` dans `src/`).

- **Elle prend déjà le nom du backend** (`apiBase(backend, hostname)`) : rien
  à changer.
- **Elle a `/backend` écrit en dur** (`apiBase(hostname)`) : la généraliser,
  sans changer son comportement pour le backend existant :
  ```js
  export function apiBase(backend, hostname = window.location.hostname) {
    const [, projet, env, ...domaine] = hostname.split('.')
    if (!projet || !env || domaine.length === 0) {
      return `/api/${backend}`
    }
    return `https://gateway.${projet}.${env}.${domaine.join('.')}/${backend}`
  }
  ```
  (Angular : même code typé, `location.hostname`.) Puis :
  - remplacer chaque appel `apiBase()` par `apiBase('backend')` ;
  - mettre à jour les tests de `apiBase` (`localhost` → `/api/backend`) ;
  - renommer l'entrée du relais local existante de `/api` en `/api/backend`
    (point c), sinon l'existant ne marche plus en local.

#### c. Relais local du serveur de dev

Un relais par backend appelé, vers son port local (README du composant, sinon
les valeurs par défaut : Express 3000, Spring 8080, Django 8000 ; backends
ajoutés : port suivant).

React/Vite — `vite.config.js`, `server.proxy` :
```js
'/api/<backend>': {
  target: 'http://localhost:<port>',
  rewrite: (path) => path.replace(/^\/api\/<backend>/, ''),
},
```

Angular — `proxy.conf.json` (les entrées les plus précises d'abord) :
```json
"/api/<backend>": {
  "target": "http://localhost:<port>",
  "pathRewrite": { "^/api/<backend>": "" }
}
```

#### d. Écrire l'appel

React — dans le fichier de `apiBase`, sur le modèle des fonctions existantes :
```js
export function listerContrats() {
  return appeler('<backend>', '/contrats')
}
```

Angular — un service par ressource, dans le dossier des services du frontend
(`CLAUDE.md`), sur le modèle d'un service existant :
```ts
@Injectable({ providedIn: 'root' })
export class ContratsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('<backend>')}/contrats`;
  lister(): Observable<Contrat[]> { return this.http.get<Contrat[]>(this.url); }
}
```

Puis l'utiliser dans l'écran (chargement, affichage, message d'erreur
compréhensible si l'appel échoue).

#### e. Vérifier le gateway (CORS et route)

Dans `gateway.yaml` (s'il existe) : le backend doit avoir son router
`PathPrefix(`/<backend>`)` avec `[<backend>-strip, cors]` (ajouté par le skill
`nouveau-composant`), et l'origine
`https://<frontend>.{{ .project }}.{{ .env }}.{{ .domain }}` doit figurer dans
`cors.headers.accessControlAllowOriginList` (l'ajouter sinon ; jamais `*`).
Ne pas toucher aux autres routes ni aux middlewares d'authentification.

#### f. Tester la liaison

- Test unitaire de la nouvelle fonction ou du nouveau service (appel simulé :
  `fetch` mocké avec vitest, `HttpTestingController` en Angular), et de
  l'écran si sa logique change.

### 13. Vérifier l'ensemble

Dans chaque composant modifié : `make lint`, `make test-unit`, et pour un
backend `make test-integration` (Docker requis ; sinon le dire). Sous Windows
sans `make` : lancer les commandes du `Makefile` directement. Couverture
≥ 80 % (quality gate SonarQube).

Puis l'essai réel : skill `lancer-en-local` (bases, backends, frontend avec son
relais), et faire ouvrir l'écran au BA pour qu'il valide le comportement.

### 14. Conclure en langage simple

Résumer : ce qui a changé dans les données (tables, champs), les routes
backend, les écrans, et ce qui est relié (écran → backend → route) ; les
liaisons que le BA a choisi de remettre à plus tard. Dire que les migrations
s'appliqueront toutes seules en dev après la mise en ligne, puis en qualif lors
de la promotion. Rappeler les demandes utiles :
- « lance l'application en local » (skill `lancer-en-local`) ;
- « crée un nouveau backend ou frontend » (skill `nouveau-composant`) ;
- « active les logs métier » (skill `logs-metier`) ;
- « déploie » quand il a terminé (skill `deploy`).

## Pièges git des migrations

Si un autre BA a ajouté une migration en même temps, l'intégration de `main`
(skill `deploy`, étape 3) peut faire apparaître **deux migrations
concurrentes**, sans conflit git visible. Après chaque intégration de `main`,
corriger toujours **côté branche** (jamais la migration de `main`) :
- Flyway : deux `V<n>__` avec le même numéro → renuméroter celle de la branche ;
- Drizzle : deux fichiers `drizzle/<n>_...` ou conflit dans `drizzle/meta/` →
  supprimer la migration de la branche (fichier SQL, snapshot et entrée du
  journal), garder `drizzle/meta/` de `main`, puis `npm run db:generate` ;
- Django : deux migrations « feuilles » → `python manage.py makemigrations --merge`.

## Ce que ce skill ne fait jamais

- Ne pousse rien et n'ouvre jamais de PR : il reste 100 % local (seul `main`
  est lu depuis GitHub). Le commit « Travail en cours » de l'étape 2 reste
  local.
- Ne force jamais un push, et ne fait jamais de `reset --hard` sans
  sauvegarde préalable et confirmation explicite.
- Ne supprime jamais une branche existante ni des modifications sans double
  confirmation.
- Ne modifie ni ne supprime jamais une migration déjà présente sur `main` ;
  n'active jamais `ddl-auto: update` / `create` ; ne modifie jamais une base
  dev ou qualif à la main ; ne supprime jamais de données sans l'accord
  explicite du BA.
- Ne relie jamais un écran à un backend sans l'avoir demandé au BA.
- N'écrit jamais d'URL complète, de nom d'environnement, de `localhost` ou de
  nom de dépôt dans le code du frontend (seulement via `apiBase` et le relais
  local du serveur de dev) ; ne fait jamais appeler un backend directement par
  le navigateur, n'ajoute jamais d'Ingress à un backend, n'ouvre jamais le
  CORS à `*`.
