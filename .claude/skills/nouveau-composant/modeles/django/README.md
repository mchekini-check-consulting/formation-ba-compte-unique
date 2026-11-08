# __SLUG__

__TITRE__ : backend Django (Python 3.13) du projet __DEPOT__, base PostgreSQL `__BASE__`.

Via le gateway : `https://gateway.__DEPOT__.<env>.check-consulting.net/__SLUG__/...`
(préfixe `/__SLUG__` retiré par le gateway).

## Structure

```
config/                # réglages Django, routes racine
__MODULE__/
  views.py, urls.py    # routes / et /health
  models.py            # tables ; python manage.py makemigrations __MODULE__
  logging/             # logs métier (@log_metier, skill logs-metier) : ne pas modifier
  tests/               # tests (test_integration.py : tag "integration")
```

## En local

```bash
docker compose up -d                     # base locale, port __PORT_DB_LOCAL__
python -m venv .venv && . .venv/bin/activate   # Windows : .venv\Scripts\activate
pip install -r requirements-dev.txt
python manage.py migrate
python manage.py runserver __PORT_LOCAL__   # http://localhost:__PORT_LOCAL__/ (santé : /health)
```

## Contrôles (comme la CI)

`make lint` (ruff), `make test-unit` (tests + coverage.xml), `make test-integration`
(PostgreSQL jetable, Docker requis).
