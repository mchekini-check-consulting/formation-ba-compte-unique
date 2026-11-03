package com.example.bouchonsidentite.commun;

// Statut du dossier d'ouverture qui découle de la vérification (le dossier lui-même est géré par US-01).
public enum StatutDossier {
	EN_COURS_DE_VERIFICATION,
	ANNULE;

	public static StatutDossier pour(Suite suite) {
		return suite == Suite.ANNULER ? ANNULE : EN_COURS_DE_VERIFICATION;
	}
}
