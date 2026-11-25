package com.example.compteunique.profil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// CU-32 : registre des consentements de CU-10 ouvert au texte d'information du Livret A.
class ConsentementsTest {

	private static final UUID CLIENTE = UUID.randomUUID();
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 25, 10, 0, 0, 0, ZoneOffset.UTC);

	private final ConsentementRepository repository = mock(ConsentementRepository.class);
	private final Consentements consentements = new Consentements(repository);

	@Test
	void acceptationEnregistreeAvecLaVersionEtLaDate() {
		consentements.accepter(CLIENTE, "TYPE", "V1", MAINTENANT);
		ArgumentCaptor<Consentement> enregistre = ArgumentCaptor.forClass(Consentement.class);
		verify(repository).save(enregistre.capture());
		assertEquals("TYPE", enregistre.getValue().getType());
		assertTrue(enregistre.getValue().isChoix());
	}

	@Test
	void versionDejaAccepteeOuNon() {
		when(repository.existsByCustomerIdAndTypeAndVersionTexteAndChoixTrue(CLIENTE, "TYPE", "V1")).thenReturn(true);
		assertTrue(consentements.aAccepte(CLIENTE, "TYPE", "V1"));
		assertFalse(consentements.aAccepte(CLIENTE, "TYPE", "V2"));
	}

}
