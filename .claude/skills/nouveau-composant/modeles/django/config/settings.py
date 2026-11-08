"""Configuration Django du backend __SLUG__ (projet __DEPOT__)."""

import os
from pathlib import Path

import dj_database_url

BASE_DIR = Path(__file__).resolve().parent.parent

# Secret de signature (sessions, jetons) : DJANGO_SECRET_KEY sur le cluster ;
# valeur de développement par défaut, jamais utilisée pour de vraies données.
SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY", "dev-uniquement-__SLUG__-pas-un-secret")

DEBUG = os.environ.get("DJANGO_DEBUG", "false").lower() == "true"

# Noms de domaine acceptés, séparés par des virgules (fourni par helm.yaml en cluster :
# hôte du gateway du projet, qui relaie les appels du navigateur)
ALLOWED_HOSTS = os.environ.get("ALLOWED_HOSTS", "localhost,127.0.0.1").split(",")

INSTALLED_APPS = [
    "django.contrib.contenttypes",
    "django.contrib.auth",
    "rest_framework",
    "__MODULE__",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.common.CommonMiddleware",
]

ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"

# API : pas encore d'authentification (à ajouter avant l'ouverture aux utilisateurs)
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [],
    "UNAUTHENTICATED_USER": None,
}

# Base PostgreSQL : DATABASE_URL, injectée par la plateforme (helm.yaml : database).
# Sans elle (poste de dev) : la base de compose.yaml.
DATABASES = {
    "default": dj_database_url.config(
        default="postgresql://app:app@localhost:__PORT_DB_LOCAL__/__BASE__",
        conn_max_age=60,
        conn_health_checks=True,
    )
}
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

LANGUAGE_CODE = "fr-fr"
TIME_ZONE = "Europe/Paris"
USE_I18N = True
USE_TZ = True

# Logs : une ligne JSON par log sur stdout (__MODULE__/logging). Seules les lignes avec
# un champ "layer" (logs métier : @log_metier) sont collectées dans Loki ; le reste
# (démarrage, Django) reste visible avec kubectl logs. Niveau minimal : LOG_LEVEL
# (debug|info|warn|error), injecté par helm.yaml (envList).
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {"json": {"()": "__MODULE__.logging.formatter.JsonFormatter"}},
    "handlers": {"console": {"class": "logging.StreamHandler", "formatter": "json"}},
    "root": {"handlers": ["console"], "level": os.environ.get("LOG_LEVEL", "info").upper()},
}
