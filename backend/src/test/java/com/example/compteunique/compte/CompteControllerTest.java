package com.example.compteunique.compte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.compte.CompteController.CompteVue;
import com.example.compteunique.compte.CompteController.Refus;

class CompteControllerTest {

	private final CompteService comptes = mock(CompteService.class);
	private final CompteController controller = new CompteController(comptes);
	private final Compte compte = new Compte(1L, UUID.randomUUID(), Iban.de("99000000001"), 7L, OffsetDateTime.now());

	@Test
	void laListeRenvoieLIbanMasqueSansLIbanComplet() {
		when(comptes.mesComptes("jeton")).thenReturn(List.of(compte));

		List<CompteVue> reponse = controller.mesComptes("jeton");

		assertThat(reponse).hasSize(1);
		assertEquals("ACTIF", reponse.get(0).statut());
		assertEquals(compte.getIbanMasque(), reponse.get(0).ibanMasque());
		assertThat(reponse.get(0).toString()).doesNotContain(compte.getIban());
	}

	@Test
	void unCompteOuvertSansVersementRenvoieZeroEtLaDateDOuverture() {
		CompteVue vue = CompteVue.de(compte);

		assertEquals(0L, vue.soldeCentimes());
		assertEquals(compte.getOuvertLe(), vue.soldeMisAJourLe());
		assertThat(vue.premierVersementAttendu()).isTrue();
	}

	@Test
	void unCompteAvecVersementRenvoieSonSoldeSansInviterAuPremierVersement() {
		ReflectionTestUtils.setField(compte, "soldeCentimes", 124050L);
		ReflectionTestUtils.setField(compte, "soldeMisAJourLe", compte.getOuvertLe().plusDays(1));

		CompteVue vue = CompteVue.de(compte);

		assertEquals(124050L, vue.soldeCentimes());
		assertThat(vue.premierVersementAttendu()).isFalse();
	}

	@Test
	void unSoldeRameneAZeroApresUnVersementNInviteAucunPremierVersement() {
		ReflectionTestUtils.setField(compte, "soldeMisAJourLe", compte.getOuvertLe().plusDays(1));

		assertThat(CompteVue.de(compte).premierVersementAttendu()).isFalse();
	}

	@Test
	void unSoldeNegatifNEstJamaisRenvoye() {
		ReflectionTestUtils.setField(compte, "soldeCentimes", -500L);

		CompteVue vue = CompteVue.de(compte);

		assertNull(vue.soldeCentimes());
		assertNull(vue.soldeMisAJourLe());
		assertThat(vue.premierVersementAttendu()).isFalse();
	}

	@Test
	void unCompteClotureRenvoieZeroSansDateQuelQueSoitSonSolde() {
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);
		ReflectionTestUtils.setField(compte, "soldeCentimes", 700L);

		CompteVue vue = CompteVue.de(compte);

		assertEquals(0L, vue.soldeCentimes());
		assertNull(vue.soldeMisAJourLe());
		assertThat(vue.premierVersementAttendu()).isFalse();
	}

	@Test
	void leDetailRenvoieLeCompteDemande() {
		when(comptes.consulter("jeton", "5")).thenReturn(compte);

		assertEquals(compte.getIbanMasque(), controller.consulter("jeton", "5").ibanMasque());
	}

	@Test
	void uneClienteNonConnecteeRecoit401() {
		assertEquals(HttpStatus.UNAUTHORIZED, controller.nonConnectee().getStatusCode());
	}

	@Test
	void unCompteInaccessibleRecoitLeMessageExactDuTicketSansAutreInformation() {
		ResponseEntity<Refus> reponse = controller.inaccessible();

		assertEquals(HttpStatus.NOT_FOUND, reponse.getStatusCode());
		assertEquals(new Refus("Ce compte n’est pas accessible"), reponse.getBody());
	}

}
