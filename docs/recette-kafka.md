# Recette TECH-14 (CU-382) : tests que la recette ne peut pas faire seule

Sur dev, la recette ne peut ni modifier le `helm.yaml`, ni redémarrer l'application, ni couper le cluster Kafka
partagé. Ce guide explique comment vérifier ces trois critères quand même : sur un poste, avec un Kafka local, et
par les tests automatisés.

## CU-386 – Variable de topic absente : arrêt avec un message qui nomme la variable

**Test automatisé** : `ConfigurationKafkaTest` (critère 2). Il retire, une par une, chaque variable `KAFKA_*` et
vérifie que le démarrage s'arrête avec le message
`Configuration Kafka incomplète : la variable <NOM> est absente. Déclarez-la dans helm.yaml (applications[].envList).`
Il tourne à chaque PR (contrôle « unit-test (backend) »).

**À la main, sur un poste** (Docker démarré, dans le dossier `backend/`) :

1. Démarrer la base et Kafka : `docker compose up -d`.
2. Démarrer le backend **sans** `KAFKA_TOPIC_ORDRES` :
   ```bash
   KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
   KAFKA_TOPIC_ORDRES_EXECUTES=cu-epargne.local.ordres-executes \
   KAFKA_TOPIC_MOUVEMENTS=cu-epargne.local.mouvements \
   KAFKA_TOPIC_CATALOGUE=cu-epargne.local.catalogue \
   KAFKA_TOPIC_PROFIL_CLIENT=cu-epargne.local.profil-client \
   KAFKA_TOPIC_RAPPROCHEMENT=cu-epargne.local.rapprochement \
   KAFKA_TOPIC_SUFFIXE_ERREURS=.dlt \
   KAFKA_GROUP_ID_PREFIXE=cu-epargne-local \
   ./mvnw spring-boot:run
   ```
3. **Attendu** : l'application ne démarre pas, et le log contient
   `la variable KAFKA_TOPIC_ORDRES est absente`.
4. Relancer en ajoutant `KAFKA_TOPIC_ORDRES=cu-epargne.local.ordres` : l'application démarre.

**Sur dev**, c'est la même règle. Une variable retirée du `helm.yaml` empêcherait le nouveau pod de démarrer : ArgoCD
garde alors l'ancienne version en ligne et le message apparaît dans les logs du pod. Ce test n'est pas à faire sur
dev : il casserait l'environnement des autres.

## CU-388 – Kafka indisponible : publication différée sans perte

**Tests automatisés** :
- `EvenementsTest` (critère 4) : Kafka simulé indisponible, l'événement reste en base, puis il est publié dès que
  Kafka répond, sans perte, et les suivants attendent pour garder l'ordre.
- `OrdresKafkaTest` (critère 8 de CU-376) : la même chose avec un ordre. L'ordre est enregistré et confirmé, puis son
  événement est publié au retour de Kafka.

**À la main, sur un poste** (backend lancé comme ci-dessus, avec toutes les variables, et le frontend avec `npm start`) :

1. Ouvrir `http://localhost:4200/test/ordres`, enregistrer une position de test de 3 000 € et passer un rachat de
   100 € : l'événement « ordre demandé » est « Publié ».
2. Couper Kafka : `docker compose stop kafka`.
3. Passer un second rachat de 100 € : l'ordre est **enregistré** (statut « Demandé »). L'événement est « En attente —
   Kafka indisponible ».
4. Remettre Kafka : `docker compose start kafka`. Au passage suivant du relais (environ 2 s), l'événement passe à
   « Publié ». Rien n'est perdu, et les deux événements sont dans le topic, dans l'ordre.

## CU-394 – Tests automatisés sur un Kafka de test, sans le cluster partagé

- `KafkaIT` (TECH-14) et `OrdresIT` (TECH-08) démarrent leur **propre Kafka jetable** (image `apache/kafka:4.1.0`) et
  leur propre PostgreSQL avec Testcontainers, puis les suppriment à la fin.
- Ils ne connaissent pas l'adresse du cluster partagé : l'adresse du Kafka de test est injectée par
  `@ServiceConnection`. Les noms de topics des tests sont `test.*` (`src/test/resources/config/application.yaml`).
- Ils tournent à chaque PR dans le contrôle « integration-test (backend) » de GitHub. Le détail (nombre de tests,
  durée, conteneurs démarrés) est dans le journal de ce contrôle, sur la page de la PR (onglet « Checks »).
- Le test `HelmTopicsTest` vérifie en plus les noms déclarés dans `helm.yaml` pour les 4 environnements :
  `cu-epargne.<environnement>.<topic>`, groupes `cu-epargne-<environnement>`, topic d'erreurs avec `.dlt`.
