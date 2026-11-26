package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

// CU-33 : les paramètres du Livret A du backend (ParametresLivretA) restent identiques à ceux de l'écran
// (frontend/src/app/livret-a/parametres.ts, CU-27). Si le taux, le plafond ou le minimum change d'un seul côté,
// ce test échoue. Il lit le fichier de l'écran dans le dépôt (présent en local et en CI).
class ParametresLivretATest {

	private static final Path PARAMETRES_ECRAN = Path.of("..", "frontend", "src", "app", "livret-a", "parametres.ts");

	private static String valeur(String source, String nom) {
		Matcher m = Pattern.compile("\\b" + nom + ":\\s*([0-9.]+)").matcher(source);
		assertTrue(m.find(), "paramètre « " + nom + " » introuvable dans " + PARAMETRES_ECRAN);
		return m.group(1);
	}

	@Test
	void identiquesAuxParametresDeLEcran() throws IOException {
		assumeTrue(Files.exists(PARAMETRES_ECRAN), "écran absent : comparaison faite en local et en CI");
		String source = Files.readString(PARAMETRES_ECRAN, StandardCharsets.UTF_8);
		assertEquals(ParametresLivretA.TAUX, Double.parseDouble(valeur(source, "taux")));
		assertEquals(ParametresLivretA.PLAFOND, Long.parseLong(valeur(source, "plafond")));
		assertEquals(ParametresLivretA.VERSEMENT_MINIMUM, Long.parseLong(valeur(source, "versementMinimum")));
	}

	@Test
	void montantsEnCentimes() {
		assertEquals(2_295_000L, ParametresLivretA.plafondCentimes());
		assertEquals(1_000L, ParametresLivretA.minimumCentimes());
	}

}
