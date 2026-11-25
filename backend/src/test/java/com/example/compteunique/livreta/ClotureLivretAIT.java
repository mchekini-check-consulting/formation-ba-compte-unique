package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.livreta.CertificatClotureLivretA.Decision;
import com.example.compteunique.livreta.LivretAController.ChoixSuite;
import com.example.compteunique.livreta.LivretAController.Confirmation;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.ResultatControle;
import com.example.compteunique.livreta.LivretAController.ResultatInterrogation;
import com.example.compteunique.livreta.LivretATestController.RefusCertificat;

// CU-296 contre un vrai PostgreSQL (migration V28, comptes de test de db/demo) : détail enregistré, certificat
// déposé (fichier en base), refusé puis validé ; renonciation puis nouvelle demande ; date métier avancée au-delà
// de la date limite -> demande annulée.
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class ClotureLivretAIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final DetailLivretA DETAIL = new DetailLivretA("Banque Exemple", "Agence Paris Centre",
			LocalDate.of(2015, 3, 2));

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
	private CertificatClotureLivretARepository certificats;

	@Autowired
	private DemandeLivretARepository demandes;

	@Autowired
	private DateMetier dateMetier;

	@Autowired
	private QuestionsLivretAController questions;

	private String jeton(String identifiant) {
		return connexion.connecter(identifiant, MOT_DE_PASSE, null).jetonSession();
	}

	/**
	 * Identité confirmée, questionnaire rempli (CU-32), demande transmise et réponse « Livret A existant » (avec le
	 * détail si partagé).
	 */
	private DemandeLivretAVue livretAExistant(String jeton, boolean partage) {
		Coordonnees coordonnees = livretA.identite(jeton).identite().coordonnees();
		ControleIdentiteVue controle = livretA.confirmerIdentite(jeton, new Confirmation(coordonnees, true)).getBody();
		livretA.resultatControle(jeton, controle.reference(),
				new ResultatControle(ResultatSanctions.AUCUNE_CORRESPONDANCE, 1));
		QuestionnaireDeTest.rempli(questions, jeton);
		DemandeLivretAVue creee = livretA.demander(jeton, new Eligibilite(true, partage)).getBody();
		return livretA.reponseFiscale(jeton, creee.reference(),
				new ResultatInterrogation("interro", ReponseFiscale.LIVRET_A_EXISTANT, partage ? DETAIL : null));
	}

	private static MockMultipartFile pdf(String nom) {
		return new MockMultipartFile("fichier", nom, "application/pdf", new byte[] { 37, 80, 68, 70 });
	}

	@Test
	void clotureParLaClienteCertificatRefusePuisValide() throws Exception {
		String cliente = jeton("TEST-CLIENTE-A");
		DemandeLivretAVue existant = livretAExistant(cliente, true);
		assertEquals(DETAIL, existant.detail());
		String reference = existant.reference();

		DemandeLivretAVue enAttente = livretA.choisirSuite(cliente, reference,
				new ChoixSuite(SuiteLivretAExistant.CLOTURE_PAR_CLIENTE));
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, enAttente.statut());
		assertThat(enAttente.dateLimiteCertificat()).isAfter(LocalDate.now().plusMonths(2));

		DemandeLivretAVue aVerifier = livretA.deposerCertificat(cliente, reference, pdf("certificat.pdf")).getBody();
		assertEquals(StatutDemandeLivretA.CERTIFICAT_A_VERIFIER, aVerifier.statut());
		assertEquals("certificat.pdf", aVerifier.certificat().nomFichier());

		DemandeLivretAVue refuse = test.refuserCertificat(reference, new RefusCertificat("Document illisible"));
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, refuse.statut());
		assertThat(refuse.message()).contains("Document illisible");

		livretA.deposerCertificat(cliente, reference, pdf("nouveau.pdf"));
		DemandeLivretAVue validee = test.validerCertificat(reference);
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, validee.statut());
		assertEquals(DemandeLivretAVue.CERTIFICAT_VALIDE, livretA.etat(cliente).demande().message());

		Long demandeId = demandes.findByReference(reference).orElseThrow().getId();
		assertThat(certificats.findAll()).filteredOn(c -> c.getDemandeId().equals(demandeId)).hasSize(2)
				.extracting(CertificatClotureLivretA::getDecision)
				.containsExactlyInAnyOrder(Decision.REFUSE, Decision.VALIDE);
		assertThat(certificats.findFirstByDemandeIdOrderByIdDesc(demandeId).orElseThrow().getFichier())
				.containsExactly(37, 80, 68, 70);
	}

	@Test
	void renonciationPuisNouvelleDemandeAcceptee() {
		String cliente = jeton("TEST-CLIENTE-B");
		DemandeLivretAVue existant = livretAExistant(cliente, false);
		assertNull(existant.detail());

		DemandeLivretAVue renoncee = livretA.choisirSuite(cliente, existant.reference(),
				new ChoixSuite(SuiteLivretAExistant.RENONCIATION));
		assertEquals(StatutDemandeLivretA.RENONCEE, renoncee.statut());

		// Nouvelle demande acceptée après la renonciation ; cette fois la cliente choisit de clôturer.
		DemandeLivretAVue nouvelle = livretAExistant(cliente, false);
		assertThat(nouvelle.reference()).isNotEqualTo(existant.reference());
		DemandeLivretAVue enAttente = livretA.choisirSuite(cliente, nouvelle.reference(),
				new ChoixSuite(SuiteLivretAExistant.CLOTURE_PAR_CLIENTE));

		// Date métier avancée au-delà de la date limite : demande annulée dès l'affichage, plus de dépôt.
		try {
			dateMetier.changer(LocalDateTime.of(enAttente.dateLimiteCertificat().plusDays(1), LocalTime.NOON),
					"test");
			assertEquals(StatutDemandeLivretA.ANNULEE, livretA.etat(cliente).demande().statut());
			DemandeRefusee trop = assertThrows(DemandeRefusee.class,
					() -> livretA.deposerCertificat(cliente, nouvelle.reference(), pdf("tard.pdf")));
			assertEquals(ClotureLivretAService.DATE_LIMITE_DEPASSEE, trop.getMessage());
		} finally {
			dateMetier.revenirALaDateReelle("test");
		}

		// Une demande annulée ne bloque plus.
		assertThat(livretAExistant(cliente, false).reference()).isNotEqualTo(nouvelle.reference());
	}

}
