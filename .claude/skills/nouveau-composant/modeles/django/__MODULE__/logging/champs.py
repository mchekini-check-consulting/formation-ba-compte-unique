"""Champs métier d'un log (skill logs-metier) : chemins <racine>.<propriété>...
approuvés par le BA ("taille" = nombre d'éléments d'une liste). Contrôlés dès la
déclaration (verifier : erreur à l'import, les tests échouent) ; lus sans jamais faire
échouer l'appel (extraire : un chemin illisible est ignoré)."""

import logging
import re
from datetime import date, datetime
from decimal import Decimal
from enum import Enum
from uuid import UUID

RESULTAT = "resultat"
_TAILLE = "taille"

# Écrits par la plateforme (et attributs d'un LogRecord Python) : un champ approuvé ne
# peut pas porter ces noms
RESERVES = {"time", "level", "msg", "logger", "layer", "method", "route", "status", "durationMs", "err"} | set(
    logging.makeLogRecord({}).__dict__
) | {"message", "asctime"}

# Mots (découpage snake_case et camelCase) qui signalent une donnée personnelle ou un
# secret : refusés même si le BA les approuve. "code" couvre code postal, de vérification...
_INTERDITS = [
    "nom", "prenom", "email", "mail", "telephone", "portable", "adresse", "code", "naissance",
    "iban", "bic", "mot de passe", "password", "jeton", "token", "authorization", "secret",
    "signature", "cookie", "ip", "secu",
]


def personnel(segment: str) -> bool:
    mots = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", segment).replace("_", " ").lower()
    return any(f" {interdit} " in f" {mots} " for interdit in _INTERDITS)


def erreurs(champs, racines) -> list[str]:
    problemes = []
    for champ in champs:
        segments = champ.split(".")
        interdit = next((s for s in segments if personnel(s)), None)
        if champ in RESERVES:
            problemes.append(f"{champ} : nom réservé à la plateforme")
        elif interdit:
            problemes.append(f"{champ} : donnée personnelle ou secret ({interdit}), interdit dans les logs")
        elif segments[0] not in racines:
            problemes.append(f"{champ} : racine inconnue ({segments[0]}), attendu : {', '.join(racines)}")
    return problemes


def verifier(contexte: str, champs, racines) -> None:
    problemes = erreurs(champs, racines)
    if problemes:
        raise ValueError(f"Logs métier de {contexte} :\n" + "\n".join(problemes))


def _simple(valeur) -> bool:
    return isinstance(valeur, str | int | float | bool | Decimal | UUID | date | datetime | Enum)


def _texte(valeur) -> str:
    if isinstance(valeur, Enum):
        return str(valeur.value)
    if isinstance(valeur, date | datetime):
        return valeur.isoformat()
    return str(valeur).lower() if isinstance(valeur, bool) else str(valeur)


def _lire(racine, proprietes):
    courant = racine
    for propriete in proprietes:
        if courant is None:
            return None
        if propriete == _TAILLE and hasattr(courant, "__len__") and not isinstance(courant, str | dict):
            courant = len(courant)
        elif isinstance(courant, dict):
            courant = courant.get(propriete)
        else:
            courant = getattr(courant, propriete, None)
    if _simple(courant):
        return _texte(courant)
    if isinstance(courant, list | tuple) and all(_simple(v) for v in courant):
        return ",".join(_texte(v) for v in courant)
    return None  # absent ou valeur non simple


def extraire(champs, racines: dict) -> dict[str, str]:
    valeurs = {}
    for champ in champs:
        racine, *proprietes = champ.split(".")
        if racine not in racines:
            continue
        try:
            valeur = _lire(racines[racine], proprietes)
        # Propriété en erreur : champ ignoré, l'appel métier continue
        except Exception:  # noqa: BLE001
            valeur = None
        if valeur is not None:
            valeurs[champ] = valeur
    return valeurs
