package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.livreta.LivretAController.Refus;
import com.example.compteunique.livreta.VersementInitialLivretAController.VersementInitial;

// CU-33 : route POST /livret-a/demandes/{reference}/versement-initial et refus affichés à la cliente.
class VersementInitialLivretAControllerTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 26, 10, 0, 0, 0, ZoneOffset.UTC);

	private final VersementInitialLivretAService versements = mock(VersementInitialLivretAService.class);
	private final LivretAService demandes = mock(LivretAService.class);
	private final VersementInitialLivretAController controleur = new VersementInitialLivretAController(versements,
			demandes);

	@Test
	void montantEnregistreDansLaDemande() {
		DemandeLivretA demande = new DemandeLivretA(7L, UUID.randomUUID(), true, MAINTENANT);
		demande.recevoirReponse("i", ReponseFiscale.AUCUN_LIVRET_A, MAINTENANT);
		demande.choisirVersementInitial(15_050L, 42L, MAINTENANT);
		when(versements.choisir("j", "ref", "150,50")).thenReturn(demande);
		when(demandes.suivi(demande)).thenReturn(new LivretAService.Suivi(demande, null));
		DemandeLivretAVue vue = controleur.choisir("j", "ref", new VersementInitial("150,50"));
		assertEquals(15_050L, vue.versementInitialCentimes());
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, vue.statut());
	}

	@Test
	void refusAvecLeMessageAfficheALaCliente() {
		when(versements.choisir("j", "ref", null))
				.thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, VersementInitialLivretAService.MINIMUM));
		ResponseEntity<Refus> refus = controleur
				.refus(assertThrows(DemandeRefusee.class, () -> controleur.choisir("j", "ref", null)));
		assertEquals(HttpStatus.BAD_REQUEST, refus.getStatusCode());
		assertEquals(VersementInitialLivretAService.MINIMUM, refus.getBody().message());
	}

}
