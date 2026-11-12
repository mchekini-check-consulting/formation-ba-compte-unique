package com.example.bouchonsidentite.fiscal;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// Corps des requêtes et réponses du bouchon de l'administration fiscale (CU-295).
public final class FiscalRequetes {

	private FiscalRequetes() {
	}

	/**
	 * POST /administration-fiscale/interrogations : informations transmises par la banque (CU-31) et choix de la
	 * cliente sur le partage du détail. casDeTest facultatif (panneau de test), SANS_LIVRET_A par défaut.
	 */
	public record Interrogation(String demandeId, String nom, String prenoms, String sexe, LocalDate dateNaissance,
			String lieuNaissance, boolean partageDetailAutorise, CasFiscal casDeTest) {
	}

	/** Détail fictif d'un Livret A détenu ailleurs. */
	public record DetailLivretA(String etablissement, String agence, LocalDate dateOuverture) {
	}

	/** detail : seulement pour LIVRET_A_EXISTANT avec l'accord de la cliente, sinon null. */
	public record ReponseFiscale(String interrogationId, ResultatFiscal resultat, DetailLivretA detail,
			String message, OffsetDateTime appeleLe) {
	}

}
