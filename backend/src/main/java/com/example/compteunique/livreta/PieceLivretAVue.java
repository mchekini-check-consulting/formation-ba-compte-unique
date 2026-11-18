package com.example.compteunique.livreta;

import java.time.LocalDate;

import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.livreta.PieceLivretAService.PieceAVerifier;
import com.example.compteunique.livreta.PieceLivretAService.Suivi;

/**
 * CU-29 : vérification de la nouvelle pièce vue par la cliente. aVerifier : identité à envoyer à l'analyse, tant
 * que la pièce l'attend. En cas d'arrêt, le message ne donne pas le détail de la vérification. Le numéro de pièce
 * n'est jamais renvoyé.
 */
public record PieceLivretAVue(String reference, StatutPieceLivretA statut, String message, TypePiece typePiece,
		LocalDate dateExpiration, PieceAVerifier aVerifier) {

	/** Délai annoncé quand la démarche est en attente (à confirmer par le métier, CU-29). */
	static final String DELAI_ATTENTE = "48 heures ouvrées";

	static final String PIECE_A_VERIFIER = "Transmettez votre nouvelle pièce d'identité.";
	static final String SELFIE_A_FAIRE = "Votre pièce est vérifiée. Dernière étape : la vérification de présence (selfie).";
	static final String VALIDE = "Votre nouvelle pièce d'identité est enregistrée. Étape suivante : l'éligibilité.";
	static final String BLOQUE = "Votre demande de Livret A ne peut pas aboutir en ligne. Contactez un conseiller.";
	static final String EN_ATTENTE = "La vérification de votre pièce n'a pas pu aboutir pour le moment. Votre demande est en attente : un conseiller a été alerté et la traitera sous "
			+ DELAI_ATTENTE + ".";

	static PieceLivretAVue de(Suivi suivi) {
		PieceIdentiteLivretA verification = suivi.verification();
		return new PieceLivretAVue(verification.getReference(), verification.getStatut(),
				message(verification.getStatut()), verification.getTypePiece(), verification.getDateExpiration(),
				suivi.aVerifier());
	}

	static String message(StatutPieceLivretA statut) {
		return switch (statut) {
			case PIECE_A_VERIFIER -> PIECE_A_VERIFIER;
			case SELFIE_A_FAIRE -> SELFIE_A_FAIRE;
			case VALIDE -> VALIDE;
			case BLOQUE -> BLOQUE;
			case EN_ATTENTE -> EN_ATTENTE;
		};
	}

}
