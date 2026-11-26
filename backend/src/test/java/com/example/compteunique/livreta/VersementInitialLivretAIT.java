package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.livreta.LivretAController.Confirmation;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.ResultatControle;
import com.example.compteunique.livreta.LivretAController.ResultatInterrogation;
import com.example.compteunique.livreta.VersementInitialLivretAController.VersementInitial;

// CU-33 contre un vrai PostgreSQL (migration V35, comptes de test de db/demo : TEST-CLIENTE-A, solde 1 240,50 €) :
// versement refusé avant l'ouverture validée, sous le minimum ou au-delà du solde ; montant enregistré dans la
// demande avec le compte courant à débiter, sans débit ni Livret A créé.
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class VersementInitialLivretAIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final UUID CLIENTE_A = UUID.fromString("00000000-0000-4000-8000-00000000000a");

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private LivretAController livretA;

	@Autowired
	private QuestionsLivretAController questions;

	@Autowired
	private VersementInitialLivretAController versements;

	@Autowired
	private DemandeLivretARepository demandes;

	@Autowired
	private CompteRepository comptes;

	private String refus(String jeton, String reference, String montant) {
		return assertThrows(DemandeRefusee.class,
				() -> versements.choisir(jeton, reference, new VersementInitial(montant))).getMessage();
	}

	@Test
	void montantEnregistreApresLOuvertureValideeSansDebit() {
		String jeton = connexion.connecter("TEST-CLIENTE-A", MOT_DE_PASSE, null).jetonSession();
		Coordonnees coordonnees = livretA.identite(jeton).identite().coordonnees();
		ControleIdentiteVue controle = livretA.confirmerIdentite(jeton, new Confirmation(coordonnees, true)).getBody();
		livretA.resultatControle(jeton, controle.reference(),
				new ResultatControle(ResultatSanctions.AUCUNE_CORRESPONDANCE, 1));
		QuestionnaireDeTest.rempli(questions, jeton);
		String reference = livretA.demander(jeton, new Eligibilite(true, true)).getBody().reference();

		// Prérequis : ouverture validée par l'administration fiscale.
		assertEquals(VersementInitialLivretAService.HORS_ETAPE, refus(jeton, reference, "150"));
		livretA.reponseFiscale(jeton, reference, new ResultatInterrogation("interro", ReponseFiscale.AUCUN_LIVRET_A));

		Compte avant = comptes.findByCustomerId(CLIENTE_A).getFirst();
		assertEquals(VersementInitialLivretAService.MINIMUM, refus(jeton, reference, "9,99"));
		assertEquals(VersementInitialLivretAService.PLAFOND, refus(jeton, reference, "22950,01"));
		assertEquals(VersementInitialLivretAService.MONTANT_INVALIDE, refus(jeton, reference, "-5"));
		assertEquals(VersementInitialLivretAService.SOLDE_INSUFFISANT, refus(jeton, reference, "1240,51"));
		assertThat(demandes.findByReference(reference))
				.hasValueSatisfying(d -> assertNull(d.getVersementInitialCentimes()));

		DemandeLivretAVue vue = versements.choisir(jeton, reference, new VersementInitial("150,50"));
		assertEquals(15_050L, vue.versementInitialCentimes());
		assertEquals(DemandeLivretAVue.VERSEMENT_ENREGISTRE.formatted("150,50 €"), vue.message());
		assertThat(demandes.findByReference(reference)).hasValueSatisfying(d -> {
			assertEquals(15_050L, d.getVersementInitialCentimes());
			assertEquals(avant.getId(), d.getCompteDebitId());
			assertThat(d.getVersementSaisiLe()).isNotNull();
			assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, d.getStatut());
		});
		// Débit à la signature (CU-34) : le compte courant n'est pas débité.
		assertEquals(avant.getSoldeCentimes(), comptes.findById(avant.getId()).orElseThrow().getSoldeCentimes());
		// La demande relue par l'écran garde le montant.
		assertEquals(15_050L, livretA.etat(jeton).demande().versementInitialCentimes());
	}

}
