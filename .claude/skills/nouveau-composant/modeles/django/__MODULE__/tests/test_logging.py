import json
import logging
from datetime import date
from unittest import mock

from django.core.exceptions import PermissionDenied
from django.http import Http404, HttpResponse
from django.test import RequestFactory, SimpleTestCase
from django.urls import ResolverMatch
from rest_framework.exceptions import NotFound
from rest_framework.response import Response

from __MODULE__.logging import log_metier as module
from __MODULE__.logging.champs import erreurs, extraire, personnel, verifier
from __MODULE__.logging.formatter import JsonFormatter
from __MODULE__.logging.log_metier import SEUIL_DB_LENT_MS, log_metier


class Logement:
    surface = 60
    adresse = "12 rue des Lilas"

    @property
    def statut(self):
        raise RuntimeError("chargement impossible")


class FormatterTests(SimpleTestCase):
    def ligne(self, **kwargs):
        record = logging.makeLogRecord({"name": "exemple", "levelname": "WARNING", "msg": "x", **kwargs})
        return json.loads(JsonFormatter().format(record))

    def test_time_level_msg_logger_et_champs_extra(self):
        ligne = self.ligne(layer="service", durationMs=5)
        self.assertEqual(ligne["level"], "warn")
        self.assertEqual(ligne["msg"], "x")
        self.assertEqual(ligne["logger"], "exemple")
        self.assertEqual(ligne["layer"], "service")
        self.assertEqual(ligne["durationMs"], 5)
        self.assertTrue(ligne["time"].endswith("Z"))

    def test_erreur_type_seulement(self):
        try:
            raise ValueError("marie.dupont@exemple.fr")
        except ValueError:
            import sys

            ligne = self.ligne(exc_info=sys.exc_info())
        self.assertEqual(ligne["err"], "ValueError")
        self.assertNotIn("marie.dupont", json.dumps(ligne))


class ChampsTests(SimpleTestCase):
    def test_extrait_valeurs_simples_listes_taille_dates(self):
        demande = {"formule": "confort", "options": ["a", "b"], "logement": Logement(), "le": date(2026, 11, 7)}
        valeurs = extraire(
            ["demande.formule", "demande.options", "demande.options.taille", "demande.logement.surface",
             "demande.le", "resultat.ok", "demande.logement", "demande.logement.statut", "absent.x",
             "demande.vide.x"],
            {"demande": demande, "resultat": {"ok": True}},
        )
        self.assertEqual(valeurs, {
            "demande.formule": "confort",
            "demande.options": "a,b",
            "demande.options.taille": "2",
            "demande.logement.surface": "60",
            "demande.le": "2026-11-07",
            "resultat.ok": "true",
        })

    def test_refuse_donnees_personnelles_noms_reserves_racines_inconnues(self):
        self.assertEqual(
            erreurs(["demande.logement.adresse", "status", "args", "inconnu.x", "demande.formule"], ["demande"]),
            [
                "demande.logement.adresse : donnée personnelle ou secret (adresse), interdit dans les logs",
                "status : nom réservé à la plateforme",
                "args : nom réservé à la plateforme",
                "inconnu.x : racine inconnue (inconnu), attendu : demande",
            ],
        )
        with self.assertRaisesRegex(ValueError, "donnée personnelle"):
            verifier("x", ["email"], ["email"])

    def test_donnees_personnelles(self):
        for segment in ["nom", "nom_client", "nomClient", "email", "code_postal", "date_naissance", "mot_de_passe", "ip"]:
            self.assertTrue(personnel(segment), segment)
        for segment in ["nombre_pieces", "formule", "reference", "description", "surface"]:
            self.assertFalse(personnel(segment), segment)


class Service:
    @log_metier("service", champs=["formule", "resultat.prix"])
    def calculer(self, formule, email):
        if formule == "inconnue":
            raise LookupError("formule inconnue")
        return {"formule": formule, "prix": 12}

    def sans_log(self):
        return {"formule": "confort"}


class Depot:
    @log_metier("db", champs=["resultat.taille"])
    def trouver(self, formule):
        return [formule]


class LogMetierTests(SimpleTestCase):
    def test_service_champs_obligatoires_et_approuves(self):
        with self.assertLogs("__MODULE__.metier", level="INFO") as logs:
            Service().calculer("confort", "marie.dupont@exemple.fr")
            Service().sans_log()
        (record,) = logs.records
        self.assertEqual(record.getMessage(), "service call completed")
        self.assertEqual(record.layer, "service")
        self.assertEqual(record.method, "Service.calculer")
        self.assertEqual(record.formule, "confort")
        self.assertEqual(getattr(record, "resultat.prix"), "12")
        self.assertIsInstance(record.durationMs, int)
        self.assertNotIn("marie.dupont", json.dumps(record.__dict__, default=str))

    def test_service_echec_warn_type_erreur_sans_resultat(self):
        with self.assertLogs("__MODULE__.metier", level="INFO") as logs, self.assertRaises(LookupError):
            Service().calculer("inconnue", "")
        (record,) = logs.records
        self.assertEqual(record.levelname, "WARNING")
        self.assertEqual(record.getMessage(), "service call failed")
        self.assertEqual(record.err, "LookupError")
        self.assertFalse(hasattr(record, "resultat.prix"))

    def test_db_appel_lent_en_warn(self):
        with (
            mock.patch.object(module.time, "monotonic", side_effect=[0, 0, 0, SEUIL_DB_LENT_MS / 1000]),
            self.assertLogs("__MODULE__.metier", level="INFO") as logs,
        ):
            Depot().trouver("confort")
            Depot().trouver("lente")
        self.assertEqual([r.getMessage() for r in logs.records], ["db call completed", "slow db call"])
        self.assertEqual(getattr(logs.records[0], "resultat.taille"), "1")
        self.assertEqual(logs.records[0].layer, "db")

    def test_refuse_a_l_import_un_champ_non_conforme(self):
        with self.assertRaisesRegex(ValueError, "donnée personnelle"):
            log_metier("service", champs=["email"])(lambda email: email)
        with self.assertRaisesRegex(ValueError, "racine inconnue"):
            log_metier("service", champs=["autre.x"])(lambda demande: demande)
        with self.assertRaisesRegex(ValueError, "couche inconnue"):
            log_metier("vue")


class ControleurTests(SimpleTestCase):
    def requete(self, chemin="/contrats/ref-1"):
        requete = RequestFactory().post(chemin)
        requete.resolver_match = ResolverMatch(lambda r: None, (), {}, route="contrats/<str:reference>")
        return requete

    def test_route_statut_duree_champs_approuves(self):
        @log_metier("controller", champs=["reference", "resultat.statut"])
        def vue(request, reference):
            return Response({"statut": "cree"}, status=201)

        with self.assertLogs("__MODULE__.metier", level="INFO") as logs:
            vue(self.requete(), reference="ref-1")
        (record,) = logs.records
        self.assertEqual(record.getMessage(), "http request handled")
        self.assertEqual(record.method, "POST")
        self.assertEqual(record.route, "contrats/<str:reference>")
        self.assertEqual(record.status, 201)
        self.assertEqual(record.reference, "ref-1")
        self.assertEqual(getattr(record, "resultat.statut"), "cree")

    def test_erreurs_converties_en_code_http(self):
        cas = [(Http404(), 404, "INFO"), (PermissionDenied(), 403, "INFO"), (NotFound(), 404, "INFO"),
               (RuntimeError("panne"), 500, "WARNING")]
        for exc, statut, niveau in cas:
            @log_metier("controller")
            def vue(request, exc=exc):
                raise exc

            with self.assertLogs("__MODULE__.metier", level="INFO") as logs, self.assertRaises(type(exc)):
                vue(self.requete())
            self.assertEqual(logs.records[0].status, statut)
            self.assertEqual(logs.records[0].levelname, niveau)

    def test_reponse_django_sans_route(self):
        @log_metier("controller", champs=["resultat.x"])
        def vue(request):
            return HttpResponse("ok")

        with self.assertLogs("__MODULE__.metier", level="INFO") as logs:
            vue(RequestFactory().get("/"))
        self.assertEqual(logs.records[0].route, "unmatched")
        self.assertEqual(logs.records[0].status, 200)
