package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.virement.VirementSortantTestController.Demande;
import com.example.compteunique.virement.VirementSortantTestController.Refus;
import com.example.compteunique.virement.VirementSortantTestController.Reponse;

class VirementSortantTestControllerTest {

	private final EmissionVirement emission = mock(EmissionVirement.class);
	private final VirementSortantTestController controller = new VirementSortantTestController(emission);

	private VirementSortant virementTransmis() {
		ArgumentCaptor<VirementSortant> virement = ArgumentCaptor.forClass(VirementSortant.class);
		verify(emission).emettre(virement.capture());
		return virement.getValue();
	}

	@Test
	void unVirementSimuleEmisRecoit201AvecLeNouveauSoldeEtEstMarqueSimule() {
		when(emission.emettre(any())).thenReturn(7000L);

		ResponseEntity<Reponse> reponse = controller.simuler(new Demande("FR7699999000019900000000105",
				new BigDecimal("3000"), "Beneficiaire", "DE89370400440532013000", "Remboursement"));

		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(new Reponse("Virement émis : le solde du compte a diminué", 7000L), reponse.getBody());
		VirementSortant virement = virementTransmis();
		assertThat(virement.simule()).isTrue();
		assertEquals(3000L, virement.montantCentimes());
		assertEquals("FR7699999000019900000000105", virement.iban());
		assertEquals("DE89370400440532013000", virement.ibanBeneficiaire());
		assertEquals("Beneficiaire", virement.beneficiaire());
	}

	@Test
	void unMontantAbsentNonEntierOuTropGrandEstTransmisCommeZero() {
		assertEquals(0L, new Demande(null, null, null, null, null).enCentimes());
		assertEquals(0L, new Demande(null, new BigDecimal("12.5"), null, null, null).enCentimes());
		assertEquals(0L, new Demande(null, new BigDecimal("1e30"), null, null, null).enCentimes());
		assertEquals(1250L, new Demande(null, new BigDecimal("1250.00"), null, null, null).enCentimes());
	}

	@Test
	void uneDemandeSansCorpsEstTransmiseVidePourEtreRefusee() {
		controller.simuler(null);

		assertEquals(0L, virementTransmis().montantCentimes());
	}

	@Test
	void lesRefusPortentLesMessagesExactsDuTicket() {
		assertRefus(controller.montantInvalide(), HttpStatus.BAD_REQUEST, "Montant invalide");
		assertRefus(controller.ibanInvalide(), HttpStatus.BAD_REQUEST, "IBAN invalide");
		assertRefus(controller.soldeInsuffisant(), HttpStatus.CONFLICT, "Solde insuffisant");
		assertRefus(controller.compteCloture(), HttpStatus.CONFLICT, "Compte clôturé");
		assertRefus(controller.compteIntrouvable(), HttpStatus.NOT_FOUND, "Compte introuvable");

		ResponseEntity<Refus> incompletes = controller.informationsIncompletes();
		assertEquals(HttpStatus.BAD_REQUEST, incompletes.getStatusCode());
		assertThat(incompletes.getBody().message()).contains("bénéficiaire").contains("libellé");
	}

	private static void assertRefus(ResponseEntity<Refus> reponse, HttpStatus statut, String message) {
		assertEquals(statut, reponse.getStatusCode());
		assertEquals(new Refus(message), reponse.getBody());
	}

	@Test
	void horsEnvironnementDeTestLeBouchonNExistePas() {
		ApplicationContextRunner contexte = new ApplicationContextRunner()
				.withBean(EmissionVirement.class, () -> emission)
				.withUserConfiguration(VirementSortantTestController.class);

		contexte.withPropertyValues("app.test.virements-simules=false")
				.run(application -> assertThat(application).doesNotHaveBean(VirementSortantTestController.class));
		contexte.run(application -> assertThat(application).doesNotHaveBean(VirementSortantTestController.class));
		contexte.withPropertyValues("app.test.virements-simules=true")
				.run(application -> assertThat(application).hasSingleBean(VirementSortantTestController.class));
	}

}
