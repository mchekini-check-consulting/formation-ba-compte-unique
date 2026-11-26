package com.example.compteunique.gestion;

/** CU-314 / CU-338 : profil de gestion, du moins risqué au plus risqué (mêmes codes que le profil investisseur). */
public enum ProfilGestion {
	PRUDENT, EQUILIBRE, DYNAMIQUE;

	/** Vrai si ce profil est plus risqué que le profil investisseur (PRUDENT, EQUILIBRE, DYNAMIQUE). */
	boolean depasse(String profilInvestisseur) {
		return ordinal() > valueOf(profilInvestisseur).ordinal();
	}

	/**
	 * Décision PO du 26/11/2026 : profil par défaut d'un nouveau contrat, le moins risqué entre « équilibré » et le
	 * profil investisseur (prudente → prudent, équilibrée ou dynamique → équilibré).
	 */
	static ProfilGestion parDefaut(String profilInvestisseur) {
		return EQUILIBRE.depasse(profilInvestisseur) ? valueOf(profilInvestisseur) : EQUILIBRE;
	}
}
