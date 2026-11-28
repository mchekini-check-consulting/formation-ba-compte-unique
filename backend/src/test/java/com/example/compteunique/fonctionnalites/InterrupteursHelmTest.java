package com.example.compteunique.fonctionnalites;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Lot 3 (PO, 27/11/2026), test des liens côté configuration : un bouton « Bientôt » ne doit jamais correspondre à un
// interrupteur activé. Seules les fonctionnalités livrées (page développée) peuvent être à "true" dans helm.yaml :
// livrer une fonctionnalité, c'est ajouter sa page, la déclarer ici et passer son interrupteur à "true".
class InterrupteursHelmTest {

	/** Fonctionnalités livrées : leur interrupteur peut être activé. */
	private static final Set<String> LIVREES = Set.of("FONCTIONNALITE_OUTILS_TEST");

	private static final Pattern INTERRUPTEUR = Pattern
			.compile("- key: (FONCTIONNALITE_[A-Z_]+)\\s*\\n\\s*value: ([^\\n]+)");

	private static Map<String, String> interrupteurs() throws IOException {
		String helm = Files.readString(Path.of("..", "helm.yaml"));
		Map<String, String> valeurs = new LinkedHashMap<>();
		Matcher m = INTERRUPTEUR.matcher(helm.replace("\r\n", "\n"));
		while (m.find()) {
			valeurs.put(m.group(1), m.group(2));
		}
		return valeurs;
	}

	@Test
	@DisplayName("helm.yaml : 21 interrupteurs ; seuls ceux des fonctionnalités livrées peuvent être activés")
	void aucunBoutonBientotActive() throws IOException {
		Map<String, String> valeurs = interrupteurs();

		assertThat(valeurs).hasSize(21);
		valeurs.forEach((cle, valeur) -> {
			if (!LIVREES.contains(cle)) {
				assertThat(valeur).as(cle + " : fonctionnalité sans page, interrupteur à laisser à \"false\"")
						.doesNotContain("true");
			}
		});
	}

	@Test
	@DisplayName("D4 : « Outils de test » activé en dev et qualif seulement")
	void outilsDeTestHorsPreprodEtProd() throws IOException {
		assertThat(interrupteurs().get("FONCTIONNALITE_OUTILS_TEST"))
				.isEqualTo("{dev: \"true\", qualif: \"true\", preprod: \"false\", prod: \"false\"}");
	}

}
