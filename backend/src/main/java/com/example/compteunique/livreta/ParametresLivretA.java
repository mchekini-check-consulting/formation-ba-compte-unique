package com.example.compteunique.livreta;

// CU-33 : paramètres réglementés du Livret A côté backend (contrôle du versement initial). Copie des paramètres de
// l'écran (frontend/src/app/livret-a/parametres.ts, CU-27) : ParametresLivretATest vérifie à chaque contrôle que
// les deux restent identiques. Quand le taux ou le plafond change, modifier les deux fichiers.
public final class ParametresLivretA {

	private ParametresLivretA() {
	}

	/** Taux net annuel, en %. */
	public static final double TAUX = 1.7;

	/** Plafond de versements, hors intérêts capitalisés, en euros. */
	public static final long PLAFOND = 22950;

	/** Versement initial minimum, en euros. */
	public static final long VERSEMENT_MINIMUM = 10;

	static long plafondCentimes() {
		return PLAFOND * 100;
	}

	static long minimumCentimes() {
		return VERSEMENT_MINIMUM * 100;
	}

}
