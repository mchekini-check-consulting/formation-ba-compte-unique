package com.example.compteunique.compte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.connexion.ConnexionService;

// CU-20 contre un vrai PostgreSQL, avec les clientes de test A et B de db/demo : le refus
// doit rester écrit au journal de sécurité, et les données de test doivent être valides.
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class AccesComptesIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final String JOURNAL_DU_COMPTE =
			"select customer_id::text as customer_id, evenement from journal_securite where compte_demande = ?";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private CompteService comptes;

	@Autowired
	private JdbcTemplate base;

	private String seConnecter(String identifiant) {
		return connexion.connecter(identifiant, MOT_DE_PASSE).jetonSession();
	}

	private Compte seulCompte(String jeton) {
		List<Compte> liste = comptes.mesComptes(jeton);
		assertThat(liste).hasSize(1);
		return liste.get(0);
	}

	@Test
	void chaqueClienteNAccedeQuASonCompte() {
		String jetonA = seConnecter("TEST-CLIENTE-A");
		Compte compteA = seulCompte(jetonA);
		Compte compteB = seulCompte(seConnecter("TEST-CLIENTE-B"));

		assertThat(compteA.getId()).isNotEqualTo(compteB.getId());
		assertThat(Iban.estValide(compteA.getIban())).isTrue();
		assertThat(Iban.estValide(compteB.getIban())).isTrue();
		assertEquals(compteA.getId(), comptes.consulter(jetonA, compteA.getId().toString()).getId());

		String demande = compteB.getId().toString();
		assertThrows(CompteInaccessibleException.class, () -> comptes.consulter(jetonA, demande));

		Map<String, Object> evenement = base.queryForMap(JOURNAL_DU_COMPTE, demande);
		assertEquals(compteA.getCustomerId().toString(), evenement.get("customer_id"));
		assertEquals(EvenementSecurite.ACCES_COMPTE_REFUSE, evenement.get("evenement"));
	}

	@Test
	void leSoldeEstLuEnCentimesSelonLEtatDuCompte() {
		Compte compteA = seulCompte(seConnecter("TEST-CLIENTE-A"));
		assertThat(compteA.getSoldeAffichable()).contains(124050L);
		assertThat(compteA.isSansVersement()).isFalse();

		Compte compteCloture = seulCompte(seConnecter("TEST-CLIENTE-CLOTURE"));
		assertThat(compteCloture.isCloture()).isTrue();
		assertThat(compteCloture.getSoldeAffichable()).contains(0L);

		assertThat(seulCompte(seConnecter("TEST-CLIENTE-NEGATIF")).getSoldeAffichable()).isEmpty();
	}

	@Test
	void unVersementEnBaseEstVisibleDesLaLectureSuivante() {
		String jeton = seConnecter("TEST-CLIENTE-B");
		Compte avant = seulCompte(jeton);
		assertThat(avant.getSoldeAffichable()).contains(0L);
		assertThat(avant.isSansVersement()).isTrue();

		base.update("update compte set solde_centimes = 5000, solde_mis_a_jour_le = now() where id = ?", avant.getId());

		Compte apres = seulCompte(jeton);
		assertThat(apres.getSoldeAffichable()).contains(5000L);
		assertThat(apres.isSansVersement()).isFalse();
	}

	@Test
	void unCompteInexistantEstRefuseEtEnregistre() {
		String jeton = seConnecter("TEST-CLIENTE-A");

		assertThrows(CompteInaccessibleException.class, () -> comptes.consulter(jeton, "999999"));

		assertThat(base.queryForList(JOURNAL_DU_COMPTE, "999999")).hasSize(1);
	}

	@Test
	void uneClienteSansCompteVoitUneListeVideEtSonRefusEstEnregistre() {
		String jeton = seConnecter("TEST-ACTIF");

		assertThat(comptes.mesComptes(jeton)).isEmpty();
		assertThrows(CompteInaccessibleException.class, () -> comptes.consulter(jeton, "888888"));

		assertThat(base.queryForMap(JOURNAL_DU_COMPTE, "888888").get("customer_id")).isNull();
	}

}
