from django.db import DatabaseError, connection
from django.http import HttpResponse, JsonResponse


def hello(request):
    """Point de départ : GET / (via le gateway : /__SLUG__/)."""
    return HttpResponse("Hello, World!", content_type="text/plain")


def health(request):
    """Santé : 200 si la base répond, 503 sinon."""
    try:
        connection.ensure_connection()
    except DatabaseError:
        return JsonResponse({"status": "ko"}, status=503)
    return JsonResponse({"status": "ok"})
