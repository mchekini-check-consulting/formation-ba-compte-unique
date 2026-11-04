package com.example.bouchonsidentite.sanctions;

// Cas de test du bouchon US-B7 (CUB-70), choisis dans le panneau de test de l'écran « Mon identité ».
public enum CasDeTest {
	/** Aucune correspondance. */
	CONFORME,
	/** Correspondance avec une liste de sanctions. */
	SANCTIONNE,
	/** Correspondance avec une liste de personnes politiquement exposées. */
	PEP,
	/** Mode « indisponible » : pas de réponse avant 5 s, puis erreur 503. */
	INDISPONIBLE
}
