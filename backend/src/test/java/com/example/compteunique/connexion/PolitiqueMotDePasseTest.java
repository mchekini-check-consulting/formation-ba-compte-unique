package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PolitiqueMotDePasseTest {

	@Test
	void accepteUnMotDePasseConforme() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghij1!")).isEmpty();
	}

	@Test
	void signaleChaqueRegleNonRespectee() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghi1!")).containsExactly("Au moins 12 caractères");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("abcdefghij1!")).containsExactly("Au moins une majuscule");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("ABCDEFGHIJ1!")).containsExactly("Au moins une minuscule");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghijk!")).containsExactly("Au moins un chiffre");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghijk1"))
				.containsExactly("Au moins un caractère spécial");
	}

	@Test
	void refuseUnMotDePasseAbsent() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees(null)).hasSize(5);
	}

}
