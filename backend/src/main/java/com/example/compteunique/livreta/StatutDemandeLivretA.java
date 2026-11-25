package com.example.compteunique.livreta;

// CU-31 et CU-296 : statut d'une demande d'ouverture de Livret A.
public enum StatutDemandeLivretA {
	/** Attestation validée, en attente de la réponse de l'administration fiscale. Aucun Livret A ouvert. */
	EN_COURS_DE_VERIFICATION,
	/** Réponse « aucun autre Livret A », ou certificat de clôture validé : suite = versement initial (CU-33). */
	OUVERTURE_VALIDEE,
	/** Réponse « Livret A existant » : Livret A non ouvert, la cliente choisit la suite (CU-296). */
	LIVRET_A_EXISTANT,
	/** CU-296 : la cliente clôture elle-même son ancien livret, certificat de clôture attendu. */
	EN_ATTENTE_CERTIFICAT,
	/** CU-296 : certificat déposé, en attente de la vérification d'un conseiller. */
	CERTIFICAT_A_VERIFIER,
	/** CU-296 : aucun certificat validé à la date limite. Ne bloque plus une nouvelle demande. */
	ANNULEE,
	/** CU-296 : la cliente a renoncé à l'ouverture. Ne bloque plus une nouvelle demande. */
	RENONCEE;

	/** CU-296 : une demande annulée ou renoncée ne bloque plus une nouvelle demande. */
	boolean estTerminee() {
		return this == ANNULEE || this == RENONCEE;
	}
}
