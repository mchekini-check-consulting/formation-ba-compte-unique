package com.example.bouchonsidentite.commun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TextesTest {

	@Test
	void normaliseMajusculesAccentsEtEspaces() {
		assertEquals("helene marie", Textes.normaliser("  HÉLÈNE   Marie "));
		assertEquals("", Textes.normaliser(null));
	}

	@Test
	void comparaisonStricteSurLesLettres() {
		assertTrue(Textes.identiques("Dupont", "DUPONT"));
		assertFalse(Textes.identiques("Dupont", "Dupond"));
	}

	@Test
	void champObligatoireNettoye() {
		assertEquals("abc", Textes.obligatoire(" abc ", "x"));
		assertEquals("x obligatoire", assertThrows(ResponseStatusException.class,
				() -> Textes.obligatoire("  ", "x")).getReason());
	}

	@Test
	void statutDuDossierSelonLaSuite() {
		assertEquals(StatutDossier.ANNULE, StatutDossier.pour(Suite.ANNULER));
		for (Suite suite : new Suite[] { Suite.POURSUIVRE, Suite.REPRENDRE, Suite.REESSAYER, Suite.REESSAYER_PLUS_TARD }) {
			assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, StatutDossier.pour(suite));
		}
	}

}
