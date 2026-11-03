package com.example.bouchonsidentite.commun;

// Ce que le parcours d'ouverture (US-01 à US-03) doit faire après la réponse d'un bouchon.
public enum Suite {
	/** La vérification est réussie : le parcours continue. */
	POURSUIVRE,
	/** La photo est à reprendre (image illisible) : le client recommence l'étape. */
	REPRENDRE,
	/** Erreur technique ponctuelle : le client peut réessayer tout de suite, sans perdre sa saisie. */
	REESSAYER,
	/** Le partenaire ne répond pas, même après les relances : réessayer plus tard. */
	REESSAYER_PLUS_TARD,
	/** L'ouverture en ligne est annulée. */
	ANNULER
}
