package com.example.compteunique.virement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.virement.VirementController.Code;
import com.example.compteunique.virement.VirementController.Demande;
import com.example.compteunique.virement.VirementController.Refus;
import com.example.compteunique.virement.VirementService.Execution;
import com.example.compteunique.virement.VirementService.Recapitulatif;

class VirementControllerTest {

	private final VirementService service = mock(VirementService.class);
	private final VirementController controller = new VirementController(service);
	private final RefusBanqueTestController bouchon = new RefusBanqueTestController(service);

	@Test
	void laPreparationRepond202AvecLeRecapitulatif() {
		Recapitulatif recap = new Recapitulatif(21L, "Papa", "Test Cliente-B", "•••• 0202", 1250, "Loyer", "code");
		when(service.preparer("jeton", 8L, "12,50", "Loyer")).thenReturn(recap);

		ResponseEntity<Recapitulatif> reponse = controller.preparer("jeton", new Demande(8L, "12,50", "Loyer"));

		assertEquals(HttpStatus.ACCEPTED, reponse.getStatusCode());
		assertEquals(recap, reponse.getBody());
		controller.preparer("jeton", null);
		verify(service).preparer("jeton", null, null, null);
	}

	@Test
	void laSignatureRenvoieLeNouveauSolde() {
		Execution execution = new Execution("exécuté", 7000);
		when(service.signer("jeton", 21L, "123456")).thenReturn(execution);
		when(service.signer("jeton", 21L, null)).thenReturn(execution);

		assertEquals(execution, controller.signer("jeton", 21L, new Code("123456")));
		assertEquals(execution, controller.signer("jeton", 21L, null));
	}

	@Test
	void lesRefusDonnentLeMessageEtLeCode() {
		ResponseEntity<Refus> reponse = controller.refus(new DemandeRefusee(HttpStatus.GONE, "Code expiré", true));

		assertEquals(HttpStatus.GONE, reponse.getStatusCode());
		assertEquals(new Refus("Code expiré", true), reponse.getBody());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.nonConnectee().getStatusCode());
		assertEquals(new Refus(VirementController.SANS_COMPTE, true), controller.sansCompte().getBody());
	}

	@Test
	void leBouchonDeRefusDeLaBanqueTransmetLeMotif() {
		Execution execution = new Execution("recrédité", 10000);
		when(service.refuserParBanque(21L, "AC04")).thenReturn(execution);
		when(service.refuserParBanque(21L, null)).thenReturn(execution);

		assertEquals(execution, bouchon.refuser(21L, new RefusBanqueTestController.Motif("AC04")));
		assertEquals(execution, bouchon.refuser(21L, null));
		ResponseEntity<RefusBanqueTestController.Refus> refus = bouchon
				.refus(new DemandeRefusee(HttpStatus.CONFLICT, "non exécuté"));
		assertEquals(HttpStatus.CONFLICT, refus.getStatusCode());
		assertEquals("non exécuté", refus.getBody().message());
	}

}
