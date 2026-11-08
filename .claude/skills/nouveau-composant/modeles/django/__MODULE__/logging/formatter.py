import json
import logging
from datetime import UTC, datetime

# Attributs par défaut d'un LogRecord : tout le reste vient de extra={...}
# (layer, durationMs...) et est recopié tel quel dans la ligne JSON.
_ATTRIBUTS_STANDARD = set(logging.makeLogRecord({}).__dict__) | {"message"}
_NIVEAUX = {"WARNING": "warn", "CRITICAL": "error"}


class JsonFormatter(logging.Formatter):
    """Une ligne JSON par log : time, level, msg, logger + champs extra. Pour une
    exception, seulement son type (son message peut contenir des données personnelles)."""

    def format(self, record: logging.LogRecord) -> str:
        ligne = {
            "time": datetime.fromtimestamp(record.created, tz=UTC)
            .isoformat(timespec="milliseconds")
            .replace("+00:00", "Z"),
            "level": _NIVEAUX.get(record.levelname, record.levelname.lower()),
            "msg": record.getMessage(),
            "logger": record.name,
        }
        for cle, valeur in record.__dict__.items():
            if cle not in _ATTRIBUTS_STANDARD:
                ligne[cle] = valeur
        if record.exc_info and record.exc_info[0]:
            ligne["err"] = record.exc_info[0].__name__
        return json.dumps(ligne, default=str, ensure_ascii=False)
