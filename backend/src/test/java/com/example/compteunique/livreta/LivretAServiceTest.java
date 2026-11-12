package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.dossier.Categories.PaysNaissance;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.LivretAService.Suivi;

// CU-31 (US-22) : un cas de test par critère d'acceptation, et CU-295 critère 5 (expiration simulée).
class LivretAServiceTest {

	/** Jeudi 12 novembre 2026, 10 h. */
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final String JETON = "jeton";
	private static final UUID CLIENTE = UUID.randomUUID();

	private final CompteService comptes = mock(CompteService.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final DemandeLivretARepository demandes = mock(DemandeLivretARepository.class);
	private final Dossier dossier = mock(Dossier.class);
	private final Compte compte = new Compte(7L, CLIENTE, Iban.de("99000000001"), 3L, MAINTENANT.minusDays(30));

	private LivretAService service(OffsetDateTime maintenant) {
		return new LivretAService(comptes, dossiers, demandes, Clock.fixed(maintenant.toInstant(), ZoneOffset.UTC));
	}

	private final LivretAService service = service(MAINTENANT);

	@BeforeEach
	void cliente() {
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(dossier.getId()).thenReturn(7L);
		when(dossier.getCivilite()).thenReturn("Mme");
		when(dossier.getNomNaissance()).thenReturn("Cliente-A");
		when(dossier.getPrenoms()).thenReturn("Test");
		when(dossier.getDateNaissance()).thenReturn(LocalDate.of(1990, 5, 14));
		when(dossier.getCommuneNaissance()).thenReturn("Paris");
		when(dossier.getPaysNaissance()).thenReturn(PaysNaissance.FRANCE);
		when(dossiers.findById(7L)).thenReturn(Optional.of(dossier));
		when(dossiers.idsMemeIdentite("CLIENTEA", "TEST", LocalDate.of(1990, 5, 14), "PARIS", "FRANCE"))
				.thenReturn(List.of(7L, 12L));
		when(demandes.save(any(DemandeLivretA.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static DemandeLivretA demande(boolean partage) {
		return new DemandeLivretA(7L, CLIENTE, partage, MAINTENANT);
	}

	private void avecDemande(DemandeLivretA demande) {
		when(demandes.findByReferenceAndCustomerId(demande.getReference(), CLIENTE)).thenReturn(Optional.of(demande));
		when(demandes.findByReference(demande.getReference())).thenReturn(Optional.of(demande));
	}

	private static void assertRefus(HttpStatus statut, String message, Runnable action) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, action::run);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	// Information et choix : attestation obligatoire, choix de partage enregistré avec la date et l'heure.
	@Test
	void eligibiliteValideeEnregistreAttestationEtChoixAvecLaDate() {
		Suivi suivi = service.demander(JETON, true, false);
		DemandeLivretA demande = suivi.demande();
		assertEquals(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION, demande.getStatut());
		assertEquals(MAINTENANT, demande.getAttestationLe());
		assertFalse(demande.isPartageDetailAutorise());
		assertEquals(MAINTENANT, demande.getChoixPartageLe());
		assertEquals(MAINTENANT, demande.getCreeeLe());
		assertEquals(7L, demande.getDossierId());
		assertEquals(CLIENTE, demande.getCustomerId());
		assertThat(demande.getReference()).isNotBlank();
		assertNull(demande.getInterrogationLe());
	}

	// Interrogation : nom, prénom, sexe, date et lieu de naissance transmis.
	@Test
	void identiteATransmettreLueDansLeDossier() {
		LivretAService.Transmission transmission = service.demander(JETON, true, true).transmission();
		assertEquals(new LivretAService.Transmission("Cliente-A", "Test", "F", LocalDate.of(1990, 5, 14), "Paris"),
				transmission);
		when(dossier.getCivilite()).thenReturn("M.");
		assertEquals("M", LivretAService.transmission(dossier).sexe());
	}

	@Test
	void attestationNonCocheeBloque() {
		assertRefus(HttpStatus.BAD_REQUEST, LivretAService.ATTESTATION_OBLIGATOIRE,
				() -> service.demander(JETON, false, true));
		assertRefus(HttpStatus.BAD_REQUEST, LivretAService.ATTESTATION_OBLIGATOIRE,
				() -> service.demander(JETON, null, true));
		verify(demandes, never()).save(any());
	}

	@Test
	void choixDePartageObligatoire() {
		assertRefus(HttpStatus.BAD_REQUEST, LivretAService.CHOIX_PARTAGE_OBLIGATOIRE,
				() -> service.demander(JETON, true, null));
	}

	// Contrôle interne : Livret A déjà détenu chez Hi-Tech Bank, y compris sous un autre dossier de la même
	// identité (pièce renouvelée).
	@Test
	void livretADejaDetenuChezNousBloque() {
		DemandeLivretA validee = new DemandeLivretA(12L, UUID.randomUUID(), true, MAINTENANT.minusDays(40));
		validee.recevoirReponse("i", ReponseFiscale.AUCUN_LIVRET_A, MAINTENANT.minusDays(40));
		when(demandes.findByDossierIdIn(List.of(7L, 12L))).thenReturn(List.of(validee));
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, () -> service.demander(JETON, true, true));
		assertEquals(HttpStatus.CONFLICT, refus.getStatut());
		assertEquals(LivretAService.LIVRET_A_DETENU, refus.getMessage());
		assertTrue(refus.isTermine());
		verify(demandes, never()).save(any());
	}

	@Test
	void demandeDejaEnCoursBloque() {
		when(demandes.findByDossierIdIn(List.of(7L, 12L))).thenReturn(List.of(demande(true)));
		assertRefus(HttpStatus.CONFLICT, LivretAService.DEMANDE_EN_COURS, () -> service.demander(JETON, true, true));
	}

	@Test
	void sansAutreDossierLeControleRegardeLeDossierDeLaCliente() {
		when(dossiers.idsMemeIdentite(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(demandes.findByDossierIdIn(anyCollection())).thenReturn(List.of());
		service.demander(JETON, true, true);
		verify(demandes).findByDossierIdIn(List.of(7L));
	}

	// Réponse « aucun autre Livret A » : ouverture validée, le parcours continue.
	@Test
	void aucunAutreLivretAValideLOuverture() {
		DemandeLivretA demande = demande(true);
		avecDemande(demande);
		Suivi suivi = service.enregistrerReponse(JETON, demande.getReference(), "interro-1",
				ReponseFiscale.AUCUN_LIVRET_A);
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, demande.getStatut());
		assertEquals("interro-1", demande.getInterrogationId());
		assertEquals(MAINTENANT, demande.getInterrogationLe());
		assertEquals(ReponseFiscale.AUCUN_LIVRET_A, demande.getReponseFiscale());
		assertEquals(MAINTENANT, demande.getReponseLe());
		assertNull(suivi.transmission());
	}

	// Réponse « Livret A existant » : non ouvert, suite dans CU-296.
	@Test
	void livretAExistantNOuvrePas() {
		DemandeLivretA demande = demande(false);
		avecDemande(demande);
		service.enregistrerReponse(JETON, demande.getReference(), "i", ReponseFiscale.LIVRET_A_EXISTANT);
		assertEquals(StatutDemandeLivretA.LIVRET_A_EXISTANT, demande.getStatut());
	}

	// Réponse « indisponible » ou pas de réponse : reste en cours ; le délai part de la première transmission.
	@Test
	void indisponiblePuisSansReponseResteEnCours() {
		DemandeLivretA demande = demande(true);
		avecDemande(demande);
		service.enregistrerReponse(JETON, demande.getReference(), "i1", ReponseFiscale.INDISPONIBLE);
		Suivi suivi = service(MAINTENANT.plusHours(1)).enregistrerReponse(JETON, demande.getReference(), "i2",
				ReponseFiscale.SANS_REPONSE);
		assertEquals(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION, demande.getStatut());
		assertEquals("i1", demande.getInterrogationId());
		assertEquals(MAINTENANT, demande.getInterrogationLe());
		assertEquals(ReponseFiscale.SANS_REPONSE, demande.getReponseFiscale());
		assertEquals(MAINTENANT.plusHours(1), demande.getReponseLe());
		assertEquals("Cliente-A", suivi.transmission().nom());
	}

	@Test
	void reponseRefuseeSansResultatSurDemandeInconnueOuTerminee() {
		DemandeLivretA demande = demande(true);
		avecDemande(demande);
		assertRefus(HttpStatus.BAD_REQUEST, LivretAService.REPONSE_OBLIGATOIRE,
				() -> service.enregistrerReponse(JETON, demande.getReference(), "i", null));
		when(demandes.findByReferenceAndCustomerId("autre", CLIENTE)).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, LivretAService.DEMANDE_INTROUVABLE,
				() -> service.enregistrerReponse(JETON, "autre", "i", ReponseFiscale.AUCUN_LIVRET_A));
		service.enregistrerReponse(JETON, demande.getReference(), "i", ReponseFiscale.AUCUN_LIVRET_A);
		assertRefus(HttpStatus.CONFLICT, LivretAService.DEMANDE_TERMINEE, () -> service
				.enregistrerReponse(JETON, demande.getReference(), "i", ReponseFiscale.LIVRET_A_EXISTANT));
	}

	@Test
	void etatDeLaDerniereDemande() {
		when(demandes.findFirstByCustomerIdOrderByCreeeLeDesc(CLIENTE)).thenReturn(Optional.empty());
		assertTrue(service.etat(JETON).isEmpty());
		DemandeLivretA demande = demande(true);
		when(demandes.findFirstByCustomerIdOrderByCreeeLeDesc(CLIENTE)).thenReturn(Optional.of(demande));
		assertEquals(demande, service.etat(JETON).orElseThrow().demande());
	}

	@Test
	void dossierIntrouvable() {
		when(dossiers.findById(7L)).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, LivretAService.DEMANDE_INTROUVABLE,
				() -> service.demander(JETON, true, true));
	}

	// Pas de réponse sous 2 jours ouvrés : la demande reste en attente et le conseiller est alerté.
	// Transmise le jeudi 12 à 10 h : le délai expire le lundi 16 à 10 h (week-end exclu).
	@Test
	void conseillerAlerteApres2JoursOuvres() {
		DemandeLivretA transmise = demande(true);
		transmise.recevoirReponse("i", ReponseFiscale.SANS_REPONSE, MAINTENANT);
		DemandeLivretA nonTransmise = demande(true);
		when(demandes.findByStatutAndConseillerAlerteLeIsNull(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION))
				.thenReturn(List.of(transmise, nonTransmise));

		assertEquals(0, service(OffsetDateTime.of(2026, 11, 16, 9, 59, 0, 0, ZoneOffset.UTC))
				.alerterLesDemandesEnRetard());
		assertNull(transmise.getConseillerAlerteLe());

		OffsetDateTime lundi = OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.UTC);
		assertEquals(1, service(lundi).alerterLesDemandesEnRetard());
		assertEquals(lundi, transmise.getConseillerAlerteLe());
		assertEquals(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION, transmise.getStatut());
		assertNull(nonTransmise.getConseillerAlerteLe());
		assertFalse(transmise.aAlerter(lundi.plusDays(3)));
	}

	// CU-295 critère 5 : expiration du délai simulée.
	@Test
	void expirationDuDelaiSimulee() {
		DemandeLivretA demande = demande(true);
		avecDemande(demande);
		assertRefus(HttpStatus.CONFLICT, LivretAService.NON_TRANSMISE,
				() -> service.simulerExpirationDelai(demande.getReference()));
		demande.recevoirReponse("i", ReponseFiscale.SANS_REPONSE, MAINTENANT);
		assertEquals(MAINTENANT, service.simulerExpirationDelai(demande.getReference()).getConseillerAlerteLe());
		demande.recevoirReponse("i", ReponseFiscale.AUCUN_LIVRET_A, MAINTENANT);
		assertRefus(HttpStatus.CONFLICT, LivretAService.DEMANDE_TERMINEE,
				() -> service.simulerExpirationDelai(demande.getReference()));
		when(demandes.findByReference("inconnue")).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, LivretAService.DEMANDE_INTROUVABLE,
				() -> service.simulerExpirationDelai("inconnue"));
	}

	@Test
	void joursOuvresSautentLeWeekEnd() {
		OffsetDateTime vendredi = OffsetDateTime.of(2026, 11, 13, 9, 0, 0, 0, ZoneOffset.UTC);
		assertEquals(OffsetDateTime.of(2026, 11, 17, 9, 0, 0, 0, ZoneOffset.UTC), JoursOuvres.ajouter(vendredi, 2));
		OffsetDateTime lundi = OffsetDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneOffset.UTC);
		assertEquals(OffsetDateTime.of(2026, 11, 18, 9, 0, 0, 0, ZoneOffset.UTC), JoursOuvres.ajouter(lundi, 2));
		OffsetDateTime samedi = OffsetDateTime.of(2026, 11, 14, 9, 0, 0, 0, ZoneOffset.UTC);
		assertEquals(OffsetDateTime.of(2026, 11, 17, 9, 0, 0, 0, ZoneOffset.UTC), JoursOuvres.ajouter(samedi, 2));
	}

}
