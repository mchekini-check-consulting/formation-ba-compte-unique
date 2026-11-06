package com.example.compteunique.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.dossier.Categories.MotifAnnulation;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.securite.JournalSecurite;

// CU-9 critères 5 à 7 : annulation quotidienne des dossiers sans action depuis 30 jours.
class InactiviteDossiersTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-06T04:00:00Z");

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final JournalSecurite journal = mock(JournalSecurite.class);
	private final InactiviteDossiers tache = new InactiviteDossiers(dossiers, journal,
			Clock.fixed(MAINTENANT, ZoneOffset.UTC));

	@Test
	void seulsLesDossiersEnVerificationHorsAnalysteSansActionDepuis30JoursSontAnnules() {
		Dossier inactif = new Dossier("inactif");
		when(dossiers.findByStatutAndAttenteAnalysteFalseAndDerniereActionLeBefore(any(), any()))
				.thenReturn(List.of(inactif));

		assertEquals(1, tache.annulerLesDossiersInactifs());

		verify(dossiers).findByStatutAndAttenteAnalysteFalseAndDerniereActionLeBefore(
				StatutDossier.EN_COURS_DE_VERIFICATION, OffsetDateTime.parse("2026-10-07T04:00:00Z"));
		assertEquals(StatutDossier.ANNULE, inactif.getStatut());
		assertEquals(MotifAnnulation.INACTIVITE, inactif.getMotifAnnulation());
		ArgumentCaptor<EvenementSecurite> evenement = ArgumentCaptor.forClass(EvenementSecurite.class);
		verify(journal).enregistrer(evenement.capture());
		assertEquals(EvenementSecurite.ANNULATION_INACTIVITE, evenement.getValue().getEvenement());
		assertEquals("INACTIVITE", evenement.getValue().getMotif());
	}

	@Test
	void leConstructeurSpringUtiliseLHorlogeSysteme() {
		when(dossiers.findByStatutAndAttenteAnalysteFalseAndDerniereActionLeBefore(any(), any())).thenReturn(List.of());
		assertEquals(0, new InactiviteDossiers(dossiers, journal).annulerLesDossiersInactifs());
	}

}
