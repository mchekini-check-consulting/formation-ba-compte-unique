from django.apps import AppConfig


class Config(AppConfig):
    name = "__MODULE__"
    default_auto_field = "django.db.models.BigAutoField"
