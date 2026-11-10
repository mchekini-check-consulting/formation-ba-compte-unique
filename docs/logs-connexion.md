# Suivre la connexion d'une cliente (US-LOG-03, CU-212)

## Où regarder

Grafana, tableau « Logs métier », en qualification :
`https://grafana.dev.check-consulting.net/d/logs-business?var-env=qualif&var-project=formation-ba-compte-unique&var-app=backend`

Filtres : environnement `qualif`, projet `formation-ba-compte-unique`, application `backend`, couche `service`.
Pour retrouver toutes les lignes d'une demande, saisir son identifiant de demande (`requestId`) dans la recherche.

## Les traces (une ligne JSON, couche `service`)

Chaque ligne porte le champ `evenementConnexion`, qui sert aux indicateurs.

| # | `evenementConnexion` | Niveau | Message | Autres champs |
|---|---|---|---|---|
| L1 | `CONNEXION_REUSSIE` | info | connexion réussie | `identifiantConnexionId` |
| L2 | `CONNEXION_REFUSEE` | warn | connexion refusée | `motif` (`IDENTIFIANT_INCONNU`, `COMPTE_NON_ACTIVE`, `MOT_DE_PASSE_INCORRECT`), `identifiantConnexionId` ou `aucun` |
| L3 | `BLOCAGE_IDENTIFIANT` | warn | blocage de 300 secondes | `motif` = `IDENTIFIANT_BLOQUE`, `secondes` = 300, `identifiantConnexionId` ou `aucun` |
| L4 | `TENTATIVE_PENDANT_BLOCAGE` | warn | tentative pendant un blocage | `motif` = `IDENTIFIANT_BLOQUE`, `secondes` restantes, `identifiantConnexionId` ou `aucun` |
| L5 | `ADRESSE_IP_BLOQUEE` | warn | adresse IP bloquée | `motif` = `ADRESSE_IP_BLOQUEE`, `secondes` restantes (pas d'identifiant) |
| L6 | `SESSION_INACTIVE` | info | inactivité 10 minutes | `identifiantConnexionId` |
| L7 | `DECONNEXION` | info | déconnexion volontaire | `identifiantConnexionId` |

Jamais dans une trace : identifiant saisi, mot de passe, jeton, code, adresse IP.

Exemple de ligne L2 :

```json
{"time":"2026-11-10T15:25:00Z","level":"WARN","msg":"connexion refusée","layer":"service","method":"ConnexionService.connecter","evenementConnexion":"CONNEXION_REFUSEE","motif":"MOT_DE_PASSE_INCORRECT","identifiantConnexionId":"14","requestId":"f0382a89"}
```

## Indicateurs I1 à I6 (panneaux Grafana, source Loki)

Requêtes à coller dans un panneau « Time series » (ou « Stat » pour I6), rafraîchissement 1 minute.
`$env`, `$project` et `$app` sont les variables du tableau « Logs métier » ; si les étiquettes Loki portent un autre
nom dans le tableau, reprendre celles des panneaux existants.

```
# I1 - connexions réussies par minute
sum(count_over_time({env="$env", project="$project", app="$app"} | json | evenementConnexion="CONNEXION_REUSSIE" [1m]))

# I2 - échecs par minute et par motif
sum by (motif) (count_over_time({env="$env", project="$project", app="$app"} | json | evenementConnexion="CONNEXION_REFUSEE" [1m]))

# I3 - blocages d'identifiant par heure
sum(count_over_time({env="$env", project="$project", app="$app"} | json | evenementConnexion="BLOCAGE_IDENTIFIANT" [1h]))

# I4 - tentatives pendant un blocage par minute
sum(count_over_time({env="$env", project="$project", app="$app"} | json | evenementConnexion="TENTATIVE_PENDANT_BLOCAGE" [1m]))

# I5 - sessions fermées par heure et par raison (inactivité / déconnexion)
sum by (evenementConnexion) (count_over_time({env="$env", project="$project", app="$app"} | json | evenementConnexion=~"SESSION_INACTIVE|DECONNEXION" [1h]))

# I6 - taux d'échec sur 10 minutes = échecs / (réussites + échecs)
sum(count_over_time({env="$env", project="$project", app="$app"} | json | evenementConnexion="CONNEXION_REFUSEE" [10m]))
/
sum(count_over_time({env="$env", project="$project", app="$app"} | json | evenementConnexion=~"CONNEXION_REUSSIE|CONNEXION_REFUSEE" [10m]))
```
