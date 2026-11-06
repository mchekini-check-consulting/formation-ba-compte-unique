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

	/** CU-9 : motif d'un dossier « Annulé ». */
	public enum MotifAnnulation {
		/** Sanctions, PEP, pièce expirée ou non authentique, permis de conduire, écart de concordance. */
		CONTROLE_AUTOMATIQUE(true),
		DECISION_CONFORMITE(true),
		DEMANDE_CLIENTE(false),
		INACTIVITE(false),
		CODE_EXPIRE(false),
		TROIS_TENTATIVES(false);

		private final boolean bloquant;

		MotifAnnulation(boolean bloquant) {
			this.bloquant = bloquant;
		}

		/** Annulé par contrôle : bloque toute nouvelle ouverture en ligne, seul un conseiller peut la reprendre. */
		public boolean estBloquant() {
			return bloquant;
		}
	}

}
