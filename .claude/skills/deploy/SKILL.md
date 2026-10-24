---
name: deploy
description: Gère tout le cycle de mise en ligne pour un Business Analyst (BA) qui ne maîtrise pas git — vérifie lint/gitleaks/tests/quality gate Sonar en local, commit, push, attend que les contrôles GitHub passent au vert, merge la PR vers main (avec confirmation, sans supprimer la branche), attend que la pipeline GitHub Actions sur main soit au vert, puis donne le lien de l'application frontend sur dev et le lien de promotion vers qualif dans Kargo. À utiliser quand le BA dit qu'il a terminé son développement et veut déployer / mettre en ligne / passer en dev.
---

# Déployer sur dev

Public : un Business Analyst, pas un développeur. Expliquer chaque étape en
langage simple (pas de jargon git brut non expliqué). Ne jamais merger ou
forcer un push sans confirmation explicite à l'étape prévue pour ça.

Rappel du pipeline réel de ce dépôt (cf. `ci-template/README.md` et
`chart-base/kargo-pipelines/README.md`) : merge sur `main` → build de l'image
et push vers ECR (`ci.yaml`) → Kargo détecte la nouvelle image et la promeut
automatiquement vers la Stage `dev` (il écrit le tag dans `helm.yaml` et
pousse un commit sur `main`) → ArgoCD synchronise l'Application `dev-<repo>`.
Le déploiement sur dev est donc **automatique après le merge**, mais pas
instantané (Kargo scrute ECR périodiquement). Le rôle de ce skill s'arrête
une fois la pipeline GitHub Actions sur `main` au vert : il ne surveille pas
activement la promotion Kargo ni la synchronisation ArgoCD, il se contente
de donner au BA les deux liens utiles pour qu'il vérifie lui-même, à son
rythme.

## Étapes

### 1. Vérifier qu'il y a bien quelque chose à déployer

- `git branch --show-current`. Si c'est `main` : s'arrêter, expliquer qu'il
  n'y a pas de fonctionnalité en cours et qu'il faut d'abord utiliser le
  skill `nouvelle-fonctionnalite`.
- Repérer les dossiers de composants modifiés (dossiers contenant un
  `Dockerfile` touchés par le diff avec `main`) :
  `git diff --name-only origin/main...HEAD` + `git status --porcelain`
  (fichiers non committés inclus).

### 2. Contrôles locaux — avant tout commit/push

Pour chaque composant modifié, dans l'ordre, en s'arrêtant au premier échec :

1. `make lint`
2. `make test-unit`
3. `make test-integration`
4. Si la commande `gitleaks` est disponible (`command -v gitleaks`) :
   `gitleaks detect --source . -v --no-banner`. Si absent, ne pas bloquer —
   dire simplement que GitHub le vérifiera à la prochaine étape.
En cas d'échec : traduire l'erreur en langage clair (quel fichier, quel test,
quelle règle), s'arrêter et proposer de l'aide pour corriger. Ne jamais
committer/pousser du code dont les contrôles locaux échouent.

#### Quality gate SonarQube — approximation locale sans jeton

Le `SONAR_TOKEN` utilisé par la CI est un secret GitHub : il ne peut jamais
être relu (ni par ce skill, ni par personne — c'est volontaire, pour la
sécurité). Lancer le vrai scanner SonarQube en local est donc impossible
sans qu'un humain crée et transmette un jeton personnel.

À la place, relancer les tests unitaires du composant modifié avec un seuil
de couverture de 80 %, passé en option de ligne de commande (pas de
modification des fichiers du projet) :
```
npm run test:cov -- --coverage.reporter=lcov \
  --coverage.thresholds.lines=80 --coverage.thresholds.branches=80 \
  --coverage.thresholds.functions=80 --coverage.thresholds.statements=80
```
La commande échoue d'elle-même si la couverture descend sous 80 %, sans
contacter aucun serveur. C'est une approximation (couverture globale du
composant, alors que Sonar regarde la couverture du *nouveau* code) mais
elle attrape la plupart des régressions avant de pousser.

- En cas d'échec, ce seuil est bloquant : le message de vitest indique le
  fichier et les lignes non couvertes. Identifier ce que fait ce code et
  ajouter directement le(s) test(s) manquant(s) qui l'exercent (pas
  seulement le proposer), puis relancer la commande jusqu'à passer le
  seuil, avant de continuer. Dire simplement au BA ce qui a été ajouté et
  pourquoi ("j'ai ajouté un test pour telle ligne, elle n'était pas encore
  vérifiée"). Ne pousser qu'une fois ce seuil franchi.
- Si la ligne non couverte ne peut raisonnablement pas être testée (ex. code
  de démarrage/câblage) ou si son comportement attendu n'est pas évident :
  s'arrêter et demander au BA plutôt que de deviner un test qui ne
  vérifierait rien d'utile.
- Même quand ce seuil passe, le vrai quality gate GitHub (étape 5) reste la
  référence finale : le dire au BA une fois ("ce contrôle local approche
  d'assez près le vrai contrôle SonarQube, mais le contrôle définitif reste
  celui de GitHub, que je surveillerai après l'envoi").

### 3. Commit et push

- Si rien n'est committé : demander au BA une courte description de ce qu'il
  a fait (sert de message de commit ; à défaut, utiliser le nom de la
  fonctionnalité donné au skill `nouvelle-fonctionnalite`).
- `git add -A`
- `git commit -m "<message>"`
- `git push -u origin <branche-courante>`
  - Si le push échoue parce que la branche distante a avancé (quelqu'un/
    quelque chose a poussé depuis) : `git fetch origin` puis tenter
    `git rebase origin/<branche-courante>`.
    - Rebase propre → `git push --force-with-lease`.
    - Conflit de rebase : **ne jamais** demander au BA de résoudre des
      marqueurs `<<<<<<<` à la main. `git rebase --abort`, puis expliquer
      simplement quel(s) fichier(s) posent un conflit et proposer un choix
      binaire par fichier ("garder votre version" / "garder celle qui est
      déjà sur la branche"), appliquer ce choix via
      `git checkout --ours|--theirs -- <fichier>` puis `git rebase --continue`.
      Si le BA ne sait pas trancher, s'arrêter et demander de l'aide plutôt
      que de deviner.

### 4. S'assurer qu'une PR existe

- `gh pr view <branche-courante>` ; si aucune PR : la créer
  (`gh pr create --base main --head <branche-courante> --title ... --body ...`).
- Si une PR existe en brouillon : `gh pr ready <branche-courante>`.

### 5. Attendre que tout soit vert sur GitHub

- `gh pr checks <branche-courante> --watch`
- Un contrôle rouge (lint, gitleaks, quality gate Sonar, tests) : récupérer le
  détail (`gh run view --log-failed`), traduire en langage simple ce qui a
  échoué, s'arrêter. Ne pas relancer en boucle à l'aveugle.

### 6. Merge — confirmation obligatoire

Une fois tout vert et aucun conflit en attente :

- Résumer au BA en une phrase ce qui va se passer : "Tout est vérifié ✅. Si
  vous confirmez, votre travail va être intégré à la version principale, ce
  qui déclenchera automatiquement sa mise en ligne sur dev. Votre branche
  `<nom>` ne sera pas supprimée. Je confirme ?"
- **Attendre une confirmation explicite** avant de merger. Ne jamais merger
  automatiquement.
- Sur confirmation : `gh pr merge <branche-courante> --merge` (merge
  classique, pas de squash, **sans** `--delete-branch` : convention déjà
  utilisée dans l'historique de ce dépôt — commits "Merge pull request #N" —
  et la branche locale/distante reste en place après le merge). Ce merge se
  fait côté GitHub (distant) uniquement.
- Séparation des responsabilités : ce skill ne touche pas à la branche
  locale après ce merge (pas de `git switch main`, pas de `git pull`) — le
  BA reste sur sa branche de fonctionnalité. C'est le skill
  `nouvelle-fonctionnalite` qui synchronisera `main` en local, la prochaine
  fois qu'il sera lancé.

### 7. Attendre que la pipeline GitHub Actions sur main soit au vert

- `gh run list --branch main --limit 1` pour repérer le run `ci.yaml`
  déclenché par le merge, puis `gh run watch <id>`.
- Si ce run échoue : récupérer le détail (`gh run view <id> --log-failed`),
  traduire en langage simple ce qui a échoué, s'arrêter — ne pas aller plus
  loin et ne pas relancer en boucle à l'aveugle.
- Dès que ce run est vert, le rôle du skill s'arrête ici : il ne surveille
  pas la promotion Kargo vers dev ni la synchronisation ArgoCD (c'est
  automatique côté plateforme, et peut prendre quelques minutes).

### 8. Donner les deux liens utiles au BA

**Ce skill est copié tel quel dans plusieurs dépôts métier**
(`formation-ba-assurance-iard`, `formation-ba-compte-unique`,
`formation-ba-gestion-financiere`, `formation-ba-quali-formation`, ...) :
ne jamais écrire le nom d'un dépôt en dur dans ces liens. Détecter le nom
du dépôt courant (`gh repo view --json name -q .name`, ou à défaut
`basename "$(git rev-parse --show-toplevel)"`) et l'utiliser pour les
deux liens. Le domaine de la plateforme et l'hôte Kargo, eux, sont
partagés par tous ces dépôts (une seule plateforme, un seul Kargo) — ce
sont les seules parties à garder en dur :

- Application frontend sur dev :
  `https://frontend.<nom-du-dépôt>.dev.check-consulting.net`. Le BA peut
  l'ouvrir pour constater lui-même quand sa mise en ligne est visible (le
  temps que Kargo promeuve et qu'ArgoCD synchronise).
- Stage `qualif` dans l'interface Kargo, pour que le BA puisse cliquer sur
  "Promote" quand il le souhaite (la promotion vers qualif n'est pas
  automatique, cf. `helm.yaml` : "qualif/preprod/prod : promotion par
  clic") :
  `https://kargo.dev.check-consulting.net/project/<nom-du-dépôt>/stage/qualif`.
- Conclure en une phrase simple : ce qui a été mis en ligne (résumé du
  changement), et les deux liens ci-dessus.

## Ce que ce skill ne fait jamais

- Ne merge jamais sans confirmation explicite à l'étape 6.
- Ne supprime jamais la branche de la fonctionnalité, ni en local ni sur
  GitHub, après le merge.
- Ne synchronise jamais `main` en local après le merge — ça reste le rôle du
  skill `nouvelle-fonctionnalite`, la prochaine fois qu'il sera lancé.
- Ne résout jamais un conflit en modifiant le contenu du code à la place du
  BA — seulement des choix "garder ma version / garder l'autre" par fichier,
  validés par le BA.
- Ne force jamais un push sans qu'un rebase propre ait précédé.
- Ne relance jamais une CI en échec en boucle sans comprendre la cause.
- Ne surveille pas activement la promotion Kargo vers dev ni la
  synchronisation ArgoCD après le merge : il s'arrête une fois la pipeline
  GitHub Actions au vert et donne les liens au BA pour qu'il vérifie
  lui-même.
