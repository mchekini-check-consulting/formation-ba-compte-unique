from django.urls import path

from __MODULE__.views import health, hello

urlpatterns = [
    path("", hello),
    path("health", health),
]
