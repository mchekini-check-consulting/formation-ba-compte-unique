---
name: nouvelle-fonctionnalite
description: Démarre une nouvelle fonctionnalité pour un Business Analyst (BA) qui ne maîtrise pas git — synchronise main avec le dépôt distant, demande le nom de la fonctionnalité et crée la branche en local (pas de push, pas de PR : c'est le rôle du skill deploy une fois le développement fait). À utiliser quand le BA dit qu'il veut commencer/développer une nouvelle fonctionnalité ou créer une branche.
---

# Nouvelle fonctionnalité

Public : un Business Analyst, pas un développeur. Ne jamais afficher de jargon
git brut (rebase, HEAD, merge conflict...) sans l'expliquer en une phrase
simple. Ne jamais faire d'action destructrice (reset --hard, suppression de
branche, écrasement de modifications) sans demander confirmation d'abord.

## Étapes

1. **Vérifier qu'on est dans un dépôt git** (`git rev-parse --is-inside-work-tree`).
   Si non : expliquer au BA qu'il doit ouvrir le dossier de son projet.

2. **Vérifier l'état courant** (`git status --porcelain`).
   - Si des modifications non enregistrées existent **et** qu'on n'est pas sur
     `main` : s'arrêter et demander au BA ce qu'il veut faire de ce travail en
     cours (le garder pour plus tard / l'abandonner). Ne jamais le supprimer
     sans réponse explicite.
   - Si on est déjà sur une branche `feature/...` propre (rien à committer) :
     informer le BA qu'il a déjà un espace de travail ouvert sur cette branche
     et demander s'il veut vraiment en créer un nouveau (à partir de main) ou
     continuer celui-ci.

3. **Synchroniser main avec le dépôt distant** :
   ```
   git fetch origin
   git switch main
   git pull --ff-only origin main
   ```
   - Si `switch` échoue à cause de modifications en cours : revenir à l'étape 2.
   - Si `pull --ff-only` échoue (main locale a divergé de origin/main, cas
     anormal) : s'arrêter, expliquer en termes simples ("votre branche
     principale locale contient des changements qui n'existent pas sur
     GitHub, ce qui est inattendu") et demander confirmation explicite avant
     de faire `git reset --hard origin/main`. Ne jamais le faire sans accord.
   - Vérifier ensuite `git rev-parse main` == `git rev-parse origin/main`.

4. **Demander le nom de la fonctionnalité** en langage simple, par exemple :
   "Quel est le nom de la fonctionnalité que vous allez développer ?"
   (éviter de parler de "nom de branche" ou "nom de PR" — c'est la même chose
   pour le BA).

5. **Construire le nom de branche** : minuscules, accents retirés, espaces et
   caractères spéciaux remplacés par des tirets, préfixe `feature/`.
   Exemple : "Filtre par date" → `feature/filtre-par-date`.
   Convention du dépôt : cf. branches existantes (`feature/...`).

6. **Créer la branche, en local uniquement** :
   ```
   git switch -c feature/<slug>
   ```
   Ne pas pousser (`git push`) ni créer de pull request ici — tant qu'il n'y a
   aucun commit, il n'y a rien à envoyer sur GitHub. Le push, le commit et la
   création de la PR sont gérés par le skill `deploy`, une fois que le BA a
   terminé une première version de son développement.

7. **Conclure en langage simple** : confirmer que son espace de travail
   "<nom>" est prêt, qu'il peut maintenant modifier le code, et que lorsqu'il
   aura terminé il lui suffira de demander à déployer (skill `deploy`), qui
   s'occupera alors d'envoyer le travail sur GitHub et d'ouvrir la PR.

## Ce que ce skill ne fait jamais

- Ne commit, ne push et n'ouvre jamais de PR — ce skill reste 100% local
  (seul `main` est synchronisé avec le distant en lecture). C'est `deploy`
  qui s'occupe de tout envoi vers GitHub.
- Ne force jamais un push ou un reset sans confirmation explicite.
- Ne supprime jamais une branche existante sans demander.
