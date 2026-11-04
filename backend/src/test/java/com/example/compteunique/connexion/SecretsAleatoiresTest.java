package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecretsAleatoiresTest {

	@Test
	void unJetonContient256BitsAleatoires() {
		String jeton = SecretsAleatoires.nouveauJeton();
		assertThat(Base64.getUrlDecoder().decode(jeton)).hasSize(32);
		assertThat(SecretsAleatoires.nouveauJeton()).isNotEqualTo(jeton);
	}

	@Test
	void uneEmpreinteEstStableEtDifferenteDuJeton() {
		String empreinte = SecretsAleatoires.empreinte("jeton");
		assertThat(empreinte).hasSize(64).isNotEqualTo("jeton").isEqualTo(SecretsAleatoires.empreinte("jeton"));
	}

	@Test
	void lesIdentifiantsSontAleatoires() {
		Set<String> identifiants = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			identifiants.add(SecretsAleatoires.nouvelIdentifiant());
		}
		assertThat(identifiants).hasSizeGreaterThan(990).allMatch(identifiant -> identifiant.matches("\\d{8}"));
	}

}
