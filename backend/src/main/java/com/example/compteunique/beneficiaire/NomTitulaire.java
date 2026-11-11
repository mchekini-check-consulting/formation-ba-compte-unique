package com.example.compteunique.beneficiaire;

import java.text.Normalizer;
import java.util.Locale;

/**
 * CU-145 critère 5 : comparaison du nom saisi avec celui du titulaire, avec la normalisation de US-02 (CU-84) :
 * majuscules, accents, espaces et tirets ignorés (« Hélène Marie-Claire » = « helene marie claire »).
 */
final class NomTitulaire {

	private NomTitulaire() {
	}

	static String normaliser(String nom) {
		if (nom == null) {
			return "";
		}
		String sansAccents = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sansAccents.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
	}

	static boolean correspondent(String saisi, String titulaire) {
		String normalise = normaliser(saisi);
		return !normalise.isEmpty() && normalise.equals(normaliser(titulaire));
	}

}
