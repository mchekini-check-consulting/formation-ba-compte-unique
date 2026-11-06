package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Identifiants, jetons et sessions contre un vrai PostgreSQL (migrations Flyway comprises).
@SpringBootTest
@Testcontainers
class ConnexionIT {

	private static final String CONFORME = "Abcdefghij1!";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private IdentifiantConnexionService identifiants;

	@Autowired
	private JetonService jetons;

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private JdbcTemplate base;

	@Test
	void parcoursComplet() {
		IdentifiantConnexion compte = identifiants.creer();
		Long id = compte.getId();

		assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter(compte.getIdentifiant(), CONFORME, null));

		String premier = jetons.generer(id, TypeJeton.ACTIVATION);
		String second = jetons.generer(id, TypeJeton.ACTIVATION);
		assertThrows(JetonInvalideException.class, () -> jetons.utiliser(premier, TypeJeton.ACTIVATION));
		assertThat(jetons.utiliser(second, TypeJeton.ACTIVATION)).isEqualTo(id);
		assertThrows(JetonInvalideException.class, () -> jetons.utiliser(second, TypeJeton.ACTIVATION));

		identifiants.definirMotDePasse(id, CONFORME);
		base.update("update identifiant_connexion set compteur_erreurs = 2 where id = ?", id);

		ResultatConnexion resultat = connexion.connecter(compte.getIdentifiant(), CONFORME, null);

		assertThat(connexion.identifiantDeLaSession(resultat.jetonSession())).contains(compte.getIdentifiant());
		assertThat(base.queryForObject("select compteur_erreurs from identifiant_connexion where id = ?",
				Integer.class, id)).isZero();
		assertThat(base.queryForObject("select mot_de_passe_hache from identifiant_connexion where id = ?",
				String.class, id)).startsWith("$argon2id$");
		assertThat(base.queryForObject("select count(*) from session_client where jeton_hache = ?", Integer.class,
				resultat.jetonSession())).isZero();
	}

	// CU-17 : activité, expiration et déconnexion enregistrées en base.
	@Test
	void activiteExpirationEtDeconnexion() {
		IdentifiantConnexion compte = identifiants.creer();
		identifiants.definirMotDePasse(compte.getId(), CONFORME);
		String expiree = connexion.connecter(compte.getIdentifiant(), CONFORME, null).jetonSession();
		String deconnectee = connexion.connecter(compte.getIdentifiant(), CONFORME, null).jetonSession();
		String empreinteExpiree = SecretsAleatoires.empreinte(expiree);

		base.update("update session_client set derniere_activite_le = now() - interval '9 minutes' where jeton_hache = ?",
				empreinteExpiree);
		assertThat(connexion.activite(expiree)).isPresent();
		assertThat(base.queryForObject(
				"select derniere_activite_le > now() - interval '1 minute' from session_client where jeton_hache = ?",
				Boolean.class, empreinteExpiree)).isTrue();

		base.update("update session_client set derniere_activite_le = now() - interval '10 minutes' where jeton_hache = ?",
				empreinteExpiree);
		assertThat(connexion.idDeLaSession(expiree)).isEmpty();
		assertThat(base.queryForObject("select revoquee_le is not null from session_client where jeton_hache = ?",
				Boolean.class, empreinteExpiree)).isTrue();

		connexion.deconnecter(deconnectee);
		assertThat(connexion.etatSession(deconnectee)).isEmpty();
		assertThat(base.queryForObject("select revoquee_le is not null from session_client where jeton_hache = ?",
				Boolean.class, SecretsAleatoires.empreinte(deconnectee))).isTrue();
	}

	@Test
	void lesComptesDeTestNeSontPasDansLesMigrations() {
		assertThat(base.queryForObject("select count(*) from identifiant_connexion where identifiant like 'TEST%'",
				Integer.class)).isZero();
	}

}
