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

import com.example.compteunique.virement.ReceptionVirement.Resultat;
import com.example.compteunique.virement.VirementTestController.Demande;
import com.example.compteunique.virement.VirementTestController.Refus;
import com.example.compteunique.virement.VirementTestController.Reponse;

class VirementTestControllerTest {

	private final ReceptionVirement reception = mock(ReceptionVirement.class);
	private final VirementTestController controller = new VirementTestController(reception);

	private VirementEntrant virementTransmis() {
		ArgumentCaptor<VirementEntrant> virement = ArgumentCaptor.forClass(VirementEntrant.class);
		verify(reception).recevoir(virement.capture());
		return virement.getValue();
	}

	@Test
	void leBouchonSeDitDisponible() {
		assertEquals(HttpStatus.NO_CONTENT, controller.disponible().getStatusCode());
	}

	@Test
	void unVirementSimuleCrediteRecoit201EtEstMarqueSimule() {
		when(reception.recevoir(any())).thenReturn(Resultat.CREDITE);

		ResponseEntity<Reponse> reponse = controller
				.simuler(new Demande("FR7699999000019900000000105", new BigDecimal("1250"), "Emetteur", "Libellé"));

		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(new Reponse(Resultat.CREDITE, "Virement reçu : le solde du compte a augmenté"), reponse.getBody());
		VirementEntrant virement = virementTransmis();
		assertThat(virement.simule()).isTrue();
		assertEquals(1250L, virement.montantCentimes());
		assertEquals("FR7699999000019900000000105", virement.iban());
	}

	@Test
	void unVirementRenvoyeRecoitLeMotifAC04() {
		when(reception.recevoir(any())).thenReturn(Resultat.RENVOYE);

		ResponseEntity<Reponse> reponse = controller
				.simuler(new Demande("FR7699999000019900000000396", new BigDecimal("100"), "Emetteur", "Libellé"));

		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertThat(reponse.getBody().message()).contains("compte clôturé (AC04)");
	}

	@Test
	void unMontantAbsentNonEntierOuTropGrandEstTransmisCommeZero() {
		assertEquals(0L, new Demande(null, null, null, null).enCentimes());
		assertEquals(0L, new Demande(null, new BigDecimal("12.5"), null, null).enCentimes());
		assertEquals(0L, new Demande(null, new BigDecimal("1e30"), null, null).enCentimes());
		assertEquals(1250L, new Demande(null, new BigDecimal("1250.00"), null, null).enCentimes());
	}

	@Test
	void uneDemandeSansCorpsEstTransmiseVidePourEtreRefusee() {
		when(reception.recevoir(any())).thenReturn(Resultat.CREDITE);

		controller.simuler(null);

		assertEquals(0L, virementTransmis().montantCentimes());
	}

	@Test
	void lesRefusPortentLesMessagesExactsDesTickets() {
		ResponseEntity<Refus> montant = controller.montantInvalide();
		assertEquals(HttpStatus.BAD_REQUEST, montant.getStatusCode());
		assertEquals(new Refus("Montant invalide"), montant.getBody());

		ResponseEntity<Refus> introuvable = controller.compteIntrouvable();
		assertEquals(HttpStatus.NOT_FOUND, introuvable.getStatusCode());
		assertEquals(new Refus("Compte introuvable"), introuvable.getBody());

		ResponseEntity<Refus> incompletes = controller.informationsIncompletes();
		assertEquals(HttpStatus.BAD_REQUEST, incompletes.getStatusCode());
		assertThat(incompletes.getBody().message()).contains("émetteur").contains("libellé");
	}

	@Test
	void horsEnvironnementDeTestLeBouchonNExistePas() {
		ApplicationContextRunner contexte = new ApplicationContextRunner()
				.withBean(ReceptionVirement.class, () -> reception)
				.withUserConfiguration(VirementTestController.class);

		contexte.withPropertyValues("app.test.virements-simules=false")
				.run(application -> assertThat(application).doesNotHaveBean(VirementTestController.class));
		contexte.run(application -> assertThat(application).doesNotHaveBean(VirementTestController.class));
		contexte.withPropertyValues("app.test.virements-simules=true")
				.run(application -> assertThat(application).hasSingleBean(VirementTestController.class));
	}

}
