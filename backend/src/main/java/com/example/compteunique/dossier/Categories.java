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
		EN_COURS_DE_VERIFICATION, ACCEPTE, ANNULE,
		/** CU-12 : convention signée ; déclenche la création du compte (CU-13). */
		CONVENTION_SIGNEE,
		/** CU-13 : création du compte en échec après les relances automatiques, à relancer. */
		CREATION_A_RELANCER,
		/** CU-13 : compte courant et identifiant de connexion créés. */
		COMPTE_OUVERT
	}

}
