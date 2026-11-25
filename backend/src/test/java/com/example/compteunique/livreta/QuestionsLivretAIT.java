package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

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
import com.example.compteunique.livreta.LivretAController.Confirmation;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.ResultatControle;
import com.example.compteunique.livreta.QuestionsLivretAController.EtapeQuestions;
import com.example.compteunique.livreta.QuestionsLivretAController.Reponses;
import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;
import com.example.compteunique.profil.Consentements;

// CU-32 contre un vrai PostgreSQL (migration V30, registre des consentements de CU-10, comptes de test de
// db/demo) : refus sans rien enregistrer, réponses et acceptation enregistrées, texte non redemandé, aucune
// éligibilité sans questionnaire, questionnaire rattaché à la demande.
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class QuestionsLivretAIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final UUID CLIENTE_B = UUID.fromString("00000000-0000-4000-8000-00000000000b");

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
	private QuestionnaireLivretARepository questionnaires;

	@Autowired
	private Consentements consentements;

	private String identiteConfirmee(String identifiant) {
		String jeton = connexion.connecter(identifiant, MOT_DE_PASSE, null).jetonSession();
		Coordonnees coordonnees = livretA.identite(jeton).identite().coordonnees();
		ControleIdentiteVue controle = livretA.confirmerIdentite(jeton, new Confirmation(coordonnees, true)).getBody();
		livretA.resultatControle(jeton, controle.reference(),
				new ResultatControle(ResultatSanctions.AUCUNE_CORRESPONDANCE, 1));
		return jeton;
	}

	private String refus(String jeton, Reponses reponses) {
		return assertThrows(DemandeRefusee.class, () -> questions.repondre(jeton, reponses)).getMessage();
	}

	@Test
	void questionnaireEtTexteEnregistresPuisRattachesALaDemande() {
		String jeton = identiteConfirmee("TEST-CLIENTE-B");
		EtapeQuestions premiere = questions.etape(jeton);
		assertTrue(premiere.texteAAccepter());
		assertEquals(QuestionsLivretAService.VERSION_TEXTE, premiere.versionTexte());
		assertThat(premiere.texte()).contains("administration fiscale");
		assertNull(premiere.questionnaire());

		// Questionnaire incomplet, « Autre » sans précision, texte refusé : rien n'est enregistré.
		assertEquals(QuestionsLivretAService.OBJET_OBLIGATOIRE,
				refus(jeton, new Reponses(null, OrigineFonds.SALAIRE_REVENUS, null, true)));
		assertEquals(QuestionsLivretAService.ORIGINE_OBLIGATOIRE,
				refus(jeton, new Reponses(ObjetEpargne.PROJET, null, null, true)));
		assertEquals(QuestionsLivretAService.PRECISION_OBLIGATOIRE,
				refus(jeton, new Reponses(ObjetEpargne.PROJET, OrigineFonds.AUTRE, "  ", true)));
		assertEquals(QuestionsLivretAService.TEXTE_REFUSE,
				refus(jeton, new Reponses(ObjetEpargne.PROJET, OrigineFonds.EPARGNE_EXISTANTE, null, false)));
		assertThat(questionnaires.findFirstByCustomerIdOrderByReponduLeDescIdDesc(CLIENTE_B)).isEmpty();
		assertTrue(questions.etape(jeton).texteAAccepter());

		// Aucune donnée transmise à l'administration fiscale sans questionnaire.
		DemandeRefusee sansQuestionnaire = assertThrows(DemandeRefusee.class,
				() -> livretA.demander(jeton, new Eligibilite(true, true)));
		assertEquals(QuestionsLivretAService.SANS_QUESTIONNAIRE, sansQuestionnaire.getMessage());

		QuestionnaireVue rempli = questions
				.repondre(jeton, new Reponses(ObjetEpargne.AUTRE, OrigineFonds.AUTRE, "  Vente de parts sociales ", true))
				.getBody();
		assertEquals("Vente de parts sociales", rempli.precisionOrigine());
		assertTrue(consentements.aAccepte(CLIENTE_B, QuestionsLivretAService.TYPE_CONSENTEMENT,
				QuestionsLivretAService.VERSION_TEXTE));

		// Version en vigueur déjà acceptée : texte non redemandé, seul le questionnaire est posé.
		EtapeQuestions apres = questions.etape(jeton);
		assertFalse(apres.texteAAccepter());
		assertEquals(rempli.reference(), apres.questionnaire().reference());
		QuestionnaireVue corrige = questions
				.repondre(jeton, new Reponses(ObjetEpargne.PRECAUTION, OrigineFonds.SALAIRE_REVENUS, "ignorée", null))
				.getBody();
		assertNull(corrige.precisionOrigine());

		DemandeLivretAVue demande = livretA.demander(jeton, new Eligibilite(true, true)).getBody();
		assertThat(questionnaires.findByReference(corrige.reference())).hasValueSatisfying(q -> {
			assertEquals(demande.reference(), q.getDemandeReference());
			assertEquals(ObjetEpargne.PRECAUTION, q.getObjetEpargne());
			assertEquals(QuestionsLivretAService.VERSION_TEXTE, q.getVersionTexteInformation());
			assertThat(q.getReponduLe()).isNotNull();
		});
		// Un questionnaire ne sert qu'à une ouverture.
		assertNull(questions.etape(jeton).questionnaire());
	}

}
