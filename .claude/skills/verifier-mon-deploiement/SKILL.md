---
name: verifier-mon-deploiement
description: Vérifie, pour un Business Analyst (BA), si sa dernière mise en ligne a réellement abouti sur dev ou qualif — image construite par la CI, version écrite par Kargo dans helm.yaml, application qui répond via le frontend et le gateway — explique où ça bloque en langage simple, et donne les liens utiles (Kargo pour promouvoir, ArgoCD pour l'état, Grafana pour les logs métier et HTTP filtrés sur son projet). À utiliser quand le BA demande si son déploiement a marché, pourquoi il ne voit pas son changement, où en est la mise en ligne, ou après un deploy / une promotion qualif.
---

# Vérifier mon déploiement

Public : un Business Analyst, pas un développeur. Résultat attendu : un
résumé simple par composant (✅ en ligne / ⏳ en cours / ❌ bloqué, et pourquoi).

Chaîne à vérifier, dans l'ordre :
merge sur `main` → CI `ci.yaml` construit l'image (tag = 7 premiers
caractères du commit de merge) → Kargo l'écrit dans `helm.yaml`
(`tag.<env>`, commit `promote(<env>): ...` sur `main` ; dev automatique, Kargo
regarde toutes les minutes ; qualif seulement après « Promote ») → ArgoCD
déploie l'Application `<env>-<dépôt>` → l'application répond.

Valeurs : `<dépôt>` = `gh repo view --json name -q .name` (jamais en dur),
domaine `check-consulting.net`, outils de la plateforme sur
`<outil>.dev.check-consulting.net` (un seul cluster pour dev et qualif).

## Étapes

### 1. Quelle mise en ligne, quel environnement

- Environnement : dev par défaut, qualif si le BA le dit.
- Mise en ligne : la dernière PR mergée de la branche courante
  (`gh pr list --head <branche> --state merged --limit 1 --json number,title,mergeCommit`),
  sinon demander au BA de quelle fonctionnalité il parle.
- `<sha>` = 7 premiers caractères de `mergeCommit.oid`.
- Composants concernés : dossiers avec `Dockerfile` modifiés par ce merge
  (`git fetch origin` puis `git diff --name-only <oid>^1 <oid>`). Aucun (ex.
  seul `gateway.yaml` ou `helm.yaml` modifié) : pas d'image, passer
  directement à l'étape 4.

### 2. L'image a-t-elle été construite ?

`gh run list --workflow ci.yaml --commit <oid> --json databaseId,status,conclusion`
- En cours : ⏳ (« la construction est en cours, quelques minutes »), proposer
  d'attendre (`gh run watch <id>`).
- Échec : ❌, `gh run view <id> --log-failed`, traduire la cause simplement.

### 3. Kargo a-t-il écrit la version ?

Lire `helm.yaml` de `main` : `git fetch origin` puis
`git show origin/main:helm.yaml`. Pour chaque composant concerné, comparer
`tag.<env>` à `<sha>`.
- Égal : la version est demandée, passer à l'étape 4.
- **dev** différent : ⏳ si l'image a été construite il y a moins de 5 minutes
  (revérifier chaque minute, jusqu'à 10 minutes) ; au-delà, ❌ et donner le
  lien Kargo de la Stage dev.
- **qualif** différent : normal tant que le BA n'a pas promu. Donner le lien
  de promotion ; préciser qu'on promeut la version déjà en dev, donc qu'elle
  doit d'abord y être (✅ en dev).
- Un `promote(<env>)` plus récent porte un autre sha : quelqu'un a mis en
  ligne après le BA ; son changement est inclus si son commit est un ancêtre
  (`git merge-base --is-ancestor <oid> <commit du tag>`) — alors ✅.

### 4. L'application répond-elle ?

Quelques secondes à 2 minutes après l'écriture de Kargo (ArgoCD remplace les
pods) :
- Frontend : `curl -s -o /dev/null -w "%{http_code}" https://frontend.<dépôt>.<env>.check-consulting.net/`
- Chaque backend, via le gateway : `curl -s -o /dev/null -w "%{http_code}" https://gateway.<dépôt>.<env>.check-consulting.net/<backend>/`
- 200 : ✅. 502/503 : le composant ne démarre pas ou pas encore — revérifier
  une minute plus tard ; si ça persiste, ❌ : donner le lien ArgoCD (état des
  pods et leurs logs de démarrage) et Grafana (logs métier) ; causes
  fréquentes à citer : migration de base en échec, variable manquante, erreur
  au démarrage. 404 sur un backend : sa route manque dans `gateway.yaml`.

### 5. Liens à donner

- Kargo, Stage de l'environnement (promotion qualif : bouton « Promote ») :
  `https://kargo.dev.check-consulting.net/project/<dépôt>/stage/<env>`
- ArgoCD, état du déploiement (lecture seule pour les BA) :
  `https://argocd.dev.check-consulting.net/applications/argocd/<env>-<dépôt>`
- Grafana, logs métier de son backend (couches controller/service/db) :
  `https://grafana.dev.check-consulting.net/d/logs-business?var-env=<env>&var-project=<dépôt>&var-app=<backend>`
- Grafana, trafic HTTP de son projet (codes de réponse, routes, erreurs) :
  `https://grafana.dev.check-consulting.net/d/logs-http?var-source=gateway&var-env=<env>&var-project=<dépôt>`

### 6. Résumer

Une ligne par composant (✅/⏳/❌ + explication simple), puis la prochaine
action : attendre, promouvoir vers qualif, ou corriger (et dans ce cas,
proposer d'aider à corriger puis `deploy`).

## Ce que ce skill ne fait jamais

- Ne modifie rien : ni code, ni `helm.yaml` (les tags sont écrits par Kargo),
  ni commit, ni push.
- Ne promeut jamais à la place du BA : il donne le lien.
- Ne relance jamais une CI ou une synchronisation en boucle.
