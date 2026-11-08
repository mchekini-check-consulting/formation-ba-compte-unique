from unittest import mock

from django.db import OperationalError
from django.test import SimpleTestCase


class VuesTests(SimpleTestCase):
    def test_hello(self):
        reponse = self.client.get("/")
        self.assertEqual(reponse.status_code, 200)
        self.assertEqual(reponse.content, b"Hello, World!")

    @mock.patch("__MODULE__.views.connection")
    def test_sante_ok_quand_la_base_repond(self, connexion):
        reponse = self.client.get("/health")
        self.assertEqual(reponse.status_code, 200)
        self.assertEqual(reponse.json(), {"status": "ok"})
        connexion.ensure_connection.assert_called_once()

    @mock.patch("__MODULE__.views.connection")
    def test_sante_503_quand_la_base_ne_repond_pas(self, connexion):
        connexion.ensure_connection.side_effect = OperationalError("connexion refusée")
        reponse = self.client.get("/health")
        self.assertEqual(reponse.status_code, 503)
        self.assertEqual(reponse.json(), {"status": "ko"})
