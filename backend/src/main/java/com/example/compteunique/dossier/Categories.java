package com.example.compteunique.dossier;

// Valeurs fermées du dossier d'ouverture (CUB-8). Enregistrées en base sous leur nom.
public final class Categories {

	private Categories() {
	}

	/** Pays de naissance : une seule valeur ; ETATS_UNIS porte l'indice américain (FATCA). */
	public enum PaysNaissance {
		FRANCE, UE_HORS_FRANCE, HORS_UE, ETATS_UNIS
	}

	/** Nationalité : une seule catégorie. */
	public enum Nationalite {
		FRANCE, UE_HORS_FRANCE, HORS_UE
	}

	/** Pièces acceptées : le passeport n'est pas proposé. Les deux rendent la cliente résidente française. */
	public enum TypePiece {
		CARTE_NATIONALE_IDENTITE, TITRE_SEJOUR
	}

	/** Résultat d'un contrôle sanctions ou PEP. */
	public enum ResultatControle {
		OK, NOK
	}

	public enum StatutDossier {
		EN_COURS_DE_VERIFICATION, ACCEPTE, ANNULE
	}

}
