package com.example.compteunique.connexion;

import java.util.ArrayList;
import java.util.List;

// Politique de mot de passe (maquette de l'étape 3 « Me connecter »), contrôlée côté serveur
// même si le contrôle de l'écran a été contourné.
final class PolitiqueMotDePasse {

	private static final int LONGUEUR_MINIMALE = 12;

	private PolitiqueMotDePasse() {
	}

	static List<String> reglesNonRespectees(String motDePasse) {
		String valeur = motDePasse == null ? "" : motDePasse;
		List<String> regles = new ArrayList<>();
		if (valeur.length() < LONGUEUR_MINIMALE) {
			regles.add("Au moins 12 caractères");
		}
		if (valeur.chars().noneMatch(Character::isUpperCase)) {
			regles.add("Au moins une majuscule");
		}
		if (valeur.chars().noneMatch(Character::isLowerCase)) {
			regles.add("Au moins une minuscule");
		}
		if (valeur.chars().noneMatch(Character::isDigit)) {
			regles.add("Au moins un chiffre");
		}
		if (valeur.chars().allMatch(Character::isLetterOrDigit)) {
			regles.add("Au moins un caractère spécial");
		}
		return regles;
	}

}
