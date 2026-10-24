# Skills BA (gabarit)

Ces deux skills sont écrits pour des Business Analysts qui ne manipulent pas
git directement. Ils ne s'exécutent pas depuis ce dépôt (`formation-ba-gitops`)
mais depuis le dépôt métier du BA (`formation-ba-<projet>`), là où vivent son
code, son `Makefile` (cibles `lint`, `test-unit`, `test-integration` — contrat
décrit dans `ci-template/README.md`) et sa pull request.

## Installation dans un dépôt formation-ba-\<projet\>

Copier tout le dossier dans le dépôt métier, lors du scaffolding du projet :

```bash
cp -r .claude/skills formation-ba-<projet>/.claude/
```

## Skills

| Skill | Rôle |
|---|---|
| [nouvelle-fonctionnalite](nouvelle-fonctionnalite/SKILL.md) | synchronise `main`, demande le nom de la fonctionnalité, crée la branche |
| [nouveau-backend](nouveau-backend/SKILL.md) | crée la branche, duplique `backend/` comme modèle vivant (squelette minimal, sans l'exemple « items »), sa propre base de données, et l'ajoute à `helm.yaml` |
| [deploy](deploy/SKILL.md) | contrôles locaux (lint/gitleaks/tests/quality gate 80% approximé), commit, push, attente CI verte, merge vers `main` (avec confirmation, branche conservée), puis liens dev/qualif |

## Hypothèses à vérifier par dépôt

- Le dépôt métier a bien un `Makefile` par composant avec `lint`, `test-unit`,
  `test-integration` (contrat `ci-template`).
- `gh` est authentifié sur le poste du BA (`gh auth status`).
- Le nom de l'Application ArgoCD dev suit le motif `dev-<repo>` et le Project
  Kargo correspond au dépôt (cf. `chart-base/kargo-pipelines/README.md`).
