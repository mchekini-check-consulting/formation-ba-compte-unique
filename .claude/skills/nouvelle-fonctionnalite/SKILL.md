---
name: nouvelle-fonctionnalite
description: Démarre une nouvelle fonctionnalité pour un Business Analyst (BA) qui ne maîtrise pas git — met de côté proprement le travail en cours, synchronise main avec GitHub, demande le nom de la fonctionnalité et crée sa branche feature/<nom> en local (pas de push ni de PR, rôle du skill deploy). À utiliser quand le BA dit qu'il veut commencer / développer une nouvelle fonctionnalité, créer une branche, ou repartir sur un nouveau sujet après une mise en ligne.
---

# Nouvelle fonctionnalité

Public : un Business Analyst, pas un développeur. Ne jamais afficher de jargon
git brut (rebase, HEAD, merge conflict...) sans l'expliquer en une phrase
simple. Ne jamais faire d'action destructrice (reset --hard, suppression de
branche, écrasement de modifications) sans demander confirmation d'abord.

Règle de travail à rappeler au BA quand c'est utile : **une fonctionnalité =
une branche**, créée depuis une version à jour de `main`. Une fois la
fonctionnalité mise en ligne (skill `deploy`), on ne continue pas sur la même
branche : on relance ce skill pour le sujet suivant. `main` bouge souvent sans
que le BA y touche (Kargo y écrit les versions déployées dans `helm.yaml`,
plusieurs fois par jour, et les autres BA y fusionnent leur travail) : c'est
pour ça que chaque branche part d'un `main` fraîchement synchronisé.

## Étapes

1. **Vérifier qu'on est dans un dépôt git** (`git rev-parse --is-inside-work-tree`).
   Si non : expliquer au BA qu'il doit ouvrir le dossier de son projet.

2. **Vérifier l'état courant** (`git branch --show-current`, `git status --porcelain`).
   - **Modifications non enregistrées sur une branche `feature/...`** :
     s'arrêter et demander au BA ce qu'il veut en faire, en langage simple :
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
       ouvert et demander s'il veut vraiment en créer un nouveau ou continuer
       celui-ci.

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
   (ne pas parler de « nom de branche » ni de « PR »).

5. **Construire le nom de branche** : minuscules, accents retirés, espaces et
   caractères spéciaux remplacés par des tirets, préfixe `feature/`.
   Exemple : « Filtre par date » → `feature/filtre-par-date`.
   Vérifier qu'elle n'existe pas déjà, ni en local
   (`git rev-parse --verify --quiet feature/<slug>`) ni sur GitHub
   (`git ls-remote --heads origin feature/<slug>`). Si elle existe :
   demander au BA s'il veut reprendre ce travail (`git switch feature/<slug>`)
   ou choisir un autre nom.

6. **Créer la branche, en local uniquement** :
   ```
   git switch -c feature/<slug>
   ```
   Ne pas pousser ni créer de pull request ici : tant qu'il n'y a aucun
   commit, il n'y a rien à envoyer. C'est le skill `deploy` qui s'en charge.

7. **Conclure en langage simple** : l'espace de travail « <nom> » est prêt, il
   peut modifier le code. Rappeler les demandes utiles :
   - « lance l'application en local » (skill `lancer-en-local`) ;
   - « crée un nouveau backend » (skill `nouveau-backend`) ;
   - « ajoute une table / un champ » (skill `modifier-le-modele-de-donnees`) ;
   - « affiche ces données dans le frontend » (skill `appeler-un-backend`) ;
   - « déploie » quand il a terminé (skill `deploy`).

## Ce que ce skill ne fait jamais

- Ne pousse rien et n'ouvre jamais de PR : il reste 100 % local (seul `main`
  est lu depuis GitHub). Le commit « Travail en cours » de l'étape 2 reste
  local.
- Ne force jamais un push, et ne fait jamais de `reset --hard` sans
  sauvegarde préalable et confirmation explicite.
- Ne supprime jamais une branche existante ni des modifications sans double
  confirmation.
