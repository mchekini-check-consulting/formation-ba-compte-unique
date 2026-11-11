package com.example.compteunique.virement;

import java.math.BigInteger;
import java.util.Locale;

/**
 * CU-144 : contrôle de l'IBAN d'un bénéficiaire, tous pays (norme ISO 13616) : 2 lettres de pays, 2 chiffres de
 * clé, puis 11 à 30 lettres ou chiffres ; la clé est juste si le reste modulo 97 vaut 1 (lettres : A = 10 ... Z = 35).
 * CU-145 à CU-147 : forme enregistrée, IBAN de test (CU-88) et forme masquée affichée à la cliente.
 */
public final class IbanBeneficiaire {

	private static final BigInteger QUATRE_VINGT_DIX_SEPT = BigInteger.valueOf(97);
	/** CU-88 : IBAN de test = FR + clé + banque 99999 + guichet 00001 + compte (11) + clé RIB (2). */
	private static final String IBAN_DE_TEST = "FR\\d{2}9999900001\\d{13}";

	private IbanBeneficiaire() {
	}

	/** Forme enregistrée : sans espace, en majuscules ; vide si absent. */
	public static String normaliser(String iban) {
		return iban == null ? "" : iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
	}

	/** iban déjà sans espace et en majuscules. */
	public static boolean estValide(String iban) {
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

	/** CU-88 : IBAN fictif de la banque de test (99999, guichet 00001), refusé en production. */
	public static boolean estDeTest(String iban) {
		return iban != null && iban.matches(IBAN_DE_TEST);
	}

	/** CU-146 critère 11 : seulement les quatre derniers caractères (•••• 3000). */
	public static String masquer(String iban) {
		if (iban == null || iban.length() < 4) {
			return "••••";
		}
		return "•••• " + iban.substring(iban.length() - 4);
	}

}
