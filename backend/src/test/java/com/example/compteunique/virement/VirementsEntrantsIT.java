package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.compte.CompteController;
import com.example.compteunique.compte.CompteController.RubriqueVersements;
import com.example.compteunique.compte.MontantInvalideException;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.virement.ReceptionVirement.Resultat;

// CU-11 et CU-143 contre un vrai PostgreSQL (migration V17, comptes de test de db/demo).
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class VirementsEntrantsIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final String IBAN_A = "FR7699999000019900000000105";
	private static final String IBAN_CLOTURE = "FR7699999000019900000000396";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ReceptionVirement reception;

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private CompteController comptes;

	@Autowired
	private JdbcTemplate base;

	private long solde(String iban) {
		return base.queryForObject("select solde_centimes from compte where iban = ?", Long.class, iban);
	}

	private int lignes(String table, String iban) {
		return base.queryForObject("select count(*) from " + table + " t join compte c on c.id = t.compte_id"
				+ " where c.iban = ?", Integer.class, iban);
	}

	private static VirementEntrant virement(String iban, long montant) {
		return new VirementEntrant(iban, montant, "Emetteur Test", "Versement de test", true);
	}

	@Test
	void unVirementSimuleCrediteLeCompteEtApparaitDansLaRubriqueVersements() {
		long avant = solde(IBAN_A);

		assertEquals(Resultat.CREDITE, reception.recevoir(virement(IBAN_A, 1250)));

		assertEquals(avant + 1250, solde(IBAN_A));
		assertThat(base.queryForObject("select bool_and(simule) from mouvement m join compte c on c.id = m.compte_id"
				+ " where c.iban = ?", Boolean.class, IBAN_A)).isTrue();

		String jeton = connexion.connecter("TEST-CLIENTE-A", MOT_DE_PASSE, null).jetonSession();
		Long id = base.queryForObject("select id from compte where iban = ?", Long.class, IBAN_A);
		RubriqueVersements rubrique = comptes.versements(jeton, id.toString());
		assertEquals("FR76 9999 9000 0199 0000 0000 105", rubrique.iban());
		assertThat(rubrique.mouvements()).isNotEmpty();
		assertEquals("Emetteur Test", rubrique.mouvements().get(0).emetteur());
	}

	@Test
	void desVirementsSimultanesSAdditionnentTous() throws Exception {
		long avant = solde(IBAN_A);
		Callable<Resultat> recevoir = () -> reception.recevoir(virement(IBAN_A, 100));

		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			for (Future<Resultat> resultat : pool.invokeAll(List.of(recevoir, recevoir, recevoir, recevoir))) {
				resultat.get();
			}
		} finally {
			pool.shutdown();
		}

		assertEquals(avant + 400, solde(IBAN_A));
	}

	@Test
	void unVirementSurUnCompteClotureEstRenvoyeEtEnregistreSansChangerLeSolde() {
		assertEquals(Resultat.RENVOYE, reception.recevoir(virement(IBAN_CLOTURE, 1250)));

		assertEquals(0L, solde(IBAN_CLOTURE));
		assertEquals(0, lignes("mouvement", IBAN_CLOTURE));
		assertThat(lignes("retour_virement", IBAN_CLOTURE)).isPositive();
		assertEquals("AC04", base.queryForObject("select motif from retour_virement r join compte c"
				+ " on c.id = r.compte_id where c.iban = ? limit 1", String.class, IBAN_CLOTURE));
	}

	@Test
	void unIbanInconnuOuUnMontantInvalideNeChangentRien() {
		long avant = solde(IBAN_A);

		assertThrows(CompteIntrouvableException.class,
				() -> reception.recevoir(virement("FR7600000000000000000000000", 1250)));
		assertThrows(MontantInvalideException.class, () -> reception.recevoir(virement(IBAN_A, 0)));

		assertEquals(avant, solde(IBAN_A));
	}

	@Test
	void laBaseRefuseUnMouvementNulMemeEcritDirectement() {
		assertThrows(Exception.class, () -> base.update("insert into mouvement (compte_id, type, montant_centimes,"
				+ " libelle, emetteur, effectue_le) select id, 'VIREMENT_ENTRANT', 0, 'x', 'y', now() from compte"
				+ " where iban = ?", IBAN_A));
	}

}
