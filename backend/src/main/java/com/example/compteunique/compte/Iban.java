package com.example.compteunique.compte;

import java.math.BigInteger;

// IBAN français (CU-88) : FR + clé IBAN (2 chiffres) + code banque (5) + code guichet (5)
// + numéro de compte (11) + clé RIB (2), soit 27 caractères.
public record Iban(String valeur, String numeroCompte) {

	static final String CODE_BANQUE = "99999";
	static final String CODE_GUICHET = "00001";

	private static final BigInteger QUATRE_VINGT_DIX_SEPT = BigInteger.valueOf(97);
	// « FR » converti en chiffres pour le calcul modulo 97 : F = 15, R = 27.
	private static final String FR_EN_CHIFFRES = "1527";

	/** Construit l'IBAN de test d'un numéro de compte à 11 chiffres (banque 99999, guichet 00001). */
	public static Iban de(String numeroCompte) {
		if (numeroCompte == null || !numeroCompte.matches("\\d{11}")) {
			throw new IllegalArgumentException("numéro de compte : 11 chiffres");
		}
		String bban = CODE_BANQUE + CODE_GUICHET + numeroCompte + cleRib(CODE_BANQUE, CODE_GUICHET, numeroCompte);
		return new Iban("FR" + cleIban(bban) + bban, numeroCompte);
	}

	/** Clé RIB : 97 - ((89 × banque + 15 × guichet + 3 × compte) modulo 97). */
	static String cleRib(String banque, String guichet, String compte) {
		BigInteger somme = BigInteger.valueOf(89).multiply(new BigInteger(banque))
				.add(BigInteger.valueOf(15).multiply(new BigInteger(guichet)))
				.add(BigInteger.valueOf(3).multiply(new BigInteger(compte)));
		return deuxChiffres(97 - somme.mod(QUATRE_VINGT_DIX_SEPT).intValue());
	}

	/** Clé IBAN : 98 - ((BBAN + « FR00 » en chiffres) modulo 97). */
	static String cleIban(String bban) {
		return deuxChiffres(98 - new BigInteger(bban + FR_EN_CHIFFRES + "00").mod(QUATRE_VINGT_DIX_SEPT).intValue());
	}

	/** Contrôle modulo 97 d'un IBAN français à chiffres : le reste doit valoir 1. */
	public static boolean estValide(String iban) {
		if (iban == null || !iban.matches("FR\\d{25}")) {
			return false;
		}
		String reordonne = iban.substring(4) + FR_EN_CHIFFRES + iban.substring(2, 4);
		return new BigInteger(reordonne).mod(QUATRE_VINGT_DIX_SEPT).intValue() == 1;
	}

	/** Forme affichée à la cliente : quatre derniers chiffres seulement (FR76 •••• •••• 1234). */
	public String masque() {
		return valeur.substring(0, 4) + " •••• •••• " + valeur.substring(valeur.length() - 4);
	}

	private static String deuxChiffres(int valeur) {
		return String.format("%02d", valeur);
	}

}
