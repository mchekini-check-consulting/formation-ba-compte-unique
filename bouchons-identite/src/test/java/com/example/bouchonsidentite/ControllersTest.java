package com.example.bouchonsidentite;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.bouchonsidentite.document.SaisieIdentite;
import com.example.bouchonsidentite.document.VerificationDocumentController;
import com.example.bouchonsidentite.document.VerificationDocumentReponse;
import com.example.bouchonsidentite.document.VerificationDocumentRequete;
import com.example.bouchonsidentite.document.VerificationDocumentService;
import com.example.bouchonsidentite.sanctions.ControleRequete;
import com.example.bouchonsidentite.sanctions.ControleReponse;
import com.example.bouchonsidentite.sanctions.ControleSanctionsController;
import com.example.bouchonsidentite.sanctions.ControleSanctionsService;
import com.example.bouchonsidentite.selfie.VerificationSelfieController;
import com.example.bouchonsidentite.selfie.VerificationSelfieReponse;
import com.example.bouchonsidentite.selfie.VerificationSelfieRequete;
import com.example.bouchonsidentite.selfie.VerificationSelfieService;

// Les contrôleurs ne font que transmettre au service.
class ControllersTest {

	@Test
	void sanctionsDelegueAuService() {
		ControleSanctionsService service = mock(ControleSanctionsService.class);
		ControleRequete requete = new ControleRequete("Conforme", "Test", LocalDate.of(1980, 1, 1), "Française");
		ControleReponse reponse = mock(ControleReponse.class);
		when(service.controler(requete)).thenReturn(reponse);
		assertSame(reponse, new ControleSanctionsController(service).controler(requete));
	}

	@Test
	void documentDelegueAuService() {
		VerificationDocumentService service = mock(VerificationDocumentService.class);
		VerificationDocumentRequete requete = new VerificationDocumentRequete("d", "PIECE_VALIDE", "PIECE_VALIDE",
				new SaisieIdentite("Dupont", "Marie Claire", LocalDate.of(1990, 5, 14), null));
		VerificationDocumentReponse reponse = mock(VerificationDocumentReponse.class);
		when(service.verifier(requete)).thenReturn(reponse);
		assertSame(reponse, new VerificationDocumentController(service).verifier(requete));
	}

	@Test
	void selfieDelegueAuService() {
		VerificationSelfieService service = mock(VerificationSelfieService.class);
		VerificationSelfieRequete requete = new VerificationSelfieRequete("d", "SELFIE_CONFORME");
		VerificationSelfieReponse reponse = mock(VerificationSelfieReponse.class);
		when(service.verifier(requete)).thenReturn(reponse);
		assertSame(reponse, new VerificationSelfieController(service).verifier(requete));
	}

}
