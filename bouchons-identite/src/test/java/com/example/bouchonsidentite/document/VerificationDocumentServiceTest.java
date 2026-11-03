package com.example.bouchonsidentite.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Messages;
import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;

// CUB-63 : un cas de test par critère d'acceptation.
class VerificationDocumentServiceTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("Europe/Paris"));
	private static final SaisieIdentite SAISIE_MARIE =
			new SaisieIdentite("Dupont", "Marie Claire", LocalDate.of(1990, 5, 14), LocalDate.of(2031, 6, 30));

	private final VerificationDocumentRepository verifications = mock(VerificationDocumentRepository.class);
	private final VerificationDocumentService service = new VerificationDocumentService(verifications, HORLOGE);

	@BeforeEach
	void enregistrementSimule() {
		when(verifications.save(any(VerificationDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private VerificationDocumentReponse verifier(String piece, SaisieIdentite saisie) {
		return service.verifier(new VerificationDocumentRequete("dossier-1", piece, piece, saisie));
	}

	private VerificationDocument enregistree() {
		ArgumentCaptor<VerificationDocument> capture = ArgumentCaptor.forClass(VerificationDocument.class);
		verify(verifications).save(capture.capture());
		return capture.getValue();
	}

	@Test
	void pieceValideVerifieeAvecDonneesExtraites() {
		VerificationDocumentReponse reponse = verifier("PIECE_VALIDE", SAISIE_MARIE);

		assertEquals(StatutVerificationDocument.VERIFIE, reponse.statut());
		assertEquals(new DonneesExtraites("Dupont", "Marie Claire", LocalDate.of(1990, 5, 14), LocalDate.of(2031, 6, 30)),
				reponse.donnees());
		assertEquals(new Concordance(true, true, true, true), reponse.concordance());
		assertEquals(Suite.POURSUIVRE, reponse.suite());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statutDossier());
		assertEquals(OffsetDateTime.now(HORLOGE), reponse.verifieLe());

		VerificationDocument enregistree = enregistree();
		assertEquals("dossier-1", enregistree.getDossierId());
		assertEquals(StatutVerificationDocument.VERIFIE, enregistree.getStatut());
		assertNull(enregistree.getMotif());
		assertTrue(enregistree.getConcordanceNom());
		assertTrue(enregistree.getConcordancePrenoms());
		assertTrue(enregistree.getConcordanceDateNaissance());
		assertTrue(enregistree.getConcordanceDateExpiration());
		assertEquals(OffsetDateTime.now(HORLOGE), enregistree.getVerifieLe());
	}

	@Test
	void dateExpirationNonSaisieNonControlee() {
		var saisie = new SaisieIdentite("DUPONT", "marie claire", LocalDate.of(1990, 5, 14), null);
		VerificationDocumentReponse reponse = verifier("PIECE_VALIDE", saisie);
		assertNull(reponse.concordance().dateExpiration());
		assertEquals(Suite.POURSUIVRE, reponse.suite());
	}

	@Test
	void pieceConformeALaSaisieConcordeAvecNimporteQuelleIdentite() {
		var saisie = new SaisieIdentite("Kessal", "Mohand", LocalDate.of(1988, 3, 9), LocalDate.of(2032, 1, 15));
		VerificationDocumentReponse reponse = verifier("PIECE_CONFORME_A_LA_SAISIE", saisie);

		assertEquals(StatutVerificationDocument.VERIFIE, reponse.statut());
		assertEquals(new DonneesExtraites("Kessal", "Mohand", LocalDate.of(1988, 3, 9), LocalDate.of(2032, 1, 15)),
				reponse.donnees());
		assertEquals(new Concordance(true, true, true, true), reponse.concordance());
		assertEquals(Suite.POURSUIVRE, reponse.suite());
		assertTrue(enregistree().getConcordanceNom());
	}

	@Test
	void pieceConformeALaSaisieSansDateExpirationValideDixAns() {
		var saisie = new SaisieIdentite("Kessal", "Mohand", LocalDate.of(1988, 3, 9), null);
		VerificationDocumentReponse reponse = verifier("PIECE_CONFORME_A_LA_SAISIE", saisie);

		assertEquals(LocalDate.of(2036, 11, 2), reponse.donnees().dateExpiration());
		assertNull(reponse.concordance().dateExpiration());
		assertEquals(Suite.POURSUIVRE, reponse.suite());
	}

	@Test
	void pieceConformeALaSaisieMaisExpireeRejetee() {
		var saisie = new SaisieIdentite("Kessal", "Mohand", LocalDate.of(1988, 3, 9), LocalDate.of(2025, 1, 1));
		VerificationDocumentReponse reponse = verifier("PIECE_CONFORME_A_LA_SAISIE", saisie);

		assertEquals(StatutVerificationDocument.REJETE, reponse.statut());
		assertEquals(Suite.ANNULER, reponse.suite());
	}

	@Test
	void pieceExpireeRejetee() {
		VerificationDocumentReponse reponse = verifier("PIECE_EXPIREE",
				new SaisieIdentite("Durand", "Sophie", LocalDate.of(1978, 11, 22), null));
		assertEquals(StatutVerificationDocument.REJETE, reponse.statut());
		assertEquals("document expiré", reponse.motif());
		assertEquals(Suite.ANNULER, reponse.suite());
		assertEquals("document expiré", enregistree().getMotif());
	}

	@Test
	void imageIllisibleAReprendreAvecMotif() {
		VerificationDocumentReponse reponse = verifier("IMAGE_ILLISIBLE", SAISIE_MARIE);
		assertEquals(StatutVerificationDocument.A_REPRENDRE, reponse.statut());
		assertTrue(reponse.motif().startsWith("image illisible"));
		assertNull(reponse.donnees());
		assertEquals(Suite.REPRENDRE, reponse.suite());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statutDossier());
	}

	@ParameterizedTest
	@CsvSource({
			"Dupond, Marie Claire, 1990-05-14, 2031-06-30, false, true, true, true",
			"Dupont, Marie, 1990-05-14, 2031-06-30, true, false, true, true",
			"Dupont, Marie Claire, 1990-05-15, 2031-06-30, true, true, false, true",
			"Dupont, Marie Claire, 1990-05-14, 2031-07-30, true, true, true, false" })
	void champQuiDiffereDeLaSaisieAnnuleLeDossier(String nom, String prenoms, LocalDate naissance,
			LocalDate expiration, boolean nomOk, boolean prenomsOk, boolean naissanceOk, boolean expirationOk) {
		VerificationDocumentReponse reponse = verifier("PIECE_VALIDE",
				new SaisieIdentite(nom, prenoms, naissance, expiration));

		assertEquals(new Concordance(nomOk, prenomsOk, naissanceOk, expirationOk), reponse.concordance());
		assertEquals(Suite.ANNULER, reponse.suite());
		assertEquals(StatutDossier.ANNULE, reponse.statutDossier());
		assertEquals(Messages.OUVERTURE_ANNULEE, reponse.message());
		assertEquals(StatutVerificationDocument.REJETE, reponse.statut());
		VerificationDocument enregistree = enregistree();
		assertEquals(nomOk, enregistree.getConcordanceNom());
		assertEquals(StatutVerificationDocument.REJETE, enregistree.getStatut());
		assertEquals("les informations de la pièce ne correspondent pas à la saisie", enregistree.getMotif());
	}

	@Test
	void pieceDUneAutrePersonneAnnuleLeDossier() {
		VerificationDocumentReponse reponse = verifier("PIECE_VALIDE_AUTRE_PERSONNE", SAISIE_MARIE);
		assertEquals(new Concordance(false, false, false, false), reponse.concordance());
		assertEquals(StatutVerificationDocument.REJETE, reponse.statut());
		assertEquals(StatutDossier.ANNULE, reponse.statutDossier());
	}

	@Test
	void erreurTechniquePermetDeReessayer() {
		VerificationDocumentReponse reponse = verifier("ERREUR_TECHNIQUE", SAISIE_MARIE);
		assertEquals(StatutVerificationDocument.ERREUR_TECHNIQUE, reponse.statut());
		assertEquals(Suite.REESSAYER, reponse.suite());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statutDossier());
		assertEquals(Messages.ERREUR_TECHNIQUE, reponse.message());
		assertEquals(1, reponse.tentatives());
	}

	@Test
	void serviceIndisponibleApresDeuxRelances() {
		VerificationDocumentReponse reponse = verifier("SERVICE_INDISPONIBLE", SAISIE_MARIE);
		assertEquals(StatutVerificationDocument.INDISPONIBLE, reponse.statut());
		assertEquals(3, reponse.tentatives());
		assertEquals(Suite.REESSAYER_PLUS_TARD, reponse.suite());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statutDossier());
		assertEquals("Vérification impossible, réessayez plus tard", reponse.message());
	}

	@Test
	void rectoEtVersoDePiecesDifferentesAReprendre() {
		VerificationDocumentReponse reponse = service.verifier(
				new VerificationDocumentRequete("dossier-1", "PIECE_VALIDE", "PIECE_EXPIREE", SAISIE_MARIE));
		assertEquals(StatutVerificationDocument.A_REPRENDRE, reponse.statut());
		assertEquals(Suite.REPRENDRE, reponse.suite());
	}

	@Test
	void pieceInconnueRefusee() {
		ResponseStatusException erreur = assertThrows(ResponseStatusException.class,
				() -> verifier("PERMIS_DE_CONDUIRE", SAISIE_MARIE));
		assertEquals(HttpStatus.BAD_REQUEST, erreur.getStatusCode());
		verify(verifications, never()).save(any(VerificationDocument.class));
	}

	@Test
	void champsObligatoires() {
		assertRefusee(new VerificationDocumentRequete(" ", "PIECE_VALIDE", "PIECE_VALIDE", SAISIE_MARIE));
		assertRefusee(new VerificationDocumentRequete("d", null, "PIECE_VALIDE", SAISIE_MARIE));
		assertRefusee(new VerificationDocumentRequete("d", "PIECE_VALIDE", "", SAISIE_MARIE));
		assertRefusee(new VerificationDocumentRequete("d", "PIECE_VALIDE", "PIECE_VALIDE", null));
		assertRefusee(new VerificationDocumentRequete("d", "PIECE_VALIDE", "PIECE_VALIDE",
				new SaisieIdentite(" ", "Marie", LocalDate.of(1990, 5, 14), null)));
		assertRefusee(new VerificationDocumentRequete("d", "PIECE_VALIDE", "PIECE_VALIDE",
				new SaisieIdentite("Dupont", null, LocalDate.of(1990, 5, 14), null)));
		assertRefusee(new VerificationDocumentRequete("d", "PIECE_VALIDE", "PIECE_VALIDE",
				new SaisieIdentite("Dupont", "Marie", null, null)));
		verify(verifications, never()).save(any(VerificationDocument.class));
	}

	private void assertRefusee(VerificationDocumentRequete requete) {
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.verifier(requete)).getStatusCode());
	}

	@Test
	void aucuneConcordanceEnregistreeQuandLaPieceNEstPasLue() {
		verifier("IMAGE_ILLISIBLE", SAISIE_MARIE);
		VerificationDocument enregistree = enregistree();
		assertNull(enregistree.getConcordanceNom());
		assertFalse(enregistree.getMotif().isBlank());
	}

}
