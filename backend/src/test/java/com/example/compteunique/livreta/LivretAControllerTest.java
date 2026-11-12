package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.Refus;
import com.example.compteunique.livreta.LivretAController.ResultatInterrogation;
import com.example.compteunique.livreta.LivretAService.Suivi;
import com.example.compteunique.livreta.LivretAService.Transmission;

// CU-31 : routes /livret-a et vue de la demande (messages affichés à la cliente).
class LivretAControllerTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final Transmission TRANSMISSION = new Transmission("N", "P", "F", LocalDate.of(1990, 1, 1), "Paris");

	private final LivretAService service = mock(LivretAService.class);
	private final LivretAController controleur = new LivretAController(service);

	private static DemandeLivretA demande() {
		return new DemandeLivretA(7L, UUID.randomUUID(), true, MAINTENANT);
	}

	@Test
	void etatSansDemandePuisAvecDemande() {
		when(service.etat("j")).thenReturn(Optional.empty());
		assertNull(controleur.etat("j").demande());
		DemandeLivretA demande = demande();
		when(service.etat("j")).thenReturn(Optional.of(new Suivi(demande, TRANSMISSION)));
		DemandeLivretAVue vue = controleur.etat("j").demande();
		assertEquals(demande.getReference(), vue.reference());
		assertEquals(DemandeLivretAVue.EN_COURS, vue.message());
		assertEquals("2 jours ouvrés", vue.delai());
		assertEquals(TRANSMISSION, vue.transmission());
		assertTrue(vue.partageDetailAutorise());
		assertEquals(MAINTENANT, vue.attestationLe());
		assertEquals(MAINTENANT, vue.choixPartageLe());
	}

	@Test
	void demandeCreeeEn201() {
		when(service.demander("j", true, false)).thenReturn(new Suivi(demande(), TRANSMISSION));
		ResponseEntity<DemandeLivretAVue> reponse = controleur.demander("j", new Eligibilite(true, false));
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		when(service.demander("j", null, null)).thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "x"));
		ResponseEntity<Refus> refus = controleur.refus(assertThrows(DemandeRefusee.class, () -> controleur.demander("j", null)));
		assertEquals(HttpStatus.BAD_REQUEST, refus.getStatusCode());
		assertEquals("x", refus.getBody().message());
		assertFalse(refus.getBody().termine());
	}

	@Test
	void reponseFiscaleTransmise() {
		DemandeLivretA demande = demande();
		demande.recevoirReponse("i", ReponseFiscale.AUCUN_LIVRET_A, MAINTENANT);
		when(service.enregistrerReponse("j", "ref", "i", ReponseFiscale.AUCUN_LIVRET_A))
				.thenReturn(new Suivi(demande, null));
		DemandeLivretAVue vue = controleur.reponseFiscale("j", "ref",
				new ResultatInterrogation("i", ReponseFiscale.AUCUN_LIVRET_A));
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, vue.statut());
		assertEquals(DemandeLivretAVue.VALIDEE, vue.message());
		assertNull(vue.transmission());
		when(service.enregistrerReponse("j", "ref", null, null))
				.thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "y"));
		assertThrows(DemandeRefusee.class,
				() -> controleur.reponseFiscale("j", "ref", null));
	}

	@Test
	void messagesSelonLaReponse() {
		DemandeLivretA existant = demande();
		existant.recevoirReponse("i", ReponseFiscale.LIVRET_A_EXISTANT, MAINTENANT);
		assertEquals(DemandeLivretAVue.EXISTANT, DemandeLivretAVue.de(new Suivi(existant, null)).message());

		DemandeLivretA indisponible = demande();
		indisponible.recevoirReponse("i", ReponseFiscale.INDISPONIBLE, MAINTENANT);
		DemandeLivretAVue vue = DemandeLivretAVue.de(new Suivi(indisponible, TRANSMISSION));
		assertEquals(DemandeLivretAVue.INDISPONIBLE, vue.message());
		assertFalse(vue.retard());

		indisponible.alerterConseiller(MAINTENANT);
		DemandeLivretAVue enRetard = DemandeLivretAVue.de(new Suivi(indisponible, TRANSMISSION));
		assertTrue(enRetard.retard());
		assertEquals(DemandeLivretAVue.RETARD, enRetard.message());
		assertEquals(ReponseFiscale.INDISPONIBLE, enRetard.reponseFiscale());
		assertEquals(MAINTENANT, enRetard.interrogationLe());
	}

	@Test
	void sansSessionOuSansCompte() {
		assertEquals(HttpStatus.UNAUTHORIZED, controleur.nonConnectee().getStatusCode());
		ResponseEntity<Refus> sansCompte = controleur.sansCompte();
		assertEquals(HttpStatus.NOT_FOUND, sansCompte.getStatusCode());
		assertEquals(LivretAController.SANS_COMPTE, sansCompte.getBody().message());
	}

	@Test
	void controleurDeTestSimuleLExpiration() {
		DemandeLivretA demande = demande();
		demande.recevoirReponse("i", ReponseFiscale.SANS_REPONSE, MAINTENANT);
		demande.alerterConseiller(MAINTENANT);
		when(service.simulerExpirationDelai("ref")).thenReturn(demande);
		LivretATestController test = new LivretATestController(service);
		assertTrue(test.expirerDelai("ref").retard());
		ResponseEntity<Refus> refus = test.refus(new DemandeRefusee(HttpStatus.CONFLICT, "z"));
		assertEquals(HttpStatus.CONFLICT, refus.getStatusCode());
	}

}
