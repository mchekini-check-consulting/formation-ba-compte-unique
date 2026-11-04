package com.example.compteunique.compte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// CU-88 : un cas de test par critère d'acceptation.
class GenerateurIbanTest {

	private final CompteRepository comptes = mock(CompteRepository.class);

	@Test
	void ibanAuFormatFrancaisDeTest() {
		Iban iban = new GenerateurIban(comptes).generer();
		assertThat(iban.valeur()).hasSize(27).matches("FR\\d{2}9999900001\\d{11}\\d{2}");
		assertThat(iban.valeur().substring(14, 25)).isEqualTo(iban.numeroCompte());
	}

	@Test
	void cleIbanValideParModulo97() {
		for (int i = 0; i < 200; i++) {
			assertThat(Iban.estValide(new GenerateurIban(comptes).generer().valeur())).isTrue();
		}
	}

	@Test
	void exempleCalculeALaMain() {
		// banque 99999, guichet 00001, compte 00000000001 :
		// clé RIB = 97 - ((89×99999 + 15×1 + 3×1) mod 97) = 97 - (8 899 929 mod 97) = 97 - 82 = 15
		Iban iban = Iban.de("00000000001");
		assertThat(iban.valeur()).endsWith("999990000100000000001" + Iban.cleRib("99999", "00001", "00000000001"));
		assertThat(Iban.cleRib("99999", "00001", "00000000001")).isEqualTo("15");
		assertThat(Iban.estValide(iban.valeur())).isTrue();
	}

	@Test
	void numeroDejaEnBaseRegenere() {
		RandomGenerator aleatoire = mock(RandomGenerator.class);
		when(aleatoire.nextLong(100_000_000_000L)).thenReturn(1L, 2L);
		when(comptes.existsByNumeroCompte("00000000001")).thenReturn(true);
		Iban iban = new GenerateurIban(comptes, aleatoire).generer();
		assertThat(iban.numeroCompte()).isEqualTo("00000000002");
		verify(comptes, times(2)).existsByNumeroCompte(anyString());
	}

	@Test
	void echecSiAucunNumeroLibre() {
		when(comptes.existsByNumeroCompte(anyString())).thenReturn(true);
		GenerateurIban generateur = new GenerateurIban(comptes);
		assertThrows(IllegalStateException.class, generateur::generer);
		verify(comptes, times(GenerateurIban.TIRAGES_MAX)).existsByNumeroCompte(anyString());
	}

	@ParameterizedTest
	@ValueSource(strings = { "FR7699999000010000000000127", "FR76 1234", "DE89370400440532013000", "" })
	void ibanFauxOuMalFormeRefuse(String iban) {
		assertThat(Iban.estValide(iban)).isFalse();
	}

	@Test
	void ibanNulRefuse() {
		assertThat(Iban.estValide(null)).isFalse();
		assertThrows(IllegalArgumentException.class, () -> Iban.de("123"));
		assertThrows(IllegalArgumentException.class, () -> Iban.de(null));
	}

	@Test
	void ibanMasqueNeMontreQueLesQuatreDerniersChiffres() {
		Iban iban = Iban.de("12345678901");
		assertThat(iban.masque()).startsWith(iban.valeur().substring(0, 4)).endsWith(iban.valeur().substring(23))
				.contains("••••");
	}

}
