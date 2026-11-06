package com.example.compteunique.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.securite.JournalTestController.LigneJournal;

// CU-167 : consultation du journal de sécurité, en environnement de test seulement.
class ConsultationJournalTest {

	private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-06T10:00:00Z");

	private final JournalSecuriteRepository journal = mock(JournalSecuriteRepository.class);

	private static EvenementSecurite echec(String identifiant) {
		EvenementSecurite evenement = EvenementSecurite.authentification(EvenementSecurite.CONNEXION_ECHOUEE,
				identifiant, 7L, "MOT_DE_PASSE_INCORRECT", T0);
		evenement.depuis("203.0.113.7");
		return evenement;
	}

	@Test
	void enTestLesDerniersEvenementsSontRenvoyes() {
		when(journal.findTop100ByOrderByIdDesc()).thenReturn(List.of(echec("12345678")));
		JournalTestController controller = new JournalTestController(new ConsultationJournal(journal, true));

		var reponse = controller.derniers(null);

		assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(reponse.getBody()).containsExactly(new LigneJournal(null, T0, "CONNEXION_ECHOUEE", "ECHEC",
				"MOT_DE_PASSE_INCORRECT", "12345678", 7L, "203.0.113.7", null));
	}

	@Test
	void unIdentifiantFiltreLesEvenements() {
		when(journal.findTop100ByIdentifiantSaisiOrderByIdDesc("12345678")).thenReturn(List.of(echec("12345678")));
		ConsultationJournal consultation = new ConsultationJournal(journal, true);

		assertThat(consultation.derniers(" 12345678 ")).hasValueSatisfying(liste -> assertThat(liste).hasSize(1));
		when(journal.findTop100ByOrderByIdDesc()).thenReturn(List.of());
		assertThat(consultation.derniers("  ")).hasValue(List.of());
	}

	@Test
	void horsEnvironnementDeTestRienNEstLuEtLaReponseEst404() {
		JournalTestController controller = new JournalTestController(new ConsultationJournal(journal, false));

		assertThat(controller.derniers("12345678").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verifyNoInteractions(journal);
	}

}
