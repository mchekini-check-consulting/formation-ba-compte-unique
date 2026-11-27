package com.example.compteunique.fonctionnalites;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.example.compteunique.securite.SessionObligatoire;

// Lot 3 : interrupteurs des fonctionnalités, par environnement (helm.yaml).
class FonctionnalitesTest {

	@Test
	@DisplayName("Nom et état de chaque interrupteur, triés ; « true » active la fonctionnalité, le reste la laisse en « Bientôt »")
	void etats() {
		MockEnvironment environnement = new MockEnvironment()
				.withProperty("app.fonctionnalites.simulateur-per", "false")
				.withProperty("app.fonctionnalites.outils-test", "true")
				.withProperty("app.fonctionnalites.messagerie", "false");

		var routes = new FonctionnalitesController(new Fonctionnalites(environnement));

		assertThat(routes.etats()).containsExactly(java.util.Map.entry("messagerie", false),
				java.util.Map.entry("outils-test", true), java.util.Map.entry("simulateur-per", false));
	}

	@Test
	@DisplayName("Aucun interrupteur déclaré : liste vide ; la route est lisible sans connexion")
	void aucunEtRoutePublique() {
		assertThat(new Fonctionnalites(new MockEnvironment()).etats()).isEmpty();
		assertThat(SessionObligatoire.ROUTES_PUBLIQUES).contains("/fonctionnalites");
	}

}
