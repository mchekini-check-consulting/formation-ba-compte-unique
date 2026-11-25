package com.example.compteunique.alertes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.messagerie.Messagerie;

// CU-382 (TECH-14) : alertes à l'équipe, mécanisme commun à tous les traitements en échec. Chaque alerte envoie un
// e-mail à ALERTE_EQUIPE_EMAIL et ajoute une ligne au journal (date métier, origine, message, e-mail envoyé ou non).
class AlertesEquipeTest {

	private static final ZonedDateTime MAINTENANT = ZonedDateTime.of(2026, 11, 24, 2, 0, 0, 0, DateMetier.PARIS);
	private static final String ADRESSE = "alertes-equipe@hitechbank.test";

	private final AlerteRepository journal = mock(AlerteRepository.class);
	private final Messagerie messagerie = mock(Messagerie.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final List<Alerte> base = new ArrayList<>();

	@BeforeEach
	void baseEnMemoire() {
		when(dateMetier.maintenant()).thenReturn(MAINTENANT);
		when(journal.save(any())).thenAnswer(appel -> {
			base.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(journal.findTop50ByOrderByIdDesc()).thenAnswer(appel -> base.reversed());
	}

	private AlertesEquipe alertes(String adresse) {
		return new AlertesEquipe(journal, messagerie, dateMetier, adresse);
	}

	@Test
	@DisplayName("Critère 11 : étant donné un batch du catalogue en échec, quand il s'arrête, alors un e-mail est "
			+ "envoyé à ALERTE_EQUIPE_EMAIL et la ligne « Batch catalogue en échec : fichier absent » est au journal")
	void alerteEnvoyeeEtJournalisee() {
		when(messagerie.envoyerEmail(anyString(), anyString(), anyString())).thenReturn(true);

		alertes(ADRESSE).signaler("Batch catalogue", "Batch catalogue en échec : fichier absent");

		verify(messagerie).envoyerEmail(ADRESSE, "Alerte Hi-Tech Bank : Batch catalogue",
				"Batch catalogue en échec : fichier absent");
		assertThat(base).singleElement().satisfies(alerte -> {
			assertThat(alerte.getDateMetier()).isEqualTo(MAINTENANT.toOffsetDateTime());
			assertThat(alerte.getOrigine()).isEqualTo("Batch catalogue");
			assertThat(alerte.getMessage()).isEqualTo("Batch catalogue en échec : fichier absent");
			assertThat(alerte.isEmailEnvoye()).isTrue();
		});
	}

	@Test
	@DisplayName("E-mail non remis (pas de fournisseur, erreur d'envoi) : l'alerte est quand même au journal, avec "
			+ "« e-mail non envoyé » ; une panne de la messagerie n'empêche jamais l'alerte")
	void emailNonRemis() {
		when(messagerie.envoyerEmail(anyString(), anyString(), anyString())).thenReturn(false)
				.thenThrow(new IllegalStateException("messagerie indisponible"));

		alertes(ADRESSE).signaler("Topic d'erreurs", "premier");
		alertes(ADRESSE).signaler("Topic d'erreurs", "second");

		assertThat(base).extracting(Alerte::isEmailEnvoye).containsExactly(false, false);
	}

	@Test
	@DisplayName("Adresse de l'équipe pas encore déclarée (à confirmer) : pas d'envoi, l'alerte est au journal")
	void adresseAbsente() {
		alertes(" ").signaler("Batch catalogue", "Batch catalogue en échec : fichier absent");
		alertes(null).signaler("Batch catalogue", "Batch catalogue en échec : fichier absent");

		verify(messagerie, never()).envoyerEmail(anyString(), anyString(), anyString());
		assertThat(base).hasSize(2).noneMatch(Alerte::isEmailEnvoye);
	}

	@Test
	@DisplayName("Un message trop long est raccourci pour tenir dans le journal")
	void messageRaccourci() {
		alertes(ADRESSE).signaler("Batch catalogue", "x".repeat(600));

		assertThat(base.getFirst().getMessage()).hasSize(Alerte.MESSAGE_LONGUEUR_MAX);
	}

	@Test
	@DisplayName("Page de test : journal des alertes, de la plus récente à la plus ancienne (heure de Paris)")
	void routeDeTest() {
		AlertesEquipe alertes = alertes(ADRESSE);
		alertes.signaler("Batch catalogue", "premier");
		alertes.signaler("Topic d'erreurs", "second");

		List<AlertesTestController.AlerteVue> vues = new AlertesTestController(alertes).journal();

		assertThat(vues).extracting(AlertesTestController.AlerteVue::origine).containsExactly("Topic d'erreurs",
				"Batch catalogue");
		assertThat(vues.getFirst().dateMetier()).isEqualTo("2026-11-24T02:00:00+01:00");
		assertThat(vues.getFirst().emailEnvoye()).isFalse();
	}

}
