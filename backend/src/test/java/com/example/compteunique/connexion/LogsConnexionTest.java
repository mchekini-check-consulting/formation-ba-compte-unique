package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.logging.CaptureLogs;
import com.example.compteunique.logging.CaptureLogs.Ligne;
import com.example.compteunique.logging.LogsMetierAspect;
import com.example.compteunique.securite.JournalSecurite;

/**
 * Logs métier « Suivre la connexion d'une cliente » : champs approuvés par le BA, sans donnée personnelle. Les
 * traces L1 à L7 de la connexion sont testées dans LogsConnexionFinitionsTest.
 */
class LogsConnexionTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-11-09T18:00:00+01:00");
	private static final String IDENTIFIANT = "marie.dupont";

	@SuppressWarnings("unchecked")
	private static <T> T avecLogs(T cible) {
		AspectJProxyFactory fabrique = new AspectJProxyFactory(cible);
		fabrique.setProxyTargetClass(true);
		fabrique.addAspect(new LogsMetierAspect());
		return (T) fabrique.getProxy();
	}

	@Test
	void journalEcritLeTypeLeMotifEtLeNumeroTechniqueDeLaCliente() {
		JournalSecurite journal = avecLogs(new JournalSecurite(mock(JournalSecuriteRepository.class)));

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.CONNEXION_ECHOUEE, IDENTIFIANT,
					42L, ProtectionConnexion.MOT_DE_PASSE_INCORRECT, MAINTENANT));

			Ligne ligne = logs.lignes().getFirst();
			assertThat(ligne.mdc()).containsEntry("layer", "service")
					.containsEntry("method", "JournalSecurite.enregistrer")
					.containsEntry("evenement.evenement", "CONNEXION_ECHOUEE")
					.containsEntry("evenement.motif", "MOT_DE_PASSE_INCORRECT")
					.containsEntry("evenement.identifiantConnexionId", "42");
			assertThat(ligne.mdc().values()).doesNotContain(IDENTIFIANT);
		}
	}

}
