package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

/** Logs métier « Suivre la connexion d'une cliente » : champs approuvés par le BA, sans donnée personnelle. */
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

	private static ProtectionConnexion protection() {
		return avecLogs(new ProtectionConnexion(mock(BlocageIdentifiantRepository.class),
				mock(BlocageAdresseIpRepository.class), mock(JournalSecuriteRepository.class),
				mock(JournalSecurite.class), false));
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

	@Test
	void troisiemeErreurEcriteEnAlerteAvecLeCompteur() {
		ProtectionConnexion protection = protection();
		BlocageIdentifiant blocage = new BlocageIdentifiant(IDENTIFIANT);

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			protection.erreur(blocage, 42L, MAINTENANT);
			protection.erreur(blocage, 42L, MAINTENANT);
			assertThatThrownBy(() -> protection.erreur(blocage, 42L, MAINTENANT))
					.isInstanceOf(TropDeTentativesException.class);

			assertThat(logs.lignes()).extracting(ligne -> ligne.mdc().get("blocage.erreursConsecutives"))
					.containsExactly("1", "2", "3");
			Ligne blocageLigne = logs.lignes().get(2);
			assertThat(blocageLigne.niveau()).isEqualTo("WARN");
			assertThat(blocageLigne.mdc()).containsEntry("err", "TropDeTentativesException")
					.containsEntry("identifiantConnexionId", "42");
			assertThat(logs.lignes()).allSatisfy(ligne -> assertThat(ligne.mdc().values()).doesNotContain(IDENTIFIANT));
		}
	}

	@Test
	void verificationEtReussiteEcriventLesErreursAccumulees() {
		ProtectionConnexion protection = protection();

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			BlocageIdentifiant blocage = protection.verifierIdentifiant(IDENTIFIANT, null, MAINTENANT);
			blocage.erreur(MAINTENANT);
			protection.reussite(blocage);

			assertThat(logs.lignes()).extracting(ligne -> ligne.mdc().get("method"))
					.containsExactly("ProtectionConnexion.verifierIdentifiant", "ProtectionConnexion.reussite");
			assertThat(logs.lignes().get(0).mdc()).containsEntry("resultat.erreursConsecutives", "0");
			assertThat(logs.lignes().get(1).mdc()).containsEntry("blocage.erreursConsecutives", "1");
		}
	}

}
