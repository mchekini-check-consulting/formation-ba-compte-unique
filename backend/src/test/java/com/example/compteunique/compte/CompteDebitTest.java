package com.example.compteunique.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// CU-147 : débit du compte par un virement ; pas de découvert, compte actif seulement.
class CompteDebitTest {

	private static final OffsetDateTime OUVERTURE = OffsetDateTime.parse("2026-10-01T09:00:00+02:00");
	private static final OffsetDateTime DEBIT = OffsetDateTime.parse("2026-11-11T10:00:00+01:00");

	private final Compte compte = new Compte(1L, UUID.randomUUID(), Iban.de("99000000001"), 7L, OUVERTURE);

	@BeforeEach
	void soldeDeCentEuros() {
		ReflectionTestUtils.setField(compte, "soldeCentimes", 10_000L);
	}

	@Test
	void leDebitDiminueLeSoldeEtSaDateDeMiseAJour() {
		compte.debiter(10_000, DEBIT);

		assertEquals(0L, compte.getSoldeCentimes());
		assertEquals(DEBIT, compte.getSoldeMisAJourLe());
	}

	@Test
	void unDebitSuperieurAuSoldeNulOuSurUnCompteClotureEstRefuse() {
		assertThrows(SoldeInsuffisantException.class, () -> compte.debiter(10_001, DEBIT));
		assertThrows(MontantInvalideException.class, () -> compte.debiter(0, DEBIT));
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);
		assertThrows(CompteNonActifException.class, () -> compte.debiter(1, DEBIT));
		assertEquals(10_000L, compte.getSoldeCentimes());
	}

}
