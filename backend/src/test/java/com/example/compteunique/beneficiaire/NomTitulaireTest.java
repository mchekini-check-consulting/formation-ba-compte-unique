package com.example.compteunique.beneficiaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// CU-145 critère 5 : même normalisation que US-02 (CU-84), tirets inclus.
class NomTitulaireTest {

	@Test
	void laCasseLesAccentsLesEspacesEtLesTiretsSontIgnores() {
		assertThat(NomTitulaire.correspondent("helene marie claire", "Hélène Marie-Claire")).isTrue();
		assertThat(NomTitulaire.correspondent("  TEST   CLIENTE A ", "Test Cliente-A")).isTrue();
		assertEquals("HELENEMARIECLAIRE", NomTitulaire.normaliser("Hélène Marie-Claire"));
	}

	@Test
	void unNomProcheMaisDifferentNeCorrespondPas() {
		assertThat(NomTitulaire.correspondent("Test Cliente-B", "Test Cliente-A")).isFalse();
	}

	@Test
	void unNomVideNeCorrespondJamais() {
		assertThat(NomTitulaire.correspondent(null, "")).isFalse();
		assertThat(NomTitulaire.correspondent(" - ", "")).isFalse();
		assertEquals("", NomTitulaire.normaliser(null));
	}

}
