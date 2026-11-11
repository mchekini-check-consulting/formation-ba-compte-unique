package com.example.compteunique.virement;

import java.math.BigInteger;

/**
 * CU-144 : contrôle de l'IBAN d'un bénéficiaire, tous pays (norme ISO 13616) : 2 lettres de pays, 2 chiffres de
 * clé, puis 11 à 30 lettres ou chiffres ; la clé est juste si le reste modulo 97 vaut 1 (lettres : A = 10 ... Z = 35).
 */
final class IbanBeneficiaire {

	private static final BigInteger QUATRE_VINGT_DIX_SEPT = BigInteger.valueOf(97);

	private IbanBeneficiaire() {
	}

	/** iban déjà sans espace et en majuscules. */
	static boolean estValide(String iban) {
		if (iban == null || !iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}")) {
			return false;
		}
		String reordonne = iban.substring(4) + iban.substring(0, 4);
		StringBuilder chiffres = new StringBuilder();
		for (char caractere : reordonne.toCharArray()) {
			chiffres.append(Character.getNumericValue(caractere));
		}
		return new BigInteger(chiffres.toString()).mod(QUATRE_VINGT_DIX_SEPT).intValue() == 1;
	}

}
