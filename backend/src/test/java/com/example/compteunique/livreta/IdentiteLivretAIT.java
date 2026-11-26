package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.LivretAController.Confirmation;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.ResultatControle;

// CU-28 contre un vrai PostgreSQL (migration V23, comptes de test de db/demo) : identité pré-remplie,
// coordonnées enregistrées dans le dossier, gel des avoirs bloquant, PPE validée par un responsable.
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration,classpath:db/demo",
		"app.test.livret-a-simule=true" })
@Testcontainers
class IdentiteLivretAIT {

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
	private DossierRepository dossiers;

	@Autowired
	private ControleIdentiteLivretARepository controles;

	@Autowired
	private QuestionsLivretAController questions;

	private String jeton(String identifiant) {
		return connexion.connecter(identifiant, MOT_DE_PASSE, null).jetonSession();
	}

	@Test
	void coordonneesCorrigeesPuisPPEValideeParUnResponsable() {
		String clienteA = jeton("TEST-CLIENTE-A");
		LivretAController.EtapeIdentite etape = livretA.identite(clienteA);
		assertEquals("Cliente-A", etape.identite().nom());
		assertEquals("France", etape.identite().residenceFiscale());

		Coordonnees corrigees = new Coordonnees("8 rue Neuve", null, "75002", "Paris", "France",
				"nouvelle-a@example.test", "0611223344");
		ControleIdentiteVue controle = livretA.confirmerIdentite(clienteA, new Confirmation(corrigees, true)).getBody();
		assertEquals(StatutControleIdentite.EN_COURS, controle.statut());
		assertEquals("Cliente-A", controle.aControler().nomNaissance());
		assertEquals("FRANCE", controle.aControler().nationalite());
		assertThat(dossiers.findByReference("demo-cliente-a")).hasValueSatisfying(dossier -> {
			assertEquals("8 rue Neuve", dossier.getVoie());
			assertEquals("nouvelle-a@example.test", dossier.getEmail());
		});

		ControleIdentiteVue ppe = livretA.resultatControle(clienteA, controle.reference(),
				new ResultatControle(ResultatSanctions.CORRESPONDANCE_PEP, 2));
		assertEquals(StatutControleIdentite.ATTENTE_RESPONSABLE, ppe.statut());
		DemandeRefusee bloquee = assertThrows(DemandeRefusee.class,
				() -> livretA.demander(clienteA, new Eligibilite(true, true)));
		assertEquals(IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE, bloquee.getMessage());

		// CU-32 : les questions viennent après l'identité confirmée.
		DemandeRefusee questionsAvantIdentite = assertThrows(DemandeRefusee.class,
				() -> QuestionnaireExemple.rempli(questions, clienteA));
		assertEquals(IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE, questionsAvantIdentite.getMessage());

		assertEquals(StatutControleIdentite.VALIDE, test.validerParResponsable(controle.reference()).statut());
		QuestionnaireExemple.rempli(questions, clienteA);
		DemandeLivretAVue demande = livretA.demander(clienteA, new Eligibilite(true, true)).getBody();
		assertThat(controles.findByReference(controle.reference())).hasValueSatisfying(c -> {
			assertEquals(demande.reference(), c.getDemandeReference());
			assertEquals(2, c.getTentatives());
			assertThat(c.getCoordonneesModifieesLe()).isNotNull();
			assertThat(c.getResponsableValideLe()).isNotNull();
		});
	}

	@Test
	void gelDesAvoirsBloqueLaDemarche() {
		String clienteB = jeton("TEST-CLIENTE-B");
		Coordonnees coordonnees = livretA.identite(clienteB).identite().coordonnees();
		ControleIdentiteVue controle = livretA.confirmerIdentite(clienteB, new Confirmation(coordonnees, true)).getBody();
		ControleIdentiteVue bloque = livretA.resultatControle(clienteB, controle.reference(),
				new ResultatControle(ResultatSanctions.CORRESPONDANCE_SANCTIONS, 1));
		assertEquals(StatutControleIdentite.BLOQUE, bloque.statut());
		assertEquals(ControleIdentiteVue.BLOQUE, livretA.identite(clienteB).controle().message());
		assertThat(controles.findByReference(controle.reference()))
				.hasValueSatisfying(c -> assertThat(c.getAlerteConformiteLe()).isNotNull());

		DemandeRefusee relance = assertThrows(DemandeRefusee.class,
				() -> livretA.confirmerIdentite(clienteB, new Confirmation(coordonnees, true)));
		assertEquals(IdentiteLivretAService.DEMARCHE_SUSPENDUE, relance.getMessage());
	}

}
