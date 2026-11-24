package com.example.compteunique.catalogue;

// CU-381 : contrôle d'un code ISIN (norme ISO 6166) : 2 lettres (pays), 9 caractères alphanumériques, 1 chiffre
// de contrôle. Les lettres sont converties en nombres (A = 10 ... Z = 35), puis la clé est vérifiée par l'algorithme
// de Luhn.
final class CodeIsin {

	private CodeIsin() {
	}

	static boolean estValide(String isin) {
		if (isin == null || !isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]")) {
			return false;
		}
		StringBuilder chiffres = new StringBuilder();
		for (char caractere : isin.toCharArray()) {
			chiffres.append(Character.digit(caractere, 36));
		}
		int somme = 0;
		boolean doubler = false;
		for (int i = chiffres.length() - 1; i >= 0; i--) {
			int chiffre = chiffres.charAt(i) - '0';
			if (doubler) {
				chiffre *= 2;
				if (chiffre > 9) {
					chiffre -= 9;
				}
			}
			somme += chiffre;
			doubler = !doubler;
		}
		return somme % 10 == 0;
	}

}
