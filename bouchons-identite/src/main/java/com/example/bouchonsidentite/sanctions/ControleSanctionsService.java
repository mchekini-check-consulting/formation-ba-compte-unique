package com.example.bouchonsidentite.sanctions;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Messages;
import com.example.bouchonsidentite.commun.Textes;
import com.example.bouchonsidentite.logging.Loggable;

// CUB-70 (US-B7) : bouchon de l'API GEL des avoirs et du contrôle PEP.
// Personnes de test (prénom « Test », nom ci-dessous) :
//   Test Sanctionné   -> correspondance sanctions
//   Test PEP          -> correspondance PEP
//   Test Conforme     -> aucune correspondance (comme toute autre identité)
//   Test Indisponible -> mode « indisponible » : pas de réponse avant 5 s, puis erreur 503
// Le champ casDeTest de la requête (panneau de test de l'écran) remplace cette recherche par nom.
// Rien n'est enregistré : le résultat est conservé par US-01.
@Service
@Loggable
public class ControleSanctionsService {

	private static final Map<String, ResultatControle> PERSONNES_DE_TEST = Map.of(
			Textes.normaliser("Test Sanctionné"), ResultatControle.CORRESPONDANCE_SANCTIONS,
			Textes.normaliser("Test PEP"), ResultatControle.CORRESPONDANCE_PEP);
	private static final String PERSONNE_INDISPONIBLE = Textes.normaliser("Test Indisponible");

	private final Duration delaiIndisponible;

	public ControleSanctionsService(
			@Value("${bouchons.sanctions.delai-indisponible:6s}") Duration delaiIndisponible) {
		this.delaiIndisponible = delaiIndisponible;
	}

	public ControleReponse controler(ControleRequete requete) {
		String nom = Textes.obligatoire(requete.nomNaissance(), "nomNaissance");
		String prenoms = Textes.obligatoire(requete.prenoms(), "prenoms");
		if (requete.casDeTest() != null) {
			return selon(requete.casDeTest());
		}
		String identite = Textes.normaliser(prenoms + " " + nom);
		if (PERSONNE_INDISPONIBLE.equals(identite)) {
			throw indisponibleApresDelai();
		}
		return ControleReponse.pour(PERSONNES_DE_TEST.getOrDefault(identite, ResultatControle.AUCUNE_CORRESPONDANCE));
	}

	private ControleReponse selon(CasDeTest cas) {
		return switch (cas) {
			case SANCTIONNE -> ControleReponse.pour(ResultatControle.CORRESPONDANCE_SANCTIONS);
			case PEP -> ControleReponse.pour(ResultatControle.CORRESPONDANCE_PEP);
			case INDISPONIBLE -> throw indisponibleApresDelai();
			case CONFORME -> ControleReponse.pour(ResultatControle.AUCUNE_CORRESPONDANCE);
		};
	}

	/** Attend le délai du mode « indisponible », puis renvoie l'erreur 503 à lever. */
	private ResponseStatusException indisponibleApresDelai() {
		try {
			Thread.sleep(delaiIndisponible);
		} catch (InterruptedException interruption) {
			Thread.currentThread().interrupt();
		}
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, Messages.VERIFICATION_IMPOSSIBLE);
	}

}
