package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IbanBeneficiaireTest {

	@Test
	void laFormeEnregistreeEstSansEspaceEtEnMajuscules() {
		assertEquals("FR7699999000019900000000105", IbanBeneficiaire.normaliser(" fr76 9999 9000 0199 0000 0000 105 "));
		assertEquals("", IbanBeneficiaire.normaliser(null));
	}

	@Test
	void unIbanDeTestEstCeluiDeLaBanque99999Guichet00001() {
		assertThat(IbanBeneficiaire.estDeTest("FR7699999000019900000000105")).isTrue();
		assertThat(IbanBeneficiaire.estDeTest("DE89370400440532013000")).isFalse();
		assertThat(IbanBeneficiaire.estDeTest("FR7630006000011234567890189")).isFalse();
		assertThat(IbanBeneficiaire.estDeTest(null)).isFalse();
	}

	@Test
	void leMasqueNeGardeQueLesQuatreDerniersCaracteres() {
		assertEquals("•••• 0105", IbanBeneficiaire.masquer("FR7699999000019900000000105"));
		assertEquals("••••", IbanBeneficiaire.masquer("FR7"));
		assertEquals("••••", IbanBeneficiaire.masquer(null));
	}

	@Test
	void laCleEstControlee() {
		assertThat(IbanBeneficiaire.estValide("DE89370400440532013000")).isTrue();
		assertThat(IbanBeneficiaire.estValide("DE88370400440532013000")).isFalse();
		assertThat(IbanBeneficiaire.estValide(null)).isFalse();
	}

}
