package com.example.bouchonsidentite.fiscal;

// CU-295 : cas de test du bouchon de l'administration fiscale, choisi dans le panneau de test de l'écran
// « Éligibilité Livret A ». Sans cas choisi : SANS_LIVRET_A.
public enum CasFiscal {
	/** Critère 1 : aucun autre Livret A. */
	SANS_LIVRET_A,
	/** Critères 2 et 3 : un Livret A est détenu dans une autre banque. */
	DETENU_AILLEURS,
	/** Critère 4 : l'administration ne répond pas (pour tester le délai de 2 jours ouvrés). */
	SANS_REPONSE,
	/** Critère 6 : service indisponible. */
	INDISPONIBLE
}
