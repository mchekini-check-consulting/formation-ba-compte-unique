package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.ResultatInterrogation;

// CU-31 contre un vrai PostgreSQL (migration V22, comptes de test de db/demo) : demande enregistrée, réponses
// de l'administration fiscale, expiration simulée du délai et contrôle interne de multidétention.
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class LivretAIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private LivretAController livretA;

	@Autowired
	private LivretATestController test;

	@Autowired
	private DemandeLivretARepository demandes;

	private String jeton(String identifiant) {
		return connexion.connecter(identifiant, MOT_DE_PASSE, null).jetonSession();
	}

	@Test
	void duSansReponseALOuvertureValideePuisPlusDeSecondeDemande() {
		String clienteA = jeton("TEST-CLIENTE-A");
		assertNull(livretA.etat(clienteA).demande());

		DemandeRefusee sansAttestation = assertThrows(DemandeRefusee.class,
				() -> livretA.demander(clienteA, new Eligibilite(false, true)));
		assertEquals(LivretAService.ATTESTATION_OBLIGATOIRE, sansAttestation.getMessage());

		DemandeLivretAVue creee = livretA.demander(clienteA, new Eligibilite(true, false)).getBody();
		assertEquals(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION, creee.statut());
		assertThat(creee.transmission().nom()).isNotBlank();
		assertThat(creee.attestationLe()).isNotNull();

		DemandeLivretAVue sansReponse = livretA.reponseFiscale(clienteA, creee.reference(),
				new ResultatInterrogation("interro-it", ReponseFiscale.SANS_REPONSE));
		assertEquals(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION, sansReponse.statut());
		assertThat(sansReponse.interrogationLe()).isNotNull();

		DemandeLivretAVue enRetard = test.expirerDelai(creee.reference());
		assertThat(enRetard.retard()).isTrue();
		assertThat(livretA.etat(clienteA).demande().message()).isEqualTo(DemandeLivretAVue.RETARD);

		DemandeRefusee doublon = assertThrows(DemandeRefusee.class,
				() -> livretA.demander(clienteA, new Eligibilite(true, true)));
		assertEquals(LivretAService.DEMANDE_EN_COURS, doublon.getMessage());

		DemandeLivretAVue validee = livretA.reponseFiscale(clienteA, creee.reference(),
				new ResultatInterrogation("interro-it-2", ReponseFiscale.AUCUN_LIVRET_A));
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, validee.statut());
		assertThat(demandes.findByReference(creee.reference())).hasValueSatisfying(demande -> {
			assertEquals("interro-it", demande.getInterrogationId());
			assertEquals(ReponseFiscale.AUCUN_LIVRET_A, demande.getReponseFiscale());
			assertThat(demande.getConseillerAlerteLe()).isNotNull();
		});

		DemandeRefusee dejaDetenu = assertThrows(DemandeRefusee.class,
				() -> livretA.demander(clienteA, new Eligibilite(true, true)));
		assertEquals(HttpStatus.CONFLICT, dejaDetenu.getStatut());
		assertEquals(LivretAService.LIVRET_A_DETENU, dejaDetenu.getMessage());
	}

	@Test
	void livretAExistantAilleursNOuvrePasEtResteVisibleDeLaSeuleCliente() {
		String clienteB = jeton("TEST-CLIENTE-B");
		DemandeLivretAVue creee = livretA.demander(clienteB, new Eligibilite(true, true)).getBody();
		DemandeLivretAVue existant = livretA.reponseFiscale(clienteB, creee.reference(),
				new ResultatInterrogation("interro-b", ReponseFiscale.LIVRET_A_EXISTANT));
		assertEquals(StatutDemandeLivretA.LIVRET_A_EXISTANT, existant.statut());
		assertNull(existant.transmission());

		String clienteA = jeton("TEST-CLIENTE-A");
		DemandeRefusee autreCliente = assertThrows(DemandeRefusee.class, () -> livretA.reponseFiscale(clienteA,
				creee.reference(), new ResultatInterrogation("x", ReponseFiscale.AUCUN_LIVRET_A)));
		assertEquals(HttpStatus.NOT_FOUND, autreCliente.getStatut());
	}

}
