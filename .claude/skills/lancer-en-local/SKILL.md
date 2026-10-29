---
name: lancer-en-local
description: Lance toute l'application du projet sur le poste du Business Analyst (BA) — une base PostgreSQL par backend (docker compose), chaque backend sur son port local (Node, Java ou Python), puis le frontend dont le serveur de dev relaie /api/<backend> vers ces backends, comme le gateway le fait sur le cluster — vérifie que tout répond et donne l'adresse à ouvrir ; sait aussi tout arrêter. À utiliser quand le BA veut essayer, tester, voir ou lancer l'application en local / sur son poste, ou l'arrêter.
---

# Lancer l'application en local

Public : un Business Analyst, pas un développeur. Lancer les commandes
soi-même, ne pas demander au BA de taper des commandes.

En local, il n'y a pas de gateway : c'est le serveur de dev du frontend qui
relaie `/api/<backend>/...` vers le backend lancé sur le poste, en retirant
le préfixe (relais `server.proxy` de `vite.config.js` ou `proxy.conf.json`).
Le CORS du gateway n'est donc pas testé en local.

## Étapes

### 1. Prérequis

- Docker démarré (`docker info`). Sinon : demander au BA de lancer Docker
  Desktop.
- Outils de la stack : `node`/`npm` pour Node et les frontends, Java 21 pour
  les backends Maven (`./mvnw` télécharge Maven), `python` 3.13 pour Django.
  Un outil manque : le dire simplement et s'arrêter.

### 2. Inventaire

Depuis `helm.yaml` : les applications `type: backend` (dossier = `name`) et
le frontend (`type: frontend`). Pour chaque backend, son port local et celui
de sa base : README du composant, sinon les valeurs par défaut du modèle
(Node 3000, Java 8080, Django 8000 ; PostgreSQL 5432 dans `compose.yaml`).
Vérifier que le relais du frontend contient une entrée par backend appelé
(sinon : skill `appeler-un-backend`).

Vérifier que ces ports sont libres. Un port pris par un programme lancé
précédemment par ce skill : le réutiliser ou l'arrêter. Pris par autre chose
(souvent un PostgreSQL déjà installé sur 5432) : le dire au BA et proposer
d'arrêter ce programme ; ne jamais arrêter un programme du BA sans accord.

### 3. Bases de données

Pour chaque backend : `docker compose up -d` dans son dossier.

### 4. Backends

Pour chaque backend, installer puis lancer **en arrière-plan** (garder la
trace des processus lancés pour l'étape 7) :

| Stack | Installation | Lancement (port local P, base sur le port B) |
|---|---|---|
| Node/NestJS | `npm install` | `PORT=P npm run start:dev` |
| Java/Spring Boot | — | `SERVER_PORT=P ./mvnw spring-boot:run` |
| Python/Django | `python -m venv .venv` puis `pip install -r requirements-dev.txt` (dans le venv) | `DATABASE_URL=postgresql://app:app@localhost:B/<base> python manage.py migrate` puis la même variable avec `python manage.py runserver P` |

La notation `VAR=valeur commande` est celle d'un terminal bash ; l'adapter
au terminal du poste (PowerShell : `$env:VAR="valeur"; commande`, venv
Python : `.venv\Scripts\python` sous Windows, `.venv/bin/python` ailleurs).

Les migrations s'appliquent au démarrage (Node, Java) ou par `migrate`
(Django). Attendre que chaque backend réponde : `curl -sf http://localhost:P/`
(route « Hello World »). En cas d'erreur, lire la sortie du processus et
l'expliquer simplement (base non démarrée, port pris, migration en échec...).

### 5. Frontend

`npm install` puis, en arrière-plan : `npm run dev` (React/Vite, adresse
http://localhost:5173) ou `npm start` (Angular, http://localhost:4200).
Vérifier le relais : `curl -sf http://localhost:<port du frontend>/api/<backend>/`
doit renvoyer la réponse du backend.

### 6. Donner l'adresse au BA

L'adresse du frontend à ouvrir dans le navigateur, la liste de ce qui tourne
(backend → port), et rappeler qu'une modification du code est prise en compte
automatiquement (rechargement) sauf en Java, qu'il faut relancer.

### 7. Arrêter (quand le BA le demande)

- Arrêter les processus lancés par ce skill (et seulement eux).
- `docker compose stop` dans chaque dossier backend : les données locales sont
  gardées pour la prochaine fois. `docker compose down -v` seulement si le BA
  veut repartir d'une base vide (prévenir : données locales effacées).

## Ce que ce skill ne fait jamais

- N'arrête jamais un programme qu'il n'a pas lancé sans l'accord du BA.
- Ne modifie jamais le code ni les ports écrits dans le code pour contourner
  un conflit de port : il passe les ports au lancement.
- N'efface jamais les données locales sans prévenir.
- Ne commit et ne push rien.
