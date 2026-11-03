package com.example.bouchonsidentite.selfie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Messages;
import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;

// CUB-64 : un cas de test par critère d'acceptation.
class VerificationSelfieServiceTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("Europe/Paris"));

	private final VerificationSelfieRepository verifications = mock(VerificationSelfieRepository.class);
	private final VerificationSelfieService service = new VerificationSelfieService(verifications, HORLOGE);

	@BeforeEach
	void enregistrementSimule() {
		when(verifications.save(any(VerificationSelfie.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private VerificationSelfieReponse verifier(String selfie) {
		return service.verifier(new VerificationSelfieRequete("dossier-1", selfie));
	}

	private VerificationSelfie enregistree() {
		ArgumentCaptor<VerificationSelfie> capture = ArgumentCaptor.forClass(VerificationSelfie.class);
		verify(verifications).save(capture.capture());
		return capture.getValue();
	}

	@Test
	void selfieConformeCorrespondAvecSonScore() {
		VerificationSelfieReponse reponse = verifier("SELFIE_CONFORME");
		assertEquals(ResultatSelfie.CORRESPONDANCE, reponse.resultat());
		assertEquals(96, reponse.score());
		assertEquals(Suite.POURSUIVRE, reponse.suite());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statutDossier());
		assertNull(reponse.message());

		VerificationSelfie enregistree = enregistree();
		assertEquals("dossier-1", enregistree.getDossierId());
		assertEquals(ResultatSelfie.CORRESPONDANCE, enregistree.getResultat());
		assertEquals(96, enregistree.getScore());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, enregistree.getStatutDossier());
		assertEquals(OffsetDateTime.now(HORLOGE), enregistree.getVerifieLe());
	}

	@Test
	void scoreDeQuatreVingtDixPourCentCorrespond() {
		assertEquals(ResultatSelfie.CORRESPONDANCE, verifier("SCORE_90").resultat());
		assertEquals(ResultatSelfie.CORRESPONDANCE, verifier("SCORE_100").resultat());
	}

	@Test
	void scoreInferieurANonanteAnnuleLeDossier() {
		VerificationSelfieReponse reponse = verifier("SCORE_89");
		assertEquals(ResultatSelfie.NON_CORRESPONDANCE, reponse.resultat());
		assertEquals(89, reponse.score());
		assertEquals(Suite.ANNULER, reponse.suite());
		assertEquals(StatutDossier.ANNULE, reponse.statutDossier());
		assertEquals(Messages.OUVERTURE_ANNULEE, reponse.message());
	}

	@Test
	void selfieDifferentAnnuleLeDossier() {
		VerificationSelfieReponse reponse = verifier("SELFIE_DIFFERENT");
		assertEquals(ResultatSelfie.NON_CORRESPONDANCE, reponse.resultat());
		assertEquals(42, reponse.score());
		assertEquals(StatutDossier.ANNULE, enregistree().getStatutDossier());
	}

	@Test
	void photoDEcranEchoueALaVerificationDePresence() {
		VerificationSelfieReponse reponse = verifier("PHOTO_ECRAN");
		assertEquals(ResultatSelfie.ECHEC_VERIFICATION_PRESENCE, reponse.resultat());
		assertNull(reponse.score());
		assertEquals(StatutDossier.ANNULE, reponse.statutDossier());
	}

	@Test
	void serviceIndisponibleApresDeuxRelances() {
		VerificationSelfieReponse reponse = verifier("SERVICE_INDISPONIBLE");
		assertEquals(ResultatSelfie.INDISPONIBILITE_TECHNIQUE, reponse.resultat());
		assertEquals(3, reponse.tentatives());
		assertEquals(Suite.REESSAYER_PLUS_TARD, reponse.suite());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statutDossier());
		assertEquals("Vérification impossible, réessayez plus tard", reponse.message());
	}

	@ParameterizedTest
	@ValueSource(strings = { "SCORE_101", "SCORE_", "SCORE_ABC", "SELFIE_FLOU" })
	void selfieDeTestInconnuRefuse(String selfie) {
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> verifier(selfie)).getStatusCode());
		verify(verifications, never()).save(any(VerificationSelfie.class));
	}

	@Test
	void champsObligatoires() {
		assertThrows(ResponseStatusException.class,
				() -> service.verifier(new VerificationSelfieRequete(null, "SELFIE_CONFORME")));
		assertThrows(ResponseStatusException.class, () -> service.verifier(new VerificationSelfieRequete("d", " ")));
	}

}
