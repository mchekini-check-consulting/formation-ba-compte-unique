package com.example.compteunique.beneficiaire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.beneficiaire.BeneficiaireController.BeneficiaireVue;
import com.example.compteunique.beneficiaire.BeneficiaireController.Code;
import com.example.compteunique.beneficiaire.BeneficiaireController.DemandeAjout;
import com.example.compteunique.beneficiaire.BeneficiaireController.Libelle;
import com.example.compteunique.beneficiaire.BeneficiaireController.Refus;
import com.example.compteunique.beneficiaire.BeneficiaireService.AjoutEnAttente;

class BeneficiaireControllerTest {

	private static final OffsetDateTime DATE = OffsetDateTime.parse("2026-11-11T10:00:00Z");

	private final BeneficiaireService service = mock(BeneficiaireService.class);
	private final BeneficiaireController controller = new BeneficiaireController(service);

	private static Beneficiaire beneficiaire() {
		AjoutBeneficiaire ajout = new AjoutBeneficiaire(UUID.randomUUID(), "FR7699999000019900000000202",
				new Verification(ResultatVerification.CORRESPOND, "Test Cliente-B"), "Papa", "x$y", DATE);
		return new Beneficiaire(ajout, DATE);
	}

	@Test
	void critere11_laListeMasqueLIbanEtIndiqueVerifie() {
		when(service.lister("jeton")).thenReturn(List.of(beneficiaire()));

		assertEquals(List.of(new BeneficiaireVue(null, "Papa", "Test Cliente-B", "•••• 0202", DATE, "vérifié")),
				controller.lister("jeton"));
	}

	@Test
	void lAjoutRepond202AvecLaDemandeEnAttente() {
		AjoutEnAttente attente = new AjoutEnAttente(11L, "Test Cliente-B", "code envoyé");
		when(service.demanderAjout("jeton", "iban", "nom", "Papa")).thenReturn(attente);

		ResponseEntity<AjoutEnAttente> reponse = controller.ajouter("jeton", new DemandeAjout("iban", "nom", "Papa"));

		assertEquals(HttpStatus.ACCEPTED, reponse.getStatusCode());
		assertEquals(attente, reponse.getBody());
		controller.ajouter("jeton", null);
		verify(service).demanderAjout("jeton", null, null, null);
	}

	@Test
	void laConfirmationRepond201AvecLeBeneficiaire() {
		when(service.confirmerAjout("jeton", 11L, "123456")).thenReturn(beneficiaire());

		ResponseEntity<BeneficiaireVue> reponse = controller.confirmer("jeton", 11L, new Code("123456"));

		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals("Papa", reponse.getBody().libelle());
		when(service.confirmerAjout("jeton", 11L, null)).thenReturn(beneficiaire());
		assertEquals(HttpStatus.CREATED, controller.confirmer("jeton", 11L, null).getStatusCode());
	}

	@Test
	void leLibelleEstModifieEtLaSuppressionRepond204() {
		when(service.renommer("jeton", 5L, "Maman")).thenReturn(beneficiaire());
		when(service.renommer("jeton", 5L, null)).thenReturn(beneficiaire());

		assertEquals("Papa", controller.renommer("jeton", 5L, new Libelle("Maman")).libelle());
		assertEquals("Papa", controller.renommer("jeton", 5L, null).libelle());
		assertEquals(HttpStatus.NO_CONTENT, controller.supprimer("jeton", 5L).getStatusCode());
		verify(service).supprimer("jeton", 5L);
	}

	@Test
	void lesRefusDonnentLeMessageEtLeCode() {
		ResponseEntity<Refus> reponse = controller
				.refus(new DemandeRefusee(HttpStatus.BAD_REQUEST, "Code incorrect. Après trois...", true));

		assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
		assertEquals(new Refus("Code incorrect. Après trois...", true), reponse.getBody());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.nonConnectee().getStatusCode());
		assertEquals(new Refus(BeneficiaireController.SANS_COMPTE, true), controller.sansCompte().getBody());
	}

}
