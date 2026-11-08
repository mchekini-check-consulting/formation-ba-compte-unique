# Configuration gunicorn (Dockerfile). Avant de démarrer les workers, applique les
# migrations Django sur la base (DATABASE_URL) : une migration en échec arrête le
# conteneur. Plusieurs réplicas : PostgreSQL sérialise les migrations concurrentes,
# elles doivent rester compatibles avec la version précédente du code.
import os


def on_starting(server):
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
    import django
    from django.core.management import call_command

    django.setup()
    call_command("migrate", interactive=False, verbosity=1)
