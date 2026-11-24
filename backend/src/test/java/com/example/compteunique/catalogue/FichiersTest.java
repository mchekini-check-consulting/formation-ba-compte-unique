package com.example.compteunique.catalogue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// CU-381 : fichiers de test joints à CAT-01 (CU-337), dans src/test/resources/catalogue/.
final class FichiersTest {

	static final String ENTETE = String.join(";", LecteurCatalogue.COLONNES);

	private FichiersTest() {
	}

	/** catalogue_uc_2026-11-23.csv : 102 supports (80 cotés, 22 non cotés, 3 retirés). */
	static String principal() {
		return lire("catalogue_uc_2026-11-23.csv");
	}

	/** Fichier principal complété des 6 lignes de catalogue_uc_anomalies_test.csv. */
	static String avecAnomalies() {
		String anomalies = lire("catalogue_uc_anomalies_test.csv");
		String sansEntete = anomalies.substring(anomalies.indexOf('\n') + 1);
		String principal = principal();
		return principal + (principal.endsWith("\n") ? "" : "\r\n") + sansEntete;
	}

	/** Un fichier fait d'un en-tête et des lignes données. */
	static String fichier(String... lignes) {
		return ENTETE + "\n" + String.join("\n", lignes) + "\n";
	}

	/** Une ligne valide ; les colonnes se remplacent par « colonne=valeur ». */
	static String ligne(String... remplacements) {
		List<String> valeurs = Arrays.asList("QZ0000000017", "Monétaire Euro Court Terme", "Monétaire",
				"Monétaire euro", "PER|AV", "1", "oui", "0.10", "actif", "25.00", "2026-11-20", "EUR", "quotidienne",
				"aucun", "Aurévia AM");
		for (String remplacement : remplacements) {
			String[] parties = remplacement.split("=", 2);
			valeurs.set(LecteurCatalogue.COLONNES.indexOf(parties[0]), parties[1]);
		}
		return String.join(";", valeurs);
	}

	/** Lignes de données du fichier principal (sans l'en-tête). */
	static List<String> lignesPrincipal() {
		return principal().lines().skip(1).filter(l -> !l.isBlank()).collect(Collectors.toList());
	}

	private static String lire(String nom) {
		try (InputStream flux = FichiersTest.class.getResourceAsStream("/catalogue/" + nom)) {
			if (flux == null) {
				throw new IllegalStateException("Fichier de test absent : " + nom);
			}
			return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
