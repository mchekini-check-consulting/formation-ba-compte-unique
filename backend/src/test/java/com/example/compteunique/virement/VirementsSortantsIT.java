package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
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
import com.example.compteunique.compte.CompteController.MouvementVue;
import com.example.compteunique.compte.CompteNonActifException;
import com.example.compteunique.compte.SoldeInsuffisantException;
import com.example.compteunique.connexion.ConnexionService;

// CU-144 contre un vrai PostgreSQL (migration V18, comptes de test de db/demo).
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class VirementsSortantsIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final String IBAN_A = "FR7699999000019900000000105";
	private static final String IBAN_B = "FR7699999000019900000000202";
	private static final String IBAN_CLOTURE = "FR7699999000019900000000396";
	private static final String IBAN_BENEFICIAIRE = "DE89370400440532013000";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private EmissionVirement emission;

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private CompteController comptes;

	@Autowired
	private JdbcTemplate base;

	private long solde(String iban) {
		return base.queryForObject("select solde_centimes from compte where iban = ?", Long.class, iban);
	}

	private static VirementSortant virement(String iban, long montant) {
		return new VirementSortant(iban, montant, "Beneficiaire Test", IBAN_BENEFICIAIRE, "Virement de test", true);
	}

	@Test
	void unVirementSimuleDebiteLeCompteEtApparaitDansLaRubriqueVersements() {
		long avant = solde(IBAN_A);

		assertEquals(avant - 1250, emission.emettre(virement(IBAN_A, 1250)));

		assertEquals(avant - 1250, solde(IBAN_A));
		String jeton = connexion.connecter("TEST-CLIENTE-A", MOT_DE_PASSE, null).jetonSession();
		Long id = base.queryForObject("select id from compte where iban = ?", Long.class, IBAN_A);
		MouvementVue dernier = comptes.versements(jeton, id.toString()).mouvements().get(0);
		assertThat(dernier.sortant()).isTrue();
		assertThat(dernier.simule()).isTrue();
		assertEquals("Beneficiaire Test", dernier.beneficiaire());
		assertEquals("Virement de test", dernier.libelle());
		assertEquals(1250L, dernier.montantCentimes());
		assertEquals(IBAN_BENEFICIAIRE, base.queryForObject("select iban_beneficiaire from mouvement where type ="
				+ " 'VIREMENT_SORTANT' order by id desc limit 1", String.class));
	}

	@Test
	void unMontantSuperieurAuSoldeOuUnCompteClotureNeChangentRien() {
		assertThrows(SoldeInsuffisantException.class, () -> emission.emettre(virement(IBAN_B, 1)));
		assertThrows(CompteNonActifException.class, () -> emission.emettre(virement(IBAN_CLOTURE, 1)));

		assertEquals(0L, solde(IBAN_B));
		assertEquals(0L, solde(IBAN_CLOTURE));
	}

	@Test
	void desVirementsSimultanesNeRendentJamaisLeSoldeNegatif() throws Exception {
		long avant = solde(IBAN_A);
		long montant = avant / 3 + 1;
		Callable<Long> emettre = () -> emission.emettre(virement(IBAN_A, montant));

		ExecutorService pool = Executors.newFixedThreadPool(4);
		int refuses = 0;
		try {
			for (Future<Long> resultat : pool.invokeAll(List.of(emettre, emettre, emettre, emettre))) {
				try {
					resultat.get();
				} catch (ExecutionException refus) {
					assertThat(refus.getCause()).isInstanceOf(SoldeInsuffisantException.class);
					refuses++;
				}
			}
		} finally {
			pool.shutdown();
		}

		assertEquals(2, refuses);
		assertEquals(avant - 2 * montant, solde(IBAN_A));
		assertThat(solde(IBAN_A)).isNotNegative();
	}

	@Test
	void laBaseRefuseUnVirementSortantSansBeneficiaire() {
		assertThrows(Exception.class, () -> base.update("insert into mouvement (compte_id, type, montant_centimes,"
				+ " libelle, effectue_le) select id, 'VIREMENT_SORTANT', 100, 'x', now() from compte where iban = ?",
				IBAN_A));
	}

}
