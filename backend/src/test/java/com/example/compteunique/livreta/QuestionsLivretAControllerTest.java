package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.livreta.LivretAController.Refus;
import com.example.compteunique.livreta.QuestionsLivretAController.EtapeQuestions;
import com.example.compteunique.livreta.QuestionsLivretAController.Reponses;
import com.example.compteunique.livreta.QuestionsLivretAService.Etape;
import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;

// CU-32 : routes /livret-a/questions et refus affichés à la cliente.
class QuestionsLivretAControllerTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 25, 10, 0, 0, 0, ZoneOffset.UTC);

	private final QuestionsLivretAService service = mock(QuestionsLivretAService.class);
	private final QuestionsLivretAController controleur = new QuestionsLivretAController(service);

	private static QuestionnaireLivretA questionnaire() {
		return new QuestionnaireLivretA(7L, UUID.randomUUID(), ObjetEpargne.AUTRE, OrigineFonds.AUTRE, "Loterie",
				"V1", MAINTENANT);
	}

	@Test
	void etapeSansPuisAvecQuestionnaire() {
		when(service.etat("j")).thenReturn(new Etape("V1", "texte", true, null));
		EtapeQuestions vide = controleur.etape("j");
		assertEquals("V1", vide.versionTexte());
		assertEquals("texte", vide.texte());
		assertTrue(vide.texteAAccepter());
		assertNull(vide.questionnaire());

		QuestionnaireLivretA rempli = questionnaire();
		when(service.etat("j")).thenReturn(new Etape("V1", "texte", false, rempli));
		QuestionnaireVue vue = controleur.etape("j").questionnaire();
		assertEquals(rempli.getReference(), vue.reference());
		assertEquals(ObjetEpargne.AUTRE, vue.objetEpargne());
		assertEquals(OrigineFonds.AUTRE, vue.origineFonds());
		assertEquals("Loterie", vue.precisionOrigine());
		assertEquals(MAINTENANT, vue.reponduLe());
		assertFalse(controleur.etape("j").texteAAccepter());
	}

	@Test
	void reponsesEnregistreesEn201() {
		when(service.repondre("j", ObjetEpargne.AUTRE, OrigineFonds.AUTRE, "Loterie", true)).thenReturn(questionnaire());
		ResponseEntity<QuestionnaireVue> reponse = controleur.repondre("j",
				new Reponses(ObjetEpargne.AUTRE, OrigineFonds.AUTRE, "Loterie", true));
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals("Loterie", reponse.getBody().precisionOrigine());
	}

	@Test
	void refusAvecLeMessageAfficheALaCliente() {
		when(service.repondre("j", null, null, null, null))
				.thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, QuestionsLivretAService.OBJET_OBLIGATOIRE));
		ResponseEntity<Refus> refus = controleur
				.refus(assertThrows(DemandeRefusee.class, () -> controleur.repondre("j", null)));
		assertEquals(HttpStatus.BAD_REQUEST, refus.getStatusCode());
		assertEquals(QuestionsLivretAService.OBJET_OBLIGATOIRE, refus.getBody().message());
		assertEquals(HttpStatus.UNAUTHORIZED, controleur.refus(new ClienteNonConnecteeException()).getStatusCode());
	}

}
