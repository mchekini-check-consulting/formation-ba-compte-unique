---
name: appeler-un-backend
description: Branche le frontend sur un backend du projet en respectant l'architecture de la plateforme — le navigateur appelle toujours le gateway du projet (https://gateway.<dépôt>.<env>.../<backend>/...), jamais un backend en direct ni une URL écrite en dur ; en local, le serveur de dev relaie /api/<backend> vers le backend lancé sur le poste. Généralise apiBase pour plusieurs backends, ajoute le relais local, écrit l'appel (React ou Angular) et son test. À utiliser quand le BA veut afficher / envoyer des données depuis l'écran vers un backend, appeler une API, ou brancher un nouveau backend au frontend.
---

# Appeler un backend depuis le frontend

Public : un Business Analyst, pas un développeur. Expliquer en langage simple,
sans jargon non expliqué.

## L'architecture à respecter (à expliquer au BA en une phrase si utile)

```
navigateur ──> https://frontend.<dépôt>.<env>.check-consulting.net   (l'écran)
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

## Étapes

### 1. Vérifier le contexte

- Être sur une branche `feature/...` (sinon : skill `nouvelle-fonctionnalite`).
- Repérer le frontend (`type: frontend` dans `helm.yaml`) et sa stack :
  `vite.config.js` → React/Vite ; `angular.json` → Angular.
- Demander au BA quelles données il veut afficher ou envoyer, et depuis quel
  écran.

### 2. Identifier le backend et la route

- Le backend : une application `type: backend` de `helm.yaml`. S'il n'existe
  pas encore : skill `nouveau-backend` d'abord.
- La route : la lire dans le code du backend (contrôleur NestJS
  `@Get/@Post`, Spring `@GetMapping/@PostMapping`, Django `urls.py`) avec la
  forme du JSON renvoyé ou attendu.
- La route n'existe pas : la créer dans le backend en respectant ses couches
  (contrôleur → service → accès aux données), le logging de la couche service
  (`@Loggable()` ou équivalent du dépôt) et un test unitaire. Si elle a
  besoin d'une nouvelle table : skill `modifier-le-modele-de-donnees`.

### 3. `apiBase` : une adresse par backend

Ouvrir le fichier qui contient `apiBase` (`src/api.js` en React,
`src/app/api.ts` en Angular).

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
    (étape 4), sinon l'existant ne marche plus en local.

### 4. Relais local du serveur de dev

Un relais par backend, vers son port local (README du composant ; modèle :
Node 3000, Java 8080, Django 8000 ; backends ajoutés : port suivant).

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

### 5. Écrire l'appel

React — dans `src/api.js`, sur le modèle des fonctions existantes :
```js
export function listerContrats(base = apiBase('<backend>')) {
  return appeler('/contrats', undefined, base)
}
```

Angular — un service par ressource, sur le modèle de `src/app/items.ts` :
```ts
@Injectable({ providedIn: 'root' })
export class ContratsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('<backend>')}/contrats`;
  lister(): Observable<Contrat[]> { return this.http.get<Contrat[]>(this.url); }
}
```

Puis l'utiliser dans l'écran demandé (chargement, affichage, message d'erreur
compréhensible si l'appel échoue).

### 6. Vérifier le gateway (CORS et route)

Dans `gateway.yaml` : le backend doit avoir son router `PathPrefix(`/<backend>`)`
avec `[<backend>-strip, cors]` (le skill `nouveau-backend` l'a ajouté), et
l'origine `https://<frontend>.{{ .project }}.{{ .env }}.{{ .domain }}` doit
figurer dans `cors.headers.accessControlAllowOriginList` (à ajouter si le
frontend ne s'appelle pas `frontend`).

### 7. Tester

- Test unitaire de la nouvelle fonction ou du nouveau service (appel simulé :
  `fetch` mocké avec vitest, `HttpTestingController` en Angular) et de
  l'écran si sa logique change. `make test-unit` dans le dossier du frontend.
- Essai réel en local : skill `lancer-en-local`, puis ouvrir l'écran.

### 8. Conclure

Dire au BA ce qui est branché (écran → backend → route), comment l'essayer en
local, et que la mise en ligne se fait avec `deploy` (backend et frontend
dans la même fonctionnalité).

## Ce que ce skill ne fait jamais

- N'écrit jamais d'URL complète, de nom d'environnement, de `localhost` ou de
  nom de dépôt dans le code du frontend (seulement via `apiBase` et le relais
  local du serveur de dev).
- Ne fait jamais appeler un backend directement par le navigateur, n'ajoute
  jamais d'Ingress à un backend, n'ouvre jamais le CORS à `*`.
- Ne commit et ne push rien (rôle de `deploy`).
