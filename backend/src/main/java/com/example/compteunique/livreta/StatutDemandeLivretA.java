package com.example.compteunique.livreta;

// CU-31 : statut d'une demande d'ouverture de Livret A.
public enum StatutDemandeLivretA {
	/** Attestation validée, en attente de la réponse de l'administration fiscale. Aucun Livret A ouvert. */
	EN_COURS_DE_VERIFICATION,
	/** Réponse « aucun autre Livret A » : la suite du parcours est le versement initial (CU-33). */
	OUVERTURE_VALIDEE,
	/** Réponse « Livret A existant » : Livret A non ouvert, suite traitée par CU-296. */
	LIVRET_A_EXISTANT
}
