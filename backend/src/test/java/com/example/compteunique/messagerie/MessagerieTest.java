package com.example.compteunique.messagerie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.example.compteunique.messagerie.MessagesTestController.MessageCapture;

// CU-87 : un cas de test par critère d'acceptation.
class MessagerieTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneId.of("Europe/Paris"));

	private final MessageTestRepository messages = mock(MessageTestRepository.class);

	private MessageTest enregistre() {
		ArgumentCaptor<MessageTest> capture = ArgumentCaptor.forClass(MessageTest.class);
		verify(messages).save(capture.capture());
		return capture.getValue();
	}

	@Test
	void emailDeTestCaptureEnBasePourSonCanal() {
		boolean remis = new Messagerie(messages, true, HORLOGE).envoyerEmail("marie@example.com", "Objet", "Texte");
		assertThat(remis).isTrue();
		MessageTest message = enregistre();
		assertThat(message.getCanal()).isEqualTo(Canal.EMAIL);
		assertThat(message.getDestinataire()).isEqualTo("marie@example.com");
		assertThat(message.getObjet()).isEqualTo("Objet");
		assertThat(message.getTexte()).isEqualTo("Texte");
		assertThat(message.getEnvoyeLe()).isEqualTo(OffsetDateTime.now(HORLOGE));
	}

	@Test
	void smsDeTestCaptureEnBasePourSonCanal() {
		assertThat(new Messagerie(messages, true, HORLOGE).envoyerSms("0612345678", "Texte")).isTrue();
		MessageTest message = enregistre();
		assertThat(message.getCanal()).isEqualTo(Canal.SMS);
		assertThat(message.getObjet()).isNull();
	}

	@Test
	void horsTestRienNEstEnregistreEtLEnvoiEchoue() {
		Messagerie messagerie = new Messagerie(messages, false);
		assertThat(messagerie.envoyerSms("0612345678", "Texte")).isFalse();
		assertThat(messagerie.envoyerEmail("a@b.fr", "o", "t")).isFalse();
		assertThat(messagerie.dernier(Canal.SMS)).isEmpty();
		verify(messages, never()).save(any());
		verify(messages, never()).findById(any());
	}

	@Test
	void pageSmsAfficheLeDernierMessageEtSonLienCliquable() {
		MessageTest sms = new MessageTest(Canal.SMS, "0612345678", null,
				"Activez votre espace : http://localhost:4200/activation?jeton=abc (valable 24 h)",
				OffsetDateTime.now(HORLOGE));
		when(messages.findById(Canal.SMS)).thenReturn(Optional.of(sms));
		var reponse = new MessagesTestController(new Messagerie(messages, true, HORLOGE)).dernierSms();
		MessageCapture capture = reponse.getBody();
		assertThat(capture).isNotNull();
		assertThat(capture.lien()).isEqualTo("http://localhost:4200/activation?jeton=abc");
		assertThat(capture.destinataire()).isEqualTo("0612345678");
	}

	@Test
	void pageEmailSansLienEtSansMessage() {
		MessageTest email = new MessageTest(Canal.EMAIL, "a@b.fr", "Objet", "Votre identifiant : 12345678",
				OffsetDateTime.now(HORLOGE));
		when(messages.findById(Canal.EMAIL)).thenReturn(Optional.of(email), Optional.empty());
		MessagesTestController controleur = new MessagesTestController(new Messagerie(messages, true, HORLOGE));
		MessageCapture capture = controleur.dernierEmail().getBody();
		assertThat(capture).isNotNull();
		assertThat(capture.lien()).isNull();
		assertThat(controleur.dernierEmail().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
