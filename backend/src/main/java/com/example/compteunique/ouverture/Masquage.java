package com.example.compteunique.ouverture;

// Adresse e-mail et numéro de mobile masqués sur l'écran de confirmation (CU-13).
final class Masquage {

	private static final String POINTS = "•••";
	private static final int CHIFFRES_VISIBLES = 2;

	private Masquage() {
	}

	/** marie.dupont@example.com -> m•••@example.com */
	static String email(String email) {
		if (email == null) {
			return null;
		}
		int arobase = email.indexOf('@');
		if (arobase <= 0) {
			return POINTS;
		}
		return email.charAt(0) + POINTS + email.substring(arobase);
	}

	/** 06 12 34 56 78 -> 06 •• •• •• 78 */
	static String mobile(String mobile) {
		if (mobile == null) {
			return null;
		}
		String chiffres = mobile.replaceAll("\\D", "");
		if (chiffres.length() <= 2 * CHIFFRES_VISIBLES) {
			return POINTS;
		}
		return chiffres.substring(0, CHIFFRES_VISIBLES) + " •• •• •• "
				+ chiffres.substring(chiffres.length() - CHIFFRES_VISIBLES);
	}

}
