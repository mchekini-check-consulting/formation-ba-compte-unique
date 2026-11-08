from django.db.migrations.recorder import MigrationRecorder
from django.test import TestCase, tag


@tag("integration")
class IntegrationTests(TestCase):
    """Application complète sur un PostgreSQL 16 jetable (config/settings_integration.py).
    make test-integration ; Docker requis."""

    def test_sante_ok_avec_une_vraie_base(self):
        reponse = self.client.get("/health")
        self.assertEqual(reponse.status_code, 200)
        self.assertEqual(reponse.json(), {"status": "ok"})

    def test_migrations_appliquees(self):
        self.assertTrue(MigrationRecorder.Migration.objects.exists())
