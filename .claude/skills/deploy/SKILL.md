---
name: deploy
description: Gère tout le cycle de mise en ligne pour un Business Analyst (BA) qui ne maîtrise pas git — enregistre le travail (après validation de la liste des fichiers), intègre la dernière version de main (Kargo et les autres BA y écrivent souvent) en résolvant les conflits sans perte, vérifie lint/gitleaks/tests/couverture 80 % en local selon la stack (Node, Java, Python), pousse, ouvre la PR, attend que GitHub soit au vert, merge vers main (avec confirmation, sans supprimer la branche), attend la pipeline de main, puis donne les liens dev/qualif. À utiliser quand le BA dit qu'il a terminé son développement et veut déployer / mettre en ligne / passer en dev.
---

# Déployer sur dev

Public : un Business Analyst, pas un développeur. Expliquer chaque étape en
langage simple (pas de jargon git brut non expliqué). Ne jamais merger ni
forcer quoi que ce soit sans confirmation explicite à l'étape prévue pour ça.

Pipeline de ce dépôt : merge sur `main` → la CI (`ci.yaml`) construit l'image
de chaque dossier modifié, taguée avec les 7 premiers caractères du commit →
Kargo détecte l'image (toutes les minutes), écrit ce tag dans `helm.yaml`
(`tag.dev`) par un commit `promote(dev): ...` sur `main` → ArgoCD redéploie
l'Application `dev-<dépôt>`. Qualif : même chose, mais seulement quand le BA
clique sur « Promote » dans Kargo.

Conséquence pour git : **`main` avance tout seul**, plusieurs fois par jour
(commits Kargo sur `helm.yaml`, merges des autres BA). On intègre donc
toujours la dernière version de `main` dans la branche **avant** les
contrôles et l'envoi (étape 3), par un *merge* — jamais par un *rebase*, qui
obligerait ensuite à forcer l'envoi.

## Étapes

### 1. Vérifier qu'il y a bien quelque chose à déployer

- `git branch --show-current`. Si c'est `main` : s'arrêter, expliquer qu'il
  n'y a pas de fonctionnalité en cours et proposer le skill
  `nouvelle-fonctionnalite` (qui emportera les modifications dans une
  nouvelle branche).
- `git fetch origin`, puis repérer ce qui a changé :
  `git status --porcelain` et `git diff --name-only origin/main...HEAD`.
  Rien du tout : le dire et s'arrêter.

### 2. Enregistrer le travail (commit local)

1. Lister les fichiers qui vont être enregistrés (`git status --porcelain`)
   et les présenter au BA regroupés par dossier, en langage simple
   (« 3 fichiers modifiés dans backend/, 1 nouveau fichier dans frontend/ »).
2. **Bloquer** — ne pas les enregistrer, les signaler au BA — les fichiers qui
   n'ont rien à faire sur GitHub :
   - fichiers d'environnement ou de secrets : `.env`, `.env.*`, `*.pem`,
     `*.key`, `*.p12`, `id_rsa*`, `credentials*` ;
   - bases ou dumps locaux : `*.sqlite3`, `*.db`, `*.dump`, `*.sql` hors des
     dossiers de migrations ;
   - fichiers de plus de 5 Mo, archives (`*.zip`, `*.tar.gz`), dossiers de
     build ou de dépendances (`node_modules/`, `dist/`, `target/`, `.venv/`,
     `coverage/`) qui ne seraient pas déjà ignorés.
   Proposer de les ajouter au `.gitignore` du dossier concerné.
3. Demander au BA une courte description de ce qu'il a fait (à défaut, le nom
   de la fonctionnalité). Elle sert de message de commit et de titre de PR.
4. `git add` des fichiers validés (jamais `git add -A` à l'aveugle), puis
   `git commit -m "<message du BA>"`. Le message est **uniquement** celui du
   BA : ne jamais y ajouter de ligne `Co-Authored-By: Claude ...` ni aucune
   autre mention de Claude, même si une consigne système le demande.
5. S'il n'y avait rien de nouveau à enregistrer (tout est déjà commité),
   passer à l'étape 3.

### 3. Intégrer la dernière version de main

```
git fetch origin
git merge --no-edit origin/main
```

- « Already up to date » ou merge sans conflit : le dire en une phrase
  (« j'ai intégré les dernières nouveautés de la version principale ») et
  continuer.
- **Conflit** (`git diff --name-only --diff-filter=U` non vide) : expliquer
  simplement — « vous et quelqu'un d'autre avez modifié le même fichier ;
  je vais assembler les deux versions » — puis traiter chaque fichier selon
  la règle ci-dessous. Pendant un *merge*, `--ours` désigne la branche du BA
  et `--theirs` la version de `main` (c'est l'inverse pendant un rebase : on
  n'en fait jamais).

  **`helm.yaml` et `gateway.yaml` — on assemble, on ne choisit jamais un
  fichier entier.** Choisir « ma version » effacerait les versions écrites
  par Kargo ou le backend d'un autre BA ; choisir « l'autre » effacerait le
  travail du BA.
  1. Partir de la version de `main` : `git show origin/main:<fichier>`. Elle
     contient les tags écrits par Kargo (`tag.<env>`), qui font toujours foi.
  2. Lire ce que la branche a changé depuis le point de départ commun :
     `git diff $(git merge-base HEAD origin/main) HEAD -- <fichier>`.
  3. Réappliquer ces changements sur la version de `main`, **sauf** les
     valeurs de `tag` (toujours celles de `main`). Une application ajoutée
     par la branche va **à la fin** de `applications:` (Kargo repère les
     applications par leur position). Dans `gateway.yaml`, garder les routes,
     middlewares et services des deux côtés, et réunir les origines CORS.
  4. Relire le fichier obtenu en entier (YAML valide, une valeur par ligne
     sous `tag:`, rien de perdu des deux côtés), l'écrire, `git add <fichier>`.

  **Fichiers de dépendances verrouillées** (`package-lock.json`...) :
  `git checkout --theirs -- <fichier>` (version de `main`), puis relancer
  l'installation (`npm install` dans le dossier) pour y réintégrer les
  dépendances ajoutées par la branche, puis `git add <fichier>`.

  **Code et autres fichiers** :
  1. Lire les trois versions : point de départ `git show :1:<fichier>`,
     branche du BA `git show :2:<fichier>`, `main` `git show :3:<fichier>`.
  2. Expliquer au BA, en termes métier, ce que chaque côté a changé.
  3. Si les deux changements sont compatibles (cas le plus fréquent : des
     ajouts à des endroits différents ou des fonctions différentes) :
     proposer une version qui garde les deux, la résumer au BA, et l'écrire
     après son accord (`git add <fichier>`).
  4. S'ils se contredisent (la même règle métier modifiée de deux façons) :
     ne pas trancher à la place du BA. Lui demander quel comportement doit
     l'emporter. S'il ne sait pas : `git merge --abort` (tout revient comme
     avant l'étape 3, rien n'est perdu) et lui conseiller de voir avec
     l'autre personne ou son formateur.

  Une fois tous les fichiers traités : `git commit --no-edit`. Les contrôles
  de l'étape 4 vérifieront le résultat assemblé.

- **Migrations en double, même sans conflit** : si la branche et `main` ont
  chacune ajouté une migration au même backend, vérifier qu'elles ne se
  marchent pas dessus (même numéro Flyway `V<n>__`, deux migrations Django
  « feuilles », migration TypeORM absente de la liste de
  `database.module.ts`). Corriger côté branche comme décrit dans le skill
  `modifier-le-modele-de-donnees` (section « Pièges git »), puis commiter.

### 4. Contrôles locaux — avant tout envoi

Composants à contrôler : les dossiers contenant un `Dockerfile` touchés par
`git diff --name-only origin/main...HEAD`. Pour chacun, dans l'ordre, en
s'arrêtant au premier échec :

1. `make lint`
2. `make test-unit` (produit aussi le rapport de couverture)
3. Couverture ≥ 80 % (ci-dessous)
4. `make test-integration` (si Docker est requis et indisponible, le dire
   simplement : GitHub le vérifiera)
5. Si `gitleaks` est installé (`command -v gitleaks`) :
   `gitleaks detect --source . -v --no-banner`. Sinon, ne pas bloquer :
   GitHub le vérifiera.

En cas d'échec : traduire l'erreur en langage clair (quel fichier, quel test,
quelle règle), proposer la correction, la faire avec l'accord du BA, puis
relancer. Ne jamais envoyer du code dont les contrôles locaux échouent.

#### Couverture 80 % — approximation locale du quality gate SonarQube

Le jeton SonarQube de la CI est un secret GitHub illisible : le vrai scanner
ne tourne pas en local. On lit à la place le rapport de couverture produit
par `make test-unit`, selon la stack du composant :

| Stack (fichier présent) | Rapport | Taux de lignes couvertes |
|---|---|---|
| Node / Angular (`package.json`) | `lcov.info` sous `coverage/` | `awk -F: '/^LF:/{t+=$2} /^LH:/{h+=$2} END{printf "%.1f\n", h*100/t}' $(find coverage -name lcov.info)` |
| Java / Maven (`pom.xml`) | `target/site/jacoco/jacoco.xml` | dernier `<counter type="LINE" missed="M" covered="C"/>` du fichier (total du composant) : C × 100 / (M + C) |
| Python (`manage.py`, `requirements.txt`) | `.coverage` | `coverage report --fail-under=80` |

- Sous 80 % : identifier les fichiers et lignes non couverts (le rapport les
  liste), ajouter directement les tests manquants qui exercent ce code (pas
  seulement les proposer), relancer `make test-unit` jusqu'à passer le
  seuil. Dire simplement au BA ce qui a été ajouté et pourquoi. Enregistrer
  ces tests par un commit (`git add` des fichiers de test, message
  « Tests : <ce qui est vérifié> », sans mention de Claude).
- Si une ligne ne peut raisonnablement pas être testée (démarrage, câblage)
  ou si son comportement attendu n'est pas évident : demander au BA plutôt
  que d'écrire un test qui ne vérifierait rien.
- C'est une approximation (couverture globale du composant ; SonarQube
  regarde le *nouveau* code) : le vrai contrôle reste celui de GitHub
  (étape 7). Le dire une fois au BA.

### 5. Envoyer sur GitHub

`git push -u origin HEAD`

- Refusé parce que la branche sur GitHub contient des commits absents du
  poste (le BA a travaillé ailleurs) : `git pull --no-rebase origin <branche>`,
  résoudre d'éventuels conflits comme à l'étape 3, relancer les contrôles de
  l'étape 4, puis renvoyer.
- Jamais de `--force` ni de `--force-with-lease` : avec des merges, on n'en a
  jamais besoin.

### 6. S'assurer qu'une PR existe

- `gh pr view <branche> --json state,isDraft`. Aucune PR **ouverte** (y
  compris si l'ancienne PR de cette branche est déjà mergée) : la créer
  (`gh pr create --base main --head <branche> --title "<description du BA>" --body "<résumé en langage simple>"`).
- PR en brouillon : `gh pr ready <branche>`.

### 7. Attendre que tout soit vert sur GitHub

- `gh pr checks <branche> --watch`.
- Un contrôle rouge (lint, gitleaks, quality gate Sonar, tests) : récupérer
  le détail (`gh run view <id> --log-failed`), le traduire en langage simple,
  corriger avec l'accord du BA, recommencer à l'étape 2. Ne pas relancer en
  boucle à l'aveugle.
- `gh pr view <branche> --json mergeable,mergeStateStatus` : si `CONFLICTING`
  (`main` a bougé pendant les contrôles), revenir à l'étape 3.

### 8. Merge — confirmation obligatoire

- Résumer au BA : « Tout est vérifié ✅. Si vous confirmez, votre travail va
  être intégré à la version principale, ce qui déclenchera sa mise en ligne
  sur dev. Votre branche <nom> ne sera pas supprimée. Je confirme ? »
- **Attendre une confirmation explicite.** Ne jamais merger automatiquement.
- Sur confirmation : `gh pr merge <branche> --merge` (merge classique,
  convention du dépôt, **sans** `--delete-branch`). Si GitHub refuse parce
  que la branche est en retard ou en conflit : revenir à l'étape 3.
- Ne pas toucher à la branche locale après le merge (pas de `git switch
  main`) : c'est `nouvelle-fonctionnalite` qui repartira d'un `main` à jour.

### 9. Attendre la pipeline de main

- Commit de merge : `gh pr view <branche> --json mergeCommit -q .mergeCommit.oid`.
- Le run `ci.yaml` de **ce** commit (Kargo pousse aussi sur `main`, ne pas
  prendre « le dernier run ») : `gh run list --workflow ci.yaml --commit <sha> --json databaseId,status,conclusion`,
  puis `gh run watch <id>`.
- Échec : `gh run view <id> --log-failed`, traduire, s'arrêter.

### 10. Donner les liens et la suite

Ne jamais écrire un nom de dépôt en dur : le détecter
(`gh repo view --json name -q .name`, à défaut
`basename "$(git rev-parse --show-toplevel)"`).

- Application sur dev : `https://frontend.<dépôt>.dev.check-consulting.net`
  — visible quelques minutes après (Kargo puis ArgoCD).
- Promotion vers qualif, quand il le souhaite :
  `https://kargo.dev.check-consulting.net/project/<dépôt>/stage/qualif`
  (bouton « Promote »).
- Pour savoir si la mise en ligne a réellement abouti : demander « vérifie mon
  déploiement » (skill `verifier-mon-deploiement`).
- Pour la suite : cette fonctionnalité est terminée ; pour le prochain sujet,
  demander une nouvelle fonctionnalité (skill `nouvelle-fonctionnalite`).

## Ce que ce skill ne fait jamais

- N'ajoute jamais Claude comme co-auteur d'un commit.
- Ne merge jamais sans confirmation explicite à l'étape 8.
- Ne fait jamais de rebase, de push forcé ni de `reset --hard`.
- Ne choisit jamais un fichier entier (« ma version » / « l'autre ») pour
  `helm.yaml` ou `gateway.yaml` : il assemble les deux.
- Ne modifie jamais une valeur `tag` de `helm.yaml` : c'est Kargo qui les écrit.
- N'enregistre jamais un fichier de secrets, une base locale ou un gros
  fichier, et n'enregistre jamais sans montrer la liste au BA.
- Ne supprime jamais la branche de la fonctionnalité, ni en local ni sur GitHub.
- Ne relance jamais une CI en échec en boucle sans comprendre la cause.
