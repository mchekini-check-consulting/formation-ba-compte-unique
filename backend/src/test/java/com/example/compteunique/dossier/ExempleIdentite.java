package com.example.compteunique.dossier;

import java.time.LocalDate;

import com.example.compteunique.dossier.Categories.Nationalite;
import com.example.compteunique.dossier.Categories.PaysNaissance;
import com.example.compteunique.dossier.Categories.TypePiece;

// Identité de test (fictive), avec des espaces et des champs facultatifs vides à nettoyer.
public final class ExempleIdentite {

	private ExempleIdentite() {
	}

	public static IdentiteRequete identite() {
		return new IdentiteRequete("Mme", " Dupont ", " ", "Marie Claire", LocalDate.of(1990, 5, 14), "Lyon",
				PaysNaissance.FRANCE, Nationalite.FRANCE, "12 rue des Lilas", "", "69003", "Lyon", "France",
				"marie.dupont@exemple.fr", "06 00 00 00 00", null, TypePiece.CARTE_NATIONALE_IDENTITE,
				"2000000000123", LocalDate.of(2031, 6, 30));
	}

}
