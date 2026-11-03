package com.example.bouchonsidentite.selfie;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Messages;
import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;
import com.example.bouchonsidentite.commun.Textes;
import com.example.bouchonsidentite.logging.Loggable;

// CUB-64 (US-B4) : bouchon de la vérification du selfie Stripe Identity.
// Selfies de test (aucune image n'est reçue ni conservée) :
//   SELFIE_CONFORME      -> correspondance, score 96 %
//   SELFIE_DIFFERENT     -> non-correspondance, score 42 %, dossier annulé
//   SCORE_<n>            -> score de n % (0 à 100), pour tester le seuil de 90 %
//   PHOTO_ECRAN          -> échec de vérification de présence, dossier annulé
//   SERVICE_INDISPONIBLE -> pas de réponse en 30 s, 2 relances, puis indisponibilité technique
@Service
@Loggable
public class VerificationSelfieService {

	/** Score minimal, en %, pour que le selfie corresponde à la pièce. */
	static final int SEUIL_CORRESPONDANCE = 90;
	/** Relances automatiques quand le partenaire ne répond pas (en plus du premier appel). */
	static final int RELANCES_MAX = 2;

	private static final Pattern SCORE = Pattern.compile("SCORE_(\\d{1,3})");

	private final VerificationSelfieRepository verifications;
	private final Clock horloge;

	public VerificationSelfieService(VerificationSelfieRepository verifications, Clock horloge) {
		this.verifications = verifications;
		this.horloge = horloge;
	}

	public VerificationSelfieReponse verifier(VerificationSelfieRequete requete) {
		String dossierId = Textes.obligatoire(requete.dossierId(), "dossierId");
		String selfie = Textes.obligatoire(requete.selfie(), "selfie");
		return switch (selfie) {
			case "SELFIE_CONFORME" -> comparer(dossierId, 96);
			case "SELFIE_DIFFERENT" -> comparer(dossierId, 42);
			case "PHOTO_ECRAN" -> enregistrer(dossierId, ResultatSelfie.ECHEC_VERIFICATION_PRESENCE, null, 1,
					Suite.ANNULER, Messages.OUVERTURE_ANNULEE);
			case "SERVICE_INDISPONIBLE" -> enregistrer(dossierId, ResultatSelfie.INDISPONIBILITE_TECHNIQUE, null,
					1 + RELANCES_MAX, Suite.REESSAYER_PLUS_TARD, Messages.VERIFICATION_IMPOSSIBLE);
			default -> comparer(dossierId, scoreDemande(selfie));
		};
	}

	private VerificationSelfieReponse comparer(String dossierId, int score) {
		if (score >= SEUIL_CORRESPONDANCE) {
			return enregistrer(dossierId, ResultatSelfie.CORRESPONDANCE, score, 1, Suite.POURSUIVRE, null);
		}
		return enregistrer(dossierId, ResultatSelfie.NON_CORRESPONDANCE, score, 1, Suite.ANNULER,
				Messages.OUVERTURE_ANNULEE);
	}

	private VerificationSelfieReponse enregistrer(String dossierId, ResultatSelfie resultat, Integer score,
			int tentatives, Suite suite, String message) {
		StatutDossier statutDossier = StatutDossier.pour(suite);
		VerificationSelfie verification = verifications.save(
				new VerificationSelfie(dossierId, resultat, score, statutDossier, OffsetDateTime.now(horloge)));
		return new VerificationSelfieReponse(verification.getId(), resultat, score, tentatives, suite, statutDossier,
				message, verification.getVerifieLe());
	}

	private static int scoreDemande(String selfie) {
		Matcher score = SCORE.matcher(selfie);
		if (score.matches() && Integer.parseInt(score.group(1)) <= 100) {
			return Integer.parseInt(score.group(1));
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selfie : selfie de test inconnu");
	}

}
