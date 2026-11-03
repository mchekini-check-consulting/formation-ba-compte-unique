package com.example.bouchonsidentite.commun;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Comparaison de noms et prénoms : sans tenir compte des majuscules, des accents ni des
// espaces en trop (« Dupont » = « DUPONT », « Hélène » = « Helene »). Un nom proche mais
// différent (« Dupond ») ne correspond pas.
public final class Textes {

	private Textes() {
	}

	public static String normaliser(String texte) {
		if (texte == null) {
			return "";
		}
		String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sansAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	public static boolean identiques(String a, String b) {
		return normaliser(a).equals(normaliser(b));
	}

	public static String obligatoire(String valeur, String nomDuChamp) {
		if (valeur == null || valeur.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomDuChamp + " obligatoire");
		}
		return valeur.trim();
	}

}
