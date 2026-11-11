package com.example.compteunique.beneficiaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.beneficiaire.CodesSms.Saisie;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.messagerie.Messagerie;

class CodesSmsTest {

	private static final OffsetDateTime ENVOI = OffsetDateTime.parse("2026-11-11T10:00:00+01:00");

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final Messagerie messagerie = mock(Messagerie.class);
	private final CodesSms codes = new CodesSms(dossiers, messagerie);
	private final Compte compte = new Compte(3L, UUID.randomUUID(), Iban.de("99000000001"), 7L, ENVOI);

	private void dossierAvecMobile(String mobile) {
		Dossier dossier = new Dossier("ref");
		ReflectionTestUtils.setField(dossier, "mobile", mobile);
		when(dossiers.findById(3L)).thenReturn(Optional.of(dossier));
	}

	@Test
	void leCodeASixChiffresEstEnvoyeAuMobileDuDossierEtSeulSonEmpreinteEstRendue() {
		dossierAvecMobile("0600000000");
		when(messagerie.envoyerSms(eq("0600000000"), anyString())).thenReturn(true);

		String empreinte = codes.envoyer(compte, code -> "Votre code : " + code);

		ArgumentCaptor<String> texte = ArgumentCaptor.forClass(String.class);
		verify(messagerie).envoyerSms(eq("0600000000"), texte.capture());
		Matcher code = Pattern.compile("(\\d{6})$").matcher(texte.getValue());
		assertThat(code.find()).isTrue();
		assertThat(empreinte).doesNotContain(code.group(1));
		assertThat(CodesSms.correspond(empreinte, code.group(1))).isTrue();
	}

	@Test
	void unSmsQuiNePartPasEstRefuse() {
		dossierAvecMobile("0600000000");
		when(messagerie.envoyerSms(anyString(), anyString())).thenReturn(false);

		DemandeRefusee refus = assertThrows(DemandeRefusee.class, () -> codes.envoyer(compte, code -> code));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, refus.getStatut());
		assertEquals(CodesSms.ENVOI_IMPOSSIBLE, refus.getMessage());
	}

	@Test
	void sansMobileAucunSmsNEstEnvoye() {
		dossierAvecMobile(" ");

		assertThrows(DemandeRefusee.class, () -> codes.envoyer(compte, code -> code));

		verify(messagerie, never()).envoyerSms(anyString(), anyString());
	}

	@Test
	void uneEmpreinteNeCorrespondQuAuBonCode() {
		String empreinte = CodesSms.hacher("123456");

		assertThat(CodesSms.correspond(empreinte, " 123456 ")).isTrue();
		assertThat(CodesSms.correspond(empreinte, "123457")).isFalse();
		assertThat(CodesSms.correspond(empreinte, "12345")).isFalse();
		assertThat(CodesSms.correspond(empreinte, null)).isFalse();
		assertThat(CodesSms.correspond(null, "123456")).isFalse();
		assertThat(CodesSms.correspond("sans-separateur", "123456")).isFalse();
		assertThat(CodesSms.hacher("123456")).isNotEqualTo(empreinte);
	}

	@Test
	void uneSaisieEstCorrecteIncorrecteDerniereOuExpiree() {
		String empreinte = CodesSms.hacher("123456");
		OffsetDateTime dansLesCinqMinutes = ENVOI.plusMinutes(5);

		assertEquals(Saisie.CORRECT, CodesSms.controler(empreinte, ENVOI, 0, "123456", dansLesCinqMinutes));
		assertEquals(Saisie.INCORRECT, CodesSms.controler(empreinte, ENVOI, 1, "000000", dansLesCinqMinutes));
		assertEquals(Saisie.INCORRECT_DERNIER, CodesSms.controler(empreinte, ENVOI, 2, "000000", dansLesCinqMinutes));
		assertEquals(Saisie.EXPIRE, CodesSms.controler(empreinte, ENVOI, 0, "123456", ENVOI.plusMinutes(5).plusSeconds(1)));
	}

}
