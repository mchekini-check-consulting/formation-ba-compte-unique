package com.example.compteunique.beneficiaire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.beneficiaire.VerificationTestController.Appel;
import com.example.compteunique.beneficiaire.VerificationTestController.Demande;
import com.example.compteunique.beneficiaire.VerificationTestController.Etat;
import com.example.compteunique.beneficiaire.VerificationTestController.Refus;
import com.example.compteunique.beneficiaire.VerificationTestController.Selecteur;

class VerificationTestControllerTest {

	private static final String IBAN = "FR7699999000019900000000105";

	private final VerificationBeneficiaire verification = mock(VerificationBeneficiaire.class);
	private final VerificationTestController controller = new VerificationTestController(verification);

	@Test
	void laVerificationEstFaiteSurLIbanNormalise() {
		Verification resultat = new Verification(ResultatVerification.CORRESPOND, "Test Cliente-A");
		when(verification.verifier(IBAN, "Test Cliente-A")).thenReturn(resultat);

		ResponseEntity<Object> reponse = controller.verifier(new Demande("fr76 9999 9000 0199 0000 0000 105", "Test Cliente-A"));

		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(resultat, reponse.getBody());
	}

	@Test
	void unIbanInvalideEstRefuseSansAppel() {
		ResponseEntity<Object> reponse = controller.verifier(new Demande("FR00 1234", "x"));

		assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
		assertEquals(new Refus(VerificationTestController.IBAN_INVALIDE), reponse.getBody());
		assertEquals(HttpStatus.BAD_REQUEST, controller.verifier(null).getStatusCode());
		verify(verification, never()).verifier(anyString(), anyString());
	}

	@Test
	void lEtatDonneLeSelecteurEtLeJournal() {
		OffsetDateTime date = OffsetDateTime.parse("2026-11-11T10:00:00Z");
		when(verification.estIndisponible()).thenReturn(true);
		when(verification.journal())
				.thenReturn(List.of(new AppelVerification(date, IBAN, ResultatVerification.INDISPONIBLE)));

		assertEquals(new Etat(true, List.of(new Appel(date, IBAN, ResultatVerification.INDISPONIBLE)),
				VerificationBeneficiaire.COMPTES_EXTERNES), controller.selecteur(new Selecteur(true)));
		verify(verification).rendreIndisponible(true);
	}

	@Test
	void sansCorpsLeSelecteurRevientADisponible() {
		controller.selecteur(null);

		verify(verification).rendreIndisponible(false);
	}

}
