# Skills BA

Skills Claude Code écrits pour des Business Analysts qui développent des
prototypes sans connaissance du développement ni de git. Ils s'exécutent dans
le dépôt métier du BA (`formation-ba-<projet>`) : Claude Code les charge
automatiquement depuis `.claude/skills/` quand le BA ouvre le dépôt. Les
conventions du dépôt (architecture, gateway, `helm.yaml`, logging, tests)
sont dans `CLAUDE.md`, à la racine.

## Skills

| Skill | Le BA dit... | Rôle |
|---|---|---|
| [nouvelle-fonctionnalite](nouvelle-fonctionnalite/SKILL.md) | « je commence une fonctionnalité », « ajoute un champ », « affiche les contrats dans l'écran » | range le travail en cours, synchronise `main`, crée `feature/<nom>` en local ; puis accompagne le développement : modèle de données si une partie métier change ou s'ajoute (nouvelle migration Flyway, Drizzle, Django, vérifiée contre une vraie base ; jamais de migration existante modifiée), routes et écrans, **liaison écran ↔ backend toujours demandée au BA** (`apiBase(<backend>)`, relais local `/api/<backend>`, CORS, tests) |
| [nouveau-composant](nouveau-composant/SKILL.md) | « crée un backend Contrats », « ajoute un écran Espace client » | backend (Spring Boot, Express, Django) ou frontend (Angular, React) généré depuis les modèles du skill, jamais par copie : plomberie CI, tests, logs métier, base, relais locaux et `apiBase` ; questions sous forme de choix ; vérifié dans Docker ; ajouté **à la fin** de `helm.yaml` (tags en bloc) et à `gateway.yaml` (route ou CORS) |
| [lancer-en-local](lancer-en-local/SKILL.md) | « lance l'application » | bases, backends sur leurs ports, frontend avec son relais ; arrêt propre |
| [deploy](deploy/SKILL.md) | « déploie » | liste des fichiers validée, commit, **intégration de `main`** (conflits `helm.yaml`/`gateway.yaml` assemblés, jamais écrasés), lint/tests/couverture 80 % par stack, push, PR, CI verte, merge confirmé, liens |
| [verifier-mon-deploiement](verifier-mon-deploiement/SKILL.md) | « est-ce que ça a marché ? » | CI → tag Kargo → réponse de l'application, en dev ou qualif ; liens Kargo, ArgoCD, Grafana |

Parcours type : `nouvelle-fonctionnalite` (données, backend, écrans, liaison ;
`nouveau-composant`, `logs-metier`, `lancer-en-local` au besoin) →
`deploy` → `verifier-mon-deploiement` → promotion qualif dans Kargo.

## Règles git communes

- Une fonctionnalité = une branche `feature/<nom>`, partie d'un `main` à jour.
  Après la mise en ligne, on ne continue pas sur la même branche.
- `main` avance tout seul : Kargo y écrit les tags déployés (`helm.yaml`)
  plusieurs fois par jour. `deploy` intègre `main` dans la branche (merge)
  avant les contrôles et l'envoi.
- Jamais de rebase, de push forcé, de `reset --hard` sans sauvegarde, ni de
  suppression de branche.
- `helm.yaml` : nouvelles applications à la fin de `applications:`, `tag` en
  bloc (une valeur par ligne), tags jamais modifiés à la main.
- Commits au nom du BA seul (pas de co-auteur Claude).

## Distribution

Ce dossier est identique dans les 5 dépôts `formation-ba-*` (assurance-iard,
compte-unique, gestion-financiere, quali-formation, scpi-invest) ; le BA le
reçoit avec le dépôt (`git clone` / `git pull`). Une modification se fait dans
les 5 dépôts à la fois. Vérifier qu'aucune copie n'a divergé, depuis le dossier
qui contient les dépôts :

```bash
for r in formation-ba-*/; do [ -d "$r.claude/skills" ] && (cd "$r.claude/skills" && find . -type f -exec md5sum {} + | sort -k2 | md5sum | sed "s|-|$r|"); done
```

Une seule empreinte doit apparaître. Prévu ensuite : publier ces skills comme
plugin Claude Code depuis `formation-ba-gitops`, quand les BA y auront accès
en lecture.

## Hypothèses par dépôt

- Un `Makefile` par composant avec `lint`, `test-unit`, `test-integration`
  (contrat `ci-template`).
- `gh` authentifié sur le poste du BA (`gh auth status`), Docker installé.
- Application ArgoCD `<env>-<dépôt>`, Project Kargo `<dépôt>`
  (`chart-base/kargo-pipelines/README.md` de `formation-ba-gitops`).
- Chart applicatif `charts-ba-formations` ≥ 0.9.2 : un composant sans tag
  pour un environnement y est ignoré (indispensable à `nouveau-composant`).
