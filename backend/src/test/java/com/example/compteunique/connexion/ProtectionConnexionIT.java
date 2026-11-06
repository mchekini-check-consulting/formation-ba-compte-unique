package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.securite.PurgeJournalSecurite;

// CU-16 et CU-48 contre un vrai PostgreSQL : les refus gardent compteurs et journal (pas
// d'annulation de la transaction), blocage par identifiant puis par adresse IP, purge du journal.
// Chaque test utilise sa propre adresse IP et ses propres identifiants : la base est partagée.
@SpringBootTest
@Testcontainers
class ProtectionConnexionIT {

	private static final String CONFORME = "Abcdefghij1!";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private IdentifiantConnexionService identifiants;

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private PurgeJournalSecurite purge;

	@Autowired
	private JdbcTemplate base;

	private String clienteActive() {
		IdentifiantConnexion compte = identifiants.creer();
		identifiants.definirMotDePasse(compte.getId(), CONFORME);
		return compte.getIdentifiant();
	}

	private int evenements(String evenement, String identifiant) {
		return base.queryForObject("select count(*) from journal_securite where evenement = ? and identifiant_saisi = ?",
				Integer.class, evenement, identifiant);
	}

	@Test
	void troisErreursBloquentLIdentifiantMemeAvecLeBonMotDePassePuisLeBlocageSeTermine() {
		String identifiant = clienteActive();
		String ip = "198.51.100.1";
		assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter(identifiant, "mauvais1", ip));
		assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter(identifiant, "mauvais2", ip));
		TropDeTentativesException blocage = assertThrows(TropDeTentativesException.class,
				() -> connexion.connecter(identifiant, "mauvais3", ip));
		assertThat(blocage.getMessage()).isEqualTo("Trop de tentatives. Réessayez dans 5 minutes.");

		assertThrows(TropDeTentativesException.class, () -> connexion.connecter(identifiant, CONFORME, ip));

		assertThat(evenements("CONNEXION_ECHOUEE", identifiant)).isEqualTo(4);
		assertThat(evenements("BLOCAGE_IDENTIFIANT", identifiant)).isEqualTo(1);
		// Jamais de mot de passe dans le journal.
		assertThat(base.queryForObject("select count(*) from journal_securite where identifiant_saisi = ? "
				+ "and (motif like '%mauvais%' or identifiant_saisi like '%mauvais%')", Integer.class, identifiant))
				.isZero();

		// Fin du blocage (horloge simulée en avançant la date de fin) : connexion et compteur à zéro.
		base.update("update blocage_identifiant set bloque_jusqu_au = now() - interval '1 second' "
				+ "where identifiant = ?", identifiant);
		assertThat(connexion.connecter(identifiant, CONFORME, ip).identifiant()).isEqualTo(identifiant);
		assertThat(base.queryForObject("select count(*) from blocage_identifiant where identifiant = ?", Integer.class,
				identifiant)).isZero();
		assertThat(evenements("CONNEXION_REUSSIE", identifiant)).isEqualTo(1);
	}

	@Test
	void unIdentifiantInconnuEstBloqueCommeUnIdentifiantExistant() {
		String ip = "198.51.100.2";
		assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter("00000001", "x", ip));
		assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter("00000001", "x", ip));
		assertThrows(TropDeTentativesException.class, () -> connexion.connecter("00000001", "x", ip));
	}

	@Test
	void uneTentativeIncompleteNAugmenteAucunCompteur() {
		assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter("1234", "x", "198.51.100.3"));
		assertThat(base.queryForObject("select count(*) from blocage_identifiant where identifiant = '1234'",
				Integer.class)).isZero();
		assertThat(base.queryForObject("select count(*) from journal_securite where adresse_ip = '198.51.100.3'",
				Integer.class)).isZero();
	}

	@Test
	void vingtEchecsDUneAdresseBloquentLaVingtEtUniemeTentativeSansBloquerLesAutresAdresses() {
		String bonne = clienteActive();
		String ip = "198.51.100.4";
		// L'adresse IP est lue par le journal dans la demande HTTP en cours.
		MockHttpServletRequest requete = new MockHttpServletRequest();
		requete.setRemoteAddr(ip);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));
		try {
			for (int i = 0; i < 20; i++) {
				String inconnu = String.format("9%07d", i);
				assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter(inconnu, "x", ip));
			}

			TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
					() -> connexion.connecter(bonne, CONFORME, ip));
			assertThat(refus.getMessage()).isEqualTo("Trop de tentatives depuis votre connexion. Réessayez plus tard.");
			assertThrows(TropDeTentativesException.class, () -> connexion.connecter(bonne, CONFORME, ip));

			// Une autre adresse n'est pas affectée.
			assertThat(connexion.connecter(bonne, CONFORME, "198.51.100.5").identifiant()).isEqualTo(bonne);

			// Fin du blocage et échecs sortis de la fenêtre de 15 minutes : accès rétabli.
			base.update("update blocage_adresse_ip set bloque_jusqu_au = now() - interval '1 second' "
					+ "where adresse_ip = ?", ip);
			base.update("update journal_securite set survenu_le = survenu_le - interval '16 minutes' "
					+ "where adresse_ip = ?", ip);
			assertThat(connexion.connecter(bonne, CONFORME, ip).identifiant()).isEqualTo(bonne);
		} finally {
			RequestContextHolder.resetRequestAttributes();
		}
		assertThat(base.queryForObject("select count(*) from journal_securite where evenement = 'BLOCAGE_ADRESSE_IP' "
				+ "and adresse_ip = ?", Integer.class, ip)).isEqualTo(1);
	}

	@Test
	void laPurgeSupprimeLesEvenementsDePlusDeSixMois() {
		base.update("insert into journal_securite (evenement, identifiant_saisi, resultat, survenu_le) "
				+ "values ('CONNEXION_REUSSIE', 'ANCIEN01', 'REUSSITE', now() - interval '7 months')");

		assertThat(purge.purger()).isPositive();

		assertThat(base.queryForObject("select count(*) from journal_securite where identifiant_saisi = 'ANCIEN01'",
				Integer.class)).isZero();
	}

}
