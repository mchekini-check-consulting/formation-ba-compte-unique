"""Tests d'intégration (make test-integration) : PostgreSQL 16 jetable dans Docker."""

import atexit

import dj_database_url
from testcontainers.postgres import PostgresContainer

from config import settings as _base

# Mêmes réglages que config.settings, seule la base change
globals().update({nom: valeur for nom, valeur in vars(_base).items() if nom.isupper()})

_postgres = PostgresContainer("postgres:16", driver=None).start()
atexit.register(_postgres.stop)

DATABASES = {"default": dj_database_url.parse(_postgres.get_connection_url())}
