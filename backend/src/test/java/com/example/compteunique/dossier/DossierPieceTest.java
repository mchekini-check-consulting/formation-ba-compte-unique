package com.example.compteunique.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.TypePiece;

// CU-29 : pièce du dossier expirée, remplacée par la nouvelle pièce vérifiée à l'ouverture d'un Livret A.
class DossierPieceTest {

	private static final OffsetDateTime PLUS_TARD = OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

	@Test
	void pieceExpireeSeulementApresSaDateDExpiration() {
		Dossier dossier = ExempleDossier.pretASigner("ref-piece", ResultatControle.OK);
		LocalDate expiration = dossier.getDateExpiration();
		assertFalse(dossier.pieceExpireeLe(expiration.minusDays(1)));
		assertFalse(dossier.pieceExpireeLe(expiration));
		assertTrue(dossier.pieceExpireeLe(expiration.plusDays(1)));
	}

	@Test
	void nouvellePieceRemplaceCelleDuDossier() {
		Dossier dossier = ExempleDossier.pretASigner("ref-piece-2", ResultatControle.OK);
		String nom = dossier.getNomNaissance();
		LocalDate expiration = LocalDate.of(2036, 3, 1);
		dossier.remplacerPiece(TypePiece.PASSEPORT, "18AB12345", expiration, PLUS_TARD);
		assertEquals(TypePiece.PASSEPORT, dossier.getTypePiece());
		assertEquals("18AB12345", dossier.getNumeroPiece());
		assertEquals(expiration, dossier.getDateExpiration());
		assertEquals(PLUS_TARD, dossier.getDerniereActionLe());
		assertEquals(nom, dossier.getNomNaissance());
	}

}
