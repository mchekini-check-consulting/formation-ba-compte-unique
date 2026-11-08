"""Logs métier activés méthode par méthode (skill logs-metier) : rien n'est journalisé
sans @log_metier. Champs obligatoires de la plateforme :
  controller : layer, method (GET...), route (modèle, jamais ses valeurs), status, durationMs
  service/db : layer, method (Classe.methode), durationMs, err (type d'erreur en échec)
puis les champs métier approuvés par le BA (champs.py), dont les racines sont les noms
des paramètres de la fonction et "resultat" (pour une vue : corps de la réponse DRF).

    @log_metier("service", champs=["demande.formule", "resultat.reference"])
    def creer(self, demande): ...
"""

import functools
import inspect
import logging
import time

from django.core.exceptions import PermissionDenied
from django.http import Http404, HttpRequest

from __MODULE__.logging.champs import RESULTAT, extraire, verifier

logger = logging.getLogger("__MODULE__.metier")

# Au-delà, un appel db est écrit en warn (visible même avec LOG_LEVEL=warn)
SEUIL_DB_LENT_MS = 200
_COUCHES = {"controller", "service", "db"}


def _statut_erreur(exc: Exception) -> int:
    """Code HTTP que Django ou DRF renverra pour cette exception levée par une vue."""
    if isinstance(exc, Http404):
        return 404
    if isinstance(exc, PermissionDenied):
        return 403
    return getattr(exc, "status_code", 500)


def log_metier(couche: str, champs=()):
    if couche not in _COUCHES:
        raise ValueError(f"couche inconnue : {couche} (controller, service ou db)")

    def decorer(fonction):
        signature = inspect.signature(fonction)
        verifier(fonction.__qualname__, champs, [*signature.parameters, RESULTAT])

        @functools.wraps(fonction)
        def enveloppe(*args, **kwargs):
            debut = time.monotonic()
            racines = dict(signature.bind_partial(*args, **kwargs).arguments)
            try:
                resultat = fonction(*args, **kwargs)
            except Exception as exc:
                _ecrire(couche, fonction, args, debut, racines, exc=exc)
                raise
            _ecrire(couche, fonction, args, debut, {**racines, RESULTAT: resultat})
            return resultat

        def _ecrire(couche, fonction, args, debut, racines, exc=None):
            duree = round((time.monotonic() - debut) * 1000)
            if couche == "controller":
                requete = next((a for a in args if isinstance(a, HttpRequest)), None)
                reponse = racines.get(RESULTAT)
                statut = _statut_erreur(exc) if exc else getattr(reponse, "status_code", 200)
                if reponse is not None:
                    racines[RESULTAT] = getattr(reponse, "data", None)
                match = getattr(requete, "resolver_match", None)
                ligne = {
                    "layer": "controller",
                    "method": getattr(requete, "method", None),
                    "route": match.route if match else "unmatched",
                    "status": statut,
                    "durationMs": duree,
                    **extraire(champs, racines),
                }
                if statut >= 500:
                    logger.warning("http request failed", extra=ligne)
                else:
                    logger.info("http request handled", extra=ligne)
                return
            ligne = {
                "layer": couche,
                "method": fonction.__qualname__,
                "durationMs": duree,
                **extraire(champs, racines),
            }
            if exc is not None:
                logger.warning(f"{couche} call failed", extra={**ligne, "err": type(exc).__name__})
            elif couche == "db" and duree >= SEUIL_DB_LENT_MS:
                logger.warning("slow db call", extra=ligne)
            else:
                logger.info(f"{couche} call completed", extra=ligne)

        return enveloppe

    return decorer
