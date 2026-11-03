package com.example.bouchonsidentite.document;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Messages;
import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;
import com.example.bouchonsidentite.commun.Textes;
import com.example.bouchonsidentite.logging.Loggable;

// CUB-63 (US-B3) : bouchon de la vérification de pièce d'identité Stripe Identity.
// Chaque vérification est enregistrée (statut, motif, date, concordance par champ), jamais l'image.
@Service
@Loggable
public class VerificationDocumentService {

	/** Relances automatiques quand le partenaire ne répond pas (en plus du premier appel). */
	static final int RELANCES_MAX = 2;

	/** Validité de la pièce « conforme à la saisie » quand aucune date d'expiration n'est saisie. */
	static final int DUREE_VALIDITE_ANS = 10;

	private final VerificationDocumentRepository verifications;
	private final Clock horloge;

	public VerificationDocumentService(VerificationDocumentRepository verifications, Clock horloge) {
		this.verifications = verifications;
		this.horloge = horloge;
	}

	public VerificationDocumentReponse verifier(VerificationDocumentRequete requete) {
		String dossierId = Textes.obligatoire(requete.dossierId(), "dossierId");
		PieceTest recto = piece(requete.recto(), "recto");
		PieceTest verso = piece(requete.verso(), "verso");
		SaisieIdentite saisie = saisieComplete(requete.saisie());

		if (recto != verso) {
			return enregistrer(dossierId, StatutVerificationDocument.A_REPRENDRE,
					"le recto et le verso ne correspondent pas à la même pièce", null, null, 1, Suite.REPRENDRE, null);
		}
		return switch (recto) {
			case IMAGE_ILLISIBLE -> enregistrer(dossierId, StatutVerificationDocument.A_REPRENDRE,
					"image illisible : photo floue, reflet ou bords coupés", null, null, 1, Suite.REPRENDRE, null);
			case ERREUR_TECHNIQUE -> enregistrer(dossierId, StatutVerificationDocument.ERREUR_TECHNIQUE,
					"erreur technique du partenaire", null, null, 1, Suite.REESSAYER, Messages.ERREUR_TECHNIQUE);
			case SERVICE_INDISPONIBLE -> enregistrer(dossierId, StatutVerificationDocument.INDISPONIBLE,
					"le partenaire ne répond pas", null, null, 1 + RELANCES_MAX, Suite.REESSAYER_PLUS_TARD,
					Messages.VERIFICATION_IMPOSSIBLE);
			case PIECE_CONFORME_A_LA_SAISIE -> verifierPieceLisible(dossierId, lueCommeSaisie(saisie), saisie);
			default -> verifierPieceLisible(dossierId, recto.donnees(), saisie);
		};
	}

	private VerificationDocumentReponse verifierPieceLisible(String dossierId, DonneesExtraites donnees,
			SaisieIdentite saisie) {
		if (donnees.dateExpiration().isBefore(LocalDate.now(horloge))) {
			return enregistrer(dossierId, StatutVerificationDocument.REJETE, "document expiré", donnees, null, 1,
					Suite.ANNULER, Messages.OUVERTURE_ANNULEE);
		}
		Concordance concordance = Concordance.entre(donnees, saisie);
		if (!concordance.complete()) {
			// CUB-9 : pièce authentique mais qui ne concorde pas avec la saisie -> rejetée, dossier annulé.
			return enregistrer(dossierId, StatutVerificationDocument.REJETE,
					"les informations de la pièce ne correspondent pas à la saisie", donnees, concordance, 1,
					Suite.ANNULER, Messages.OUVERTURE_ANNULEE);
		}
		return enregistrer(dossierId, StatutVerificationDocument.VERIFIE, null, donnees, concordance, 1,
				Suite.POURSUIVRE, null);
	}

	// Pièce « conforme à la saisie » : elle porte l'identité saisie. Sans date d'expiration saisie,
	// elle expire dans 10 ans (DUREE_VALIDITE_ANS).
	private DonneesExtraites lueCommeSaisie(SaisieIdentite saisie) {
		LocalDate expiration = saisie.dateExpiration() != null ? saisie.dateExpiration()
				: LocalDate.now(horloge).plusYears(DUREE_VALIDITE_ANS);
		return new DonneesExtraites(saisie.nomNaissance(), saisie.prenoms(), saisie.dateNaissance(), expiration);
	}

	private VerificationDocumentReponse enregistrer(String dossierId, StatutVerificationDocument statut, String motif,
			DonneesExtraites donnees, Concordance concordance, int tentatives, Suite suite, String message) {
		VerificationDocument verification = verifications.save(
				new VerificationDocument(dossierId, statut, motif, concordance, OffsetDateTime.now(horloge)));
		return new VerificationDocumentReponse(verification.getId(), statut, motif, donnees, concordance, tentatives,
				suite, StatutDossier.pour(suite), message, verification.getVerifieLe());
	}

	private static PieceTest piece(String code, String face) {
		String valeur = Textes.obligatoire(code, face);
		try {
			return PieceTest.valueOf(valeur);
		} catch (IllegalArgumentException inconnue) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, face + " : pièce de test inconnue", inconnue);
		}
	}

	private static SaisieIdentite saisieComplete(SaisieIdentite saisie) {
		if (saisie == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "saisie obligatoire");
		}
		Textes.obligatoire(saisie.nomNaissance(), "saisie.nomNaissance");
		Textes.obligatoire(saisie.prenoms(), "saisie.prenoms");
		if (saisie.dateNaissance() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "saisie.dateNaissance obligatoire");
		}
		return saisie;
	}

}
