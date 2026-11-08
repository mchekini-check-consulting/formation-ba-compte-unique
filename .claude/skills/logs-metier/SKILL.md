---
name: logs-metier
description: Ajoute les logs métier des backends du dépôt selon la convention de la plateforme (une ligne JSON par log, champ layer controller/service/db, niveau LOG_LEVEL), en Java/Spring Boot, Node (Express, NestJS) ou Python/Django, de façon interactive pour un Business Analyst (BA) — liste les backends et l'état de leurs logs, demande lesquels traiter, puis pour chacun, en tenant compte des logs déjà en place : pour chaque couche (controller, service, db) présente son intérêt et demande au BA s'il l'active ; si oui, pour chaque méthode présente l'intérêt de la journaliser et demande son accord, puis liste les informations de la ligne (champs obligatoires imposés par la plateforme + champs métier proposés, à approuver un par un, jamais de donnée personnelle). Ne commit ni ne push rien (rôle du skill deploy). À utiliser quand le BA veut ajouter, modifier ou retirer des logs métier, suivre une action dans Grafana, tracer un parcours, ou « voir ce qui se passe » dans un backend.
---

# Logs métier

Public : un Business Analyst, pas un développeur. Parler d'« actions »,
d'« écrans », de « règles métier » et de « données » ; expliquer chaque terme
technique en une phrase. **Rien n'est activé sans l'accord explicite du BA** :
un backend, puis une couche, puis une méthode, puis chacun de ses champs.

Ce skill est le même dans tous les dépôts `formation-ba-*` : il ne suppose
jamais une stack, un nom de dossier ni un état des logs ; il les lit dans
**ce** dépôt.

## Convention de la plateforme (non négociable)

Les logs des backends sont lus par Alloy puis envoyés dans Loki ; le
dashboard Grafana « Logs métier » les affiche par environnement, projet,
application et couche.

- **Une ligne JSON par log sur stdout.**
- **Champ `layer`** = `controller`, `service` ou `db` sur chaque log métier.
  Une ligne sans `layer` (démarrage, framework, ORM…) est **jetée** par
  Alloy : elle n'apparaît jamais dans le dashboard (elle reste visible avec
  `kubectl logs`).
- **Niveau minimal `LOG_LEVEL`** (variable de `helm.yaml`) : `debug` en dev,
  `info` en qualif et preprod, `warn` en prod. En prod, seules les lignes
  `warn`/`error` (échec, lenteur) sont donc visibles.
- **Jamais de donnée personnelle ni de secret** : nom, prénom, e-mail,
  téléphone, adresse, code postal, date de naissance, IBAN/BIC, mot de passe,
  code de vérification, jeton, en-tête `Authorization`, IP, n° de sécurité
  sociale ; ni corps de requête complet, ni paramètre ou valeur SQL, ni
  message d'erreur brut qui pourrait en contenir. Ces champs ne sont **jamais
  proposés**, même si le BA les demande : lui expliquer pourquoi (RGPD, logs
  lisibles par toute l'équipe et conservés).

### Champs obligatoires (écrits sur chaque ligne, sans question)

| Couche | Informations imposées par la plateforme |
|---|---|
| toutes | date et heure (`time`), niveau (`level`), message (`msg`), couche (`layer`) ; ajoutés par la plateforme : `env`, `project`, `application` |
| controller | verbe HTTP, route déclarée (modèle de l'adresse, ex. `/devis/enregistres/{reference}`, jamais ses valeurs), code HTTP (`status`), durée (`durationMs`) |
| service | classe et méthode, durée (`durationMs`), type d'erreur en cas d'échec |
| db | méthode (ou requête paramétrée, sans valeurs), durée (`durationMs`), type d'erreur en cas d'échec ; ligne `warn` à partir de **200 ms** (appel lent) |

**Noms des champs** : garder ceux que la plomberie du backend écrit déjà
(ex. `httpMethod`, `class`, `method`, `requestId`, `error`/`errorCode` en
Node et Python ; `method`, `route`, `status` en Java) — le dashboard et les
requêtes existantes s'en servent. Pour une plomberie neuve, reprendre ceux
de l'implémentation de référence de la même stack dans les autres dépôts
`formation-ba-*` s'ils sont présents, sinon : `method`, `route`, `status`,
`durationMs`, `err`.

Niveaux : `info` quand tout va bien ; `warn` pour une erreur métier (4xx),
un appel db lent ; `error` (ou `warn` si la plomberie existante le fait
ainsi) pour une panne (5xx, exception inattendue). Garder les niveaux déjà
en place.

## Étapes

### 1. Contexte

- Être sur une branche `feature/...` (sinon : skill `nouvelle-fonctionnalite`).
- Dire au BA en deux phrases ce qu'on va faire : choisir backend par
  backend, couche par couche, puis méthode par méthode, ce qui sera visible
  dans Grafana.

### 2. Lister les backends et l'état de leurs logs

Backends = applications `type: backend` de `helm.yaml` (dossier `<name>/` à
la racine). Pour chacun, lire le code et établir :

- **Stack** : `pom.xml` → Java / Spring Boot ; `package.json` → Node
  (NestJS si `@nestjs/core`, sinon Express ; ORM : TypeORM, Drizzle, Prisma…) ;
  `manage.py` → Python / Django (Django REST Framework si
  `rest_framework`).
- **Plomberie de logs** (dossier `logging/` ou équivalent) : sortie JSON
  (Java : `logback-spring.xml` + layout JSON ; Node : `pino`/`winston`
  configuré en JSON ; Python : `LOGGING` de `settings.py` + formatter JSON),
  `LOG_LEVEL` branché, mécanisme par couche.
- **Logs déjà actifs**, couche par couche, et **comment** :
  - controller : intercepteur / middleware **global** (toutes les requêtes)
    ou par route ;
  - service : activation **par classe ou par objet** (`@Loggable` Java,
    `loggable('XService', {...})` Node, `@loggable` Python : toutes les
    méthodes publiques) ou par méthode ;
  - db : journal **global** des requêtes lentes ou en erreur (`DataSource`
    Java, `execute_wrapper` Django, logger de l'ORM Node) ou par méthode ;
  - champs métier déjà écrits, le cas échéant.

Présenter un tableau au BA, par exemple :

| Backend | Stack | Logs en place |
|---|---|---|
| `backend` | Java / Spring Boot | aucun (pas de plomberie) |
| `contrats` | Node / Express | controller : toutes les requêtes ; service : `ContratService` (toutes ses méthodes) ; db : aucun |

Puis demander **quels backends traiter** (choix multiple). Les traiter **un
par un**, dans l'ordre de `helm.yaml`, avec les étapes 3 à 8 ; annoncer à
chaque fois le backend en cours.

### 3. Plomberie : réutiliser l'existant, compléter seulement ce qui manque

La plomberie est le code technique commun qui écrit les lignes ; le BA n'a
rien à décider ici. Le principe du skill est l'**activation méthode par
méthode, avec des champs approuvés**. Il faut donc, pour le backend en
cours :

1. **Garder** tout ce qui existe et respecte la convention : sortie JSON,
   `LOG_LEVEL`, `requestId`, description d'erreur sans données (ex.
   `erreur.ts`), niveaux, noms des champs, `/health` en `debug`, tests. Ne
   jamais réécrire une plomberie qui marche.
2. **Ajouter** le mécanisme d'activation par méthode qui manque, avec la
   même syntaxe de champs quelle que soit la stack :
   - chemins `<paramètre>.<propriété>...` ou `resultat.<propriété>...` ;
     `taille` = nombre d'éléments d'une liste ; valeur absente ou facultative
     dépliée ; valeur simple = texte, nombre, booléen, enum, identifiant,
     date, ou liste de ceux-ci (écrite `a,b,c`) ;
   - le champ est écrit dans la ligne sous son chemin (`"demande.formule":
     "confort"`), à côté des champs obligatoires ;
   - un champ illisible à l'exécution est ignoré : un log ne doit **jamais**
     faire échouer l'action métier ;
   - le contexte d'une ligne ne doit pas déborder sur la suivante (ni sur
     l'appel englobant : une méthode db appelée depuis un service).

   | Stack | Activation d'une méthode | Couche controller |
   |---|---|---|
   | Java / Spring Boot | annotation de méthode `@LogMetier(champs = {...})`, aspect Spring AOP (`spring-boot-starter-aspectj`) ; couche `db` si la cible est un `Repository` Spring Data (annotation sur la méthode de l'interface), sinon `service` ; contexte MDC restauré après chaque ligne | `HandlerInterceptor` (code HTTP connu en fin de requête) qui n'écrit que pour une méthode de contrôleur annotée ; les champs approuvés lui sont confiés par l'aspect |
   | Node / NestJS | décorateur de méthode `@LogMetier({ champs: [...] })` | intercepteur Nest qui n'écrit que pour un handler décoré |
   | Node / Express | enveloppe par fonction : `logMetier('service', 'XService', { creer: { champs: [...] }, lister: {} }, fonctions)` — une fonction absente de la liste n'est pas journalisée | middleware de route `logMetier({ champs: ['params.reference', 'body.formule'] })` posé sur les seules routes retenues, au lieu d'un middleware global |
   | Python / Django | décorateur `@log_metier(champs=[...])` sur la méthode ou la fonction (paramètres désignés par leur nom, `resultat` pour le retour) | même décorateur sur la vue ou l'action DRF (code HTTP lu dans la réponse) ; le middleware ne fait plus que poser le contexte (`requestId`, hook db) |

   Couche db hors Java : décorer/envelopper les fonctions d'accès aux données
   (repository, module `db/`, managers ou fonctions de requête Django).
3. **Test de convention** (Java : `LogMetierConventionTest` ; Node :
   `*.test.ts` ; Python : `test_logging.py`) qui parcourt toutes les
   activations et échoue si un champ n'existe pas, n'est pas une valeur
   simple, est une donnée personnelle ou porte un nom de champ obligatoire.
   Tests unitaires de toute la plomberie ajoutée (couverture ≥ 80 %).
4. `LOG_LEVEL` présent dans `envList` du backend dans `helm.yaml` (sinon
   l'ajouter : `{dev: debug, qualif: info, preprod: info, prod: warn}`) et lu
   par la configuration des logs (Java : `logging.level.<paquet racine>:
   ${LOG_LEVEL:info}` dans `application.yaml`).

`make lint` et `make test-unit` doivent passer **avant** de continuer.

**Activations globales existantes** (middleware de toutes les requêtes,
`@Loggable` / `loggable()` / `@loggable` de toute une classe, journal global
des requêtes lentes) : ne pas les retirer ici. Elles sont présentées au BA
aux étapes 4 et 5 comme l'état actuel, et ne sont remplacées par des
activations méthode par méthode qu'avec son accord (étape 6).

### 4. Couche par couche : le BA active ou non

Traiter les couches dans l'ordre **controller → service → db**. Pour chacune :

1. Présenter son intérêt en langage métier :
   - **controller — les actions des utilisateurs** : chaque action d'un écran
     qui appelle le backend (enregistrer un devis, signer un mandat…), son
     résultat (réussie, refusée, introuvable, en erreur) et son temps de
     réponse. Utile pour compter les usages d'un parcours, voir les erreurs
     subies par les clients, repérer les lenteurs.
   - **service — les règles métier** : chaque calcul ou décision (tarif,
     vérification d'un code, reprise d'un devis expiré), sa durée et ses
     échecs métier (devis inconnu, code incorrect). Utile pour comprendre
     pourquoi un parcours échoue et quels choix font les clients.
   - **db — l'accès aux données** : chaque lecture ou écriture choisie, sa
     durée, les accès lents (≥ 200 ms, visibles même en prod) et les erreurs
     de base. Utile pour diagnostiquer une lenteur.
2. Montrer les **champs obligatoires** de la couche, avec les noms de ce
   backend et un exemple de ligne, par exemple :
   ```json
   {"time":"2026-11-07T10:15:02Z","level":"info","msg":"service call completed","layer":"service","method":"TarifService.prixMensuel","durationMs":"4"}
   ```
3. Rappeler **ce qui est déjà actif** dans cette couche (étape 2) :
   « aujourd'hui, toutes les requêtes sont journalisées », « toutes les
   méthodes de `CompteService` le sont », ou la liste des méthodes déjà
   activées et leurs champs.
4. Demander : « Activer les logs de la couche <couche> pour <backend> ? »
   (Oui / Non, outil de question à choix si disponible).
   - **Non** et rien d'actif : couche suivante, sans rien modifier.
   - **Non** alors que des logs sont actifs : demander s'il faut les
     **retirer** ou les **laisser tels quels** ; ne retirer qu'avec un oui
     explicite.

Prévenir une fois : activer la même action dans les trois couches produit
trois lignes par action ; mieux vaut viser les actions qui comptent.

### 5. Méthode par méthode : le BA active ou non

Inventaire de la couche (lire le code, ne rien supposer) :

| Couche | Java / Spring Boot | Node | Python / Django |
|---|---|---|---|
| controller | méthodes `@GetMapping`/`@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping` des `@RestController` | routes Express (`router.get/post/...`) ou handlers des `@Controller` Nest | vues, `ViewSet`/actions DRF, chemins de `urls.py` |
| service | méthodes publiques des `@Service`/`@Component` (pour une interface, chaque implémentation) | fonctions des objets service (`loggable(...)`) ou méthodes des `@Injectable` | méthodes publiques des classes de service, fonctions de règles métier |
| db | méthodes **déclarées** dans les interfaces de repository (`findBy...`, `deleteBy...`, `@Query`) ; les héritées (`save`, `findById`, `delete`) ne sont pas proposées : proposer plutôt la méthode service qui les appelle | fonctions d'accès aux données (repository, module `db/`, requêtes ORM nommées) | managers / fonctions de requête nommées |

Présenter chaque méthode comme une action métier : controller « verbe +
route — action de l'écran » (`POST /devis/enregistres` — enregistrer un
devis), service et db « ce que fait la règle ou la requête », en s'appuyant
sur la documentation du code et les US citées. Ignorer les routes techniques
(`/`, `/health`, « Hello World ») ; signaler les bouchons, rarement utiles.

Pour **chaque** méthode, une à la fois :
1. Présenter son intérêt : l'événement métier qu'elle représente et ce que le
   BA pourra en apprendre (volume, durée, taux d'échec, choix des clients).
   Préciser si elle est déjà journalisée et avec quels champs.
2. Demander : « Activer le log de <méthode> ? » (Oui / Non). Non : méthode
   suivante.
3. Si oui, présenter **les informations qui figureront dans la ligne** :
   - les **champs obligatoires** de la couche (rappel, sans question) ;
   - les **champs métier proposés**, un par un : chemin (`demande.formule`),
     signification métier, exemple de valeur (`confort`). Les construire en
     parcourant les paramètres et le résultat (DTO, entités, modèles,
     schémas de validation) jusqu'aux valeurs simples. Utile : identifiants
     techniques (`reference`), choix (`formule`, `options`, `franchise`),
     catégories (`typeLogement`), montants calculés, nombre d'éléments
     (`resultat.taille`), statuts ;
   - les **champs écartés** et pourquoi (donnée personnelle, secret, valeur
     non simple) : le BA ne peut pas les réactiver.
4. Faire approuver chaque champ proposé : choix multiple (au plus 4 par
   question ; au-delà, plusieurs questions, ou liste numérotée à laquelle le
   BA répond par les numéros). Aucun champ approuvé est un choix valable : la
   ligne n'aura que les champs obligatoires.

Activation globale existante dans la couche (toutes les requêtes, toute une
classe) : la passer en revue méthode par méthode de la même façon ; les
méthodes refusées ne seront plus journalisées une fois la bascule faite
(étape 6). Le dire clairement au BA.

### 6. Récapitulatif et accord final (par backend)

Afficher un tableau : couche, méthode, état actuel, état après (activée /
retirée / inchangée), champs obligatoires, champs approuvés. Signaler chaque
activation globale qui sera remplacée par des activations méthode par
méthode. Demander la confirmation avant de modifier le code ; ajuster si le
BA change d'avis.

### 7. Appliquer

- Poser l'activation (annotation, décorateur, enveloppe ou middleware de
  route de l'étape 3) sur chaque méthode retenue, avec ses champs approuvés.
- Retirer les activations refusées ; remplacer une activation globale
  approuvée en bascule (middleware global, `@Loggable`/`loggable()`/
  `@loggable` de classe, journal global db) par les activations méthode par
  méthode.
- Ne rien changer d'autre dans les méthodes. Adapter les tests existants qui
  supposaient l'activation globale, sans en supprimer la portée.

### 8. Vérifier

1. Dans le dossier du backend : `make lint` puis `make test-unit`. Si le test
   de convention échoue, expliquer le champ en cause au BA et corriger avec
   lui (retirer ou remplacer le champ).
2. Démonstration locale si le BA le souhaite : lancer le backend (skill
   `lancer-en-local`), faire l'action dans l'écran, montrer dans la console
   les lignes JSON avec `"layer"` et les champs approuvés.

Puis backend suivant (retour à l'étape 3).

### 9. Fin

Récapituler par backend ce qui est activé. Dire où le retrouver après la mise
en ligne (skill `deploy`) : Grafana, dashboard « Logs métier », filtres
environnement / projet / application / couche ; en prod, seules les lignes
`warn` et `error` apparaissent.

Ne pas commiter : le skill `deploy` s'en charge.
