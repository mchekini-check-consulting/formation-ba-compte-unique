package com.example.bouchonsidentite.docusign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.docusign.DocusignRequetes.DemandeEnveloppe;
import com.example.bouchonsidentite.docusign.DocusignRequetes.ReponseDocusign;
import com.example.bouchonsidentite.docusign.DocusignRequetes.SaisieCode;

// CU-89 : un cas de test par critère d'acceptation.
class DocusignServiceTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneId.of("Europe/Paris"));

	private final AppelDocusignRepository appels = mock(AppelDocusignRepository.class);
	private final DocusignService service = new DocusignService(appels, HORLOGE);
	private final List<AppelDocusign> traces = new ArrayList<>();

	@BeforeEach
	void enregistrementSimule() {
		when(appels.save(any(AppelDocusign.class))).thenAnswer(invocation -> {
			traces.add(invocation.getArgument(0));
			return invocation.getArgument(0);
		});
		when(appels.findFirstByEnveloppeIdAndOperation("env-1", OperationDocusign.ENVOI_ENVELOPPE))
				.thenReturn(Optional.of(new AppelDocusign("dossier-1", "env-1", OperationDocusign.ENVOI_ENVELOPPE,
						null, ResultatDocusign.ENVELOPPE_ENVOYEE, OffsetDateTime.now(HORLOGE))));
	}

	@Test
	void envoiDEnveloppeSimuleLEnvoiDuCodeEtLeTrace() {
		ReponseDocusign reponse = service.envoyerEnveloppe("dossier-1");
		assertNotNull(reponse.enveloppeId());
		assertEquals(ResultatDocusign.ENVELOPPE_ENVOYEE, reponse.resultat());
		AppelDocusign trace = traces.get(0);
		assertEquals("dossier-1", trace.getDossierId());
		assertEquals(reponse.enveloppeId(), trace.getEnveloppeId());
		assertEquals(OperationDocusign.ENVOI_ENVELOPPE, trace.getOperation());
		assertNull(trace.getCodeRecu());
		assertEquals(OffsetDateTime.now(HORLOGE), trace.getAppeleLe());
	}

	@ParameterizedTest
	@CsvSource({ "111111, SIGNATURE_VALIDE, Signature valide", "000000, CODE_FAUX, Code faux",
			"999999, CODE_EXPIRE, Code expiré", "123456, CODE_FAUX, Code faux" })
	void codeDeTestDonneSonResultatEtEstTrace(String code, ResultatDocusign attendu, String message) {
		ReponseDocusign reponse = service.saisirCode("env-1", code);
		assertEquals(attendu, reponse.resultat());
		assertEquals(message, reponse.message());
		AppelDocusign trace = traces.get(0);
		assertEquals(code, trace.getCodeRecu());
		assertEquals(attendu, trace.getResultat());
		assertEquals("dossier-1", trace.getDossierId());
		assertEquals(OperationDocusign.SAISIE_CODE, trace.getOperation());
	}

	@Test
	void annulationRenvoieAnnulationDemandee() {
		ReponseDocusign reponse = service.annuler("env-1");
		assertEquals(ResultatDocusign.ANNULATION_DEMANDEE, reponse.resultat());
		assertEquals("Annulation demandée", reponse.message());
		assertEquals(OperationDocusign.ANNULATION, traces.get(0).getOperation());
	}

	@Test
	void codeQuiNEstPasA6ChiffresRefuse() {
		ResponseStatusException refus = assertThrows(ResponseStatusException.class,
				() -> service.saisirCode("env-1", "12ab"));
		assertEquals(HttpStatus.BAD_REQUEST, refus.getStatusCode());
	}

	@Test
	void enveloppeInconnueRefusee() {
		ResponseStatusException refus = assertThrows(ResponseStatusException.class,
				() -> service.annuler("inconnue"));
		assertEquals(HttpStatus.NOT_FOUND, refus.getStatusCode());
	}

	@Test
	void controleurDelegueAuService() {
		DocusignService mockService = mock(DocusignService.class);
		ReponseDocusign reponse = mock(ReponseDocusign.class);
		when(mockService.envoyerEnveloppe("d")).thenReturn(reponse);
		when(mockService.saisirCode("e", "111111")).thenReturn(reponse);
		when(mockService.annuler("e")).thenReturn(reponse);
		DocusignController controleur = new DocusignController(mockService);
		assertSame(reponse, controleur.envoyer(new DemandeEnveloppe("d")));
		assertSame(reponse, controleur.saisirCode("e", new SaisieCode("111111")));
		assertSame(reponse, controleur.annuler("e"));
	}

}
