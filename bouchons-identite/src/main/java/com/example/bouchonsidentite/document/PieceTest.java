package com.example.bouchonsidentite.document;

import java.time.LocalDate;

// Jeu de pièces d'identité de test (fictives). Le bouchon ne reçoit pas d'image : le recto et
// le verso sont désignés par le code de la pièce de test.
public enum PieceTest {
	/** Pièce valide dont les données lues sont celles saisies par le client : concorde toujours (essais avec n'importe quelle identité). */
	PIECE_CONFORME_A_LA_SAISIE(null),
	/** Pièce valide : vérifiée, données extraites renvoyées. */
	PIECE_VALIDE(new DonneesExtraites("Dupont", "Marie Claire", LocalDate.of(1990, 5, 14), LocalDate.of(2031, 6, 30))),
	/** Pièce valide d'une autre personne : sert à tester la non-concordance avec la saisie. */
	PIECE_VALIDE_AUTRE_PERSONNE(new DonneesExtraites("Martin", "Paul", LocalDate.of(1985, 2, 3), LocalDate.of(2030, 9, 15))),
	/** Pièce dont la date d'expiration est dépassée : rejetée. */
	PIECE_EXPIREE(new DonneesExtraites("Durand", "Sophie", LocalDate.of(1978, 11, 22), LocalDate.of(2024, 1, 31))),
	/** Photo floue ou avec reflet : à reprendre. */
	IMAGE_ILLISIBLE(null),
	/** Erreur technique ponctuelle du partenaire : le client peut réessayer. */
	ERREUR_TECHNIQUE(null),
	/** Le partenaire ne répond pas, même après 2 relances. */
	SERVICE_INDISPONIBLE(null);

	private final DonneesExtraites donnees;

	PieceTest(DonneesExtraites donnees) {
		this.donnees = donnees;
	}

	public DonneesExtraites donnees() {
		return donnees;
	}
}
