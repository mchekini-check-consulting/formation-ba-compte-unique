package com.example.compteunique.connexion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

// CU-17 : règles de la session (10 minutes sans activité, révocation).
class SessionClientTest {

	private static final OffsetDateTime OUVERTURE = OffsetDateTime.parse("2026-11-05T10:00:00Z");

	@Test
	void uneNouvelleSessionCommenceParUneActiviteEtExpireDixMinutesPlusTard() {
		SessionClient session = new SessionClient(1L, "empreinte", OUVERTURE);

		assertEquals(OUVERTURE, session.getDerniereActiviteLe());
		assertEquals(OUVERTURE.plusMinutes(10), session.expireLe());
		assertTrue(session.estActive(OUVERTURE.plusSeconds(599)));
		assertFalse(session.estActive(OUVERTURE.plusMinutes(10)));
		assertNull(session.getRevoqueeLe());
	}

	@Test
	void uneActiviteRepousseLExpiration() {
		SessionClient session = new SessionClient(1L, "empreinte", OUVERTURE);
		session.activite(OUVERTURE.plusMinutes(7));

		assertTrue(session.estActive(OUVERTURE.plusMinutes(16)));
		assertEquals(OUVERTURE.plusMinutes(17), session.expireLe());
	}

	@Test
	void uneSessionRevoqueeNEstPlusJamaisActiveEtGardeSaPremiereDateDeRevocation() {
		SessionClient session = new SessionClient(1L, "empreinte", OUVERTURE);
		session.revoquer(OUVERTURE.plusMinutes(1));
		session.revoquer(OUVERTURE.plusMinutes(2));
		session.activite(OUVERTURE.plusMinutes(3));

		assertTrue(session.estRevoquee());
		assertEquals(OUVERTURE.plusMinutes(1), session.getRevoqueeLe());
		assertFalse(session.estActive(OUVERTURE.plusMinutes(3)));
	}

}
