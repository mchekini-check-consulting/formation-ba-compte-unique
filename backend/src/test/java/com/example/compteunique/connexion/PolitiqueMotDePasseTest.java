package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PolitiqueMotDePasseTest {

	private static final List<String> MARIE = PolitiqueMotDePasse.interditsDe("marie.dupont@example.com", "Dupont",
			"Hélène");

	@Test
	void accepteUnMotDePasseConforme() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghij1!")).isEmpty();
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghij1!", MARIE)).isEmpty();
	}

	@Test
	void signaleChaqueRegleNonRespectee() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghi1!"))
				.containsExactly(PolitiqueMotDePasse.TROP_COURT);
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("abcdefghij1!")).containsExactly("Au moins une majuscule");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("ABCDEFGHIJ1!")).containsExactly("Au moins une minuscule");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghijk!")).containsExactly("Au moins un chiffre");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghijk1"))
				.containsExactly("Au moins un caractère spécial");
	}

	// Complément de l'US-09 : saisie et confirmation du mot de passe.
	@Test
	void confirmationIdentiqueAcceptee() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghij1!", "Abcdefghij1!", MARIE)).isEmpty();
	}

	@Test
	void confirmationDifferenteOuVideRefusee() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghij1!", "Abcdefghij1?", MARIE))
				.containsExactly("Les deux mots de passe ne correspondent pas");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghij1!", "", MARIE))
				.containsExactly("Veuillez confirmer votre mot de passe");
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Abcdefghij1!", null, MARIE))
				.containsExactly("Veuillez confirmer votre mot de passe");
	}

	@Test
	void reglesSignaleesQuelleQueSoitLaConfirmation() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Court1!", "autre", MARIE)).first()
				.isEqualTo(PolitiqueMotDePasse.TROP_COURT);
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Motdepasse123!", "", MARIE))
				.contains(PolitiqueMotDePasse.TROP_COURANT)
				.doesNotContain(PolitiqueMotDePasse.CONFIRMATION_VIDE);
	}

	// CU-14 : « Votre mot de passe doit contenir au moins 12 caractères »
	@Test
	void moinsDe12CaracteresRefuseAvecLeMessageDeCu14() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Court1!", MARIE)).first()
				.isEqualTo("Votre mot de passe doit contenir au moins 12 caractères");
	}

	// CU-14 : « Ce mot de passe est trop courant », sans tenir compte des majuscules.
	@ParameterizedTest
	@ValueSource(strings = { "Motdepasse123!", "MotDePasse123!", "Azertyuiop123!" })
	void motDePasseCourantRefuse(String courant) {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees(courant, MARIE))
				.containsExactly("Ce mot de passe est trop courant");
	}

	// CU-14 : « Votre mot de passe ne doit pas contenir votre e-mail ni votre nom »
	@ParameterizedTest
	@ValueSource(strings = { "Xx-Dupont-2026", "Xx-DUPONT-2026", "Ab1!marie.dupont@example.com", "Ab1!Marie.Dupont9",
			"Ab1!helene-2026x", "Ab1!HÉLÈNE-2026x" })
	void motDePasseContenantLEmailOuLeNomRefuse(String motDePasse) {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees(motDePasse, MARIE))
				.containsExactly("Votre mot de passe ne doit pas contenir votre e-mail ni votre nom");
	}

	@Test
	void nomTropCourtNonRecherche() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees("Lilas-Bleu-2026",
				PolitiqueMotDePasse.interditsDe(null, "Li", null))).isEmpty();
	}

	@Test
	void refuseUnMotDePasseAbsent() {
		assertThat(PolitiqueMotDePasse.reglesNonRespectees(null)).hasSize(5);
	}

}
