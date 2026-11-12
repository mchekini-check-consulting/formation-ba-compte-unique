package com.example.bouchonsidentite.fiscal;

// CU-295 : réponses simulées de l'administration fiscale.
public enum ResultatFiscal {
	/** « Aucun autre Livret A » : l'ouverture peut continuer. */
	AUCUN_LIVRET_A,
	/** « Livret A existant », avec le détail seulement si la cliente l'a autorisé. */
	LIVRET_A_EXISTANT,
	/** Aucune réponse pour l'instant : la demande reste en attente. */
	SANS_REPONSE,
	/** Service indisponible. */
	INDISPONIBLE
}
