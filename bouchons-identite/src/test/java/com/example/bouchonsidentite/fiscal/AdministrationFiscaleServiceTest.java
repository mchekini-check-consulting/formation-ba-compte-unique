package com.example.bouchonsidentite.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.fiscal.FiscalRequetes.Interrogation;
import com.example.bouchonsidentite.fiscal.FiscalRequetes.ReponseFiscale;

// CU-295 : un cas de test par critère d'acceptation du bouchon.
class AdministrationFiscaleServiceTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneId.of("Europe/Paris"));

	private final InterrogationFiscaleRepository interrogations = mock(InterrogationFiscaleRepository.class);
	private final AdministrationFiscaleService service = new AdministrationFiscaleService(interrogations, HORLOGE);
	private final List<InterrogationFiscale> traces = new ArrayList<>();

	@BeforeEach
	void enregistrementSimule() {
		when(interrogations.save(any(InterrogationFiscale.class))).thenAnswer(invocation -> {
			traces.add(invocation.getArgument(0));
			return invocation.getArgument(0);
		});
	}

	private static Interrogation demande(boolean partage, CasFiscal cas) {
		return new Interrogation("demande-1", "Cliente-A", "Test", "F", LocalDate.of(1990, 5, 14), "Paris", partage,
				cas);
	}

	@Test
	void critere1SansLivretA() {
		ReponseFiscale reponse = service.interroger(demande(true, CasFiscal.SANS_LIVRET_A));
		assertEquals(ResultatFiscal.AUCUN_LIVRET_A, reponse.resultat());
		assertNull(reponse.detail());
		assertEquals("Aucun autre Livret A", reponse.message());
	}

	@Test
	void sansCasDeTestLaReponseEstAucunLivretA() {
		assertEquals(ResultatFiscal.AUCUN_LIVRET_A, service.interroger(demande(false, null)).resultat());
	}

	@Test
	void critere2DetenuAilleursAvecAccordDonneLeDetail() {
		ReponseFiscale reponse = service.interroger(demande(true, CasFiscal.DETENU_AILLEURS));
		assertEquals(ResultatFiscal.LIVRET_A_EXISTANT, reponse.resultat());
		assertSame(AdministrationFiscaleService.DETAIL_FICTIF, reponse.detail());
		assertEquals("Banque Exemple", reponse.detail().etablissement());
		assertTrue(traces.get(0).isDetailCommunique());
	}

	@Test
	void critere3DetenuAilleursSansAccordSansDetail() {
		ReponseFiscale reponse = service.interroger(demande(false, CasFiscal.DETENU_AILLEURS));
		assertEquals(ResultatFiscal.LIVRET_A_EXISTANT, reponse.resultat());
		assertNull(reponse.detail());
		assertFalse(traces.get(0).isDetailCommunique());
	}

	@ParameterizedTest
	@CsvSource({ "SANS_REPONSE, SANS_REPONSE", "INDISPONIBLE, INDISPONIBLE" })
	void criteres4Et6SansReponseEtIndisponible(CasFiscal cas, ResultatFiscal attendu) {
		ReponseFiscale reponse = service.interroger(demande(true, cas));
		assertEquals(attendu, reponse.resultat());
		assertNull(reponse.detail());
	}

	@Test
	void critere9AppelTraceSansIdentite() {
		ReponseFiscale reponse = service.interroger(demande(true, CasFiscal.SANS_LIVRET_A));
		InterrogationFiscale trace = traces.get(0);
		assertNotNull(reponse.interrogationId());
		assertEquals(reponse.interrogationId(), trace.getInterrogationId());
		assertEquals("demande-1", trace.getDemandeId());
		assertEquals(CasFiscal.SANS_LIVRET_A, trace.getCasDeTest());
		assertEquals(ResultatFiscal.AUCUN_LIVRET_A, trace.getResultat());
		assertEquals(OffsetDateTime.now(HORLOGE), trace.getAppeleLe());
		assertEquals(OffsetDateTime.now(HORLOGE), reponse.appeleLe());
		assertNull(trace.getId());
	}

	@Test
	void sexeEnMinusculeAccepte() {
		Interrogation minuscule = new Interrogation("d", "N", "P", "m", LocalDate.of(1990, 1, 1), "Lyon", false,
				null);
		assertEquals(ResultatFiscal.AUCUN_LIVRET_A, service.interroger(minuscule).resultat());
	}

	@Test
	void demandeIncompleteRefusee() {
		assertRefus(null);
		assertRefus(new Interrogation(" ", "N", "P", "F", LocalDate.of(1990, 1, 1), "Lyon", false, null));
		assertRefus(new Interrogation("d", null, "P", "F", LocalDate.of(1990, 1, 1), "Lyon", false, null));
		assertRefus(new Interrogation("d", "N", "", "F", LocalDate.of(1990, 1, 1), "Lyon", false, null));
		assertRefus(new Interrogation("d", "N", "P", "F", LocalDate.of(1990, 1, 1), null, false, null));
		assertRefus(new Interrogation("d", "N", "P", "X", LocalDate.of(1990, 1, 1), "Lyon", false, null));
		assertRefus(new Interrogation("d", "N", "P", null, LocalDate.of(1990, 1, 1), "Lyon", false, null));
		assertRefus(new Interrogation("d", "N", "P", "F", null, "Lyon", false, null));
	}

	private void assertRefus(Interrogation demande) {
		ResponseStatusException refus = assertThrows(ResponseStatusException.class, () -> service.interroger(demande));
		assertEquals(HttpStatus.BAD_REQUEST, refus.getStatusCode());
	}

	@Test
	void controleurDelegueAuService() {
		AdministrationFiscaleService mockService = mock(AdministrationFiscaleService.class);
		ReponseFiscale reponse = mock(ReponseFiscale.class);
		Interrogation demande = demande(true, null);
		when(mockService.interroger(demande)).thenReturn(reponse);
		assertSame(reponse, new AdministrationFiscaleController(mockService).interroger(demande));
	}

}
