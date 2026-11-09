package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.logging.CaptureLogs;
import com.example.compteunique.logging.CaptureLogs.Ligne;
import com.example.compteunique.logging.LogsMetierAspect;
import com.example.compteunique.securite.JournalSecurite;

/**
 * CU-212 (US-LOG-03) : logs de la connexion sur la chaîne réelle ConnexionService → ProtectionConnexion →
 * JournalSecurite (aspect des logs métier posé comme dans l'application), bases simulées.
 */
class LogsConnexionFinitionsTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-11-09T17:00:00Z");
	private static final Clock HORLOGE = Clock.fixed(MAINTENANT.toInstant(), ZoneOffset.UTC);
	private static final String IDENTIFIANT = "ABC12345";
	private static final String MOT_DE_PASSE = "Abcdefghij1!";
	private static final String IP = "203.0.113.7";
	private static final long NUMERO = 42L;

	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final SessionClientRepository sessions = mock(SessionClientRepository.class);
	private final BlocageIdentifiantRepository blocagesIdentifiant = mock(BlocageIdentifiantRepository.class);
	private final BlocageAdresseIpRepository blocagesAdresse = mock(BlocageAdresseIpRepository.class);
	private final JournalSecuriteRepository evenements = mock(JournalSecuriteRepository.class);
	private final PasswordEncoder hacheur = spy(new HachageConfig().hacheurMotDePasse());
	private final JournalSecurite journal = avecLogs(new JournalSecurite(evenements));
	private final ProtectionConnexion protection = avecLogs(new ProtectionConnexion(blocagesIdentifiant,
			blocagesAdresse, evenements, journal, false));
	private final ConnexionService service = avecLogs(new ConnexionService(identifiants, sessions, hacheur,
			protection, journal, HORLOGE));

	@SuppressWarnings("unchecked")
	static <T> T avecLogs(T cible) {
		AspectJProxyFactory fabrique = new AspectJProxyFactory(cible);
		fabrique.setProxyTargetClass(true);
		fabrique.addAspect(new LogsMetierAspect());
		return (T) fabrique.getProxy();
	}

	static Ligne ligne(List<Ligne> lignes, String methode) {
		return lignes.stream()
				.filter(ligne -> methode.equals(ligne.mdc().get("method")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("aucune ligne pour " + methode + " : " + lignes));
	}

	private IdentifiantConnexion compteActif() {
		IdentifiantConnexion compte = new IdentifiantConnexion(IDENTIFIANT);
		compte.definirMotDePasse(hacheur.encode(MOT_DE_PASSE), MAINTENANT.minusDays(1));
		ReflectionTestUtils.setField(compte, "id", NUMERO);
		when(identifiants.findByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(compte));
		return compte;
	}

	private void identifiantBloque() {
		BlocageIdentifiant blocage = new BlocageIdentifiant(IDENTIFIANT);
		for (int i = 0; i < BlocageIdentifiant.ERREURS_MAX; i++) {
			blocage.erreur(MAINTENANT.minusMinutes(1));
		}
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(blocage));
	}

	// P2 : tentative pendant un blocage, identifiant existant.
	@Test
	void tentativePendantUnBlocageDonneLeNumeroTechniqueDeLaCliente() {
		compteActif();
		identifiantBloque();

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			assertThatThrownBy(() -> service.connecter(IDENTIFIANT, MOT_DE_PASSE, IP))
					.isInstanceOf(TropDeTentativesException.class)
					.hasMessage("Trop de tentatives. Réessayez dans 4 minutes.");

			Ligne verification = ligne(logs.lignes(), "ProtectionConnexion.verifierIdentifiant");
			assertThat(verification.niveau()).isEqualTo("WARN");
			assertThat(verification.mdc()).containsEntry("layer", "service")
					.containsEntry("identifiantConnexionId", String.valueOf(NUMERO))
					.containsEntry("err", "TropDeTentativesException");
		}
		// Comportement inchangé : pas de comparaison de mot de passe, journal sans numéro technique.
		verify(hacheur, never()).matches(anyString(), anyString());
		verify(sessions, never()).save(any());
		ArgumentCaptor<EvenementSecurite> evenement = ArgumentCaptor.forClass(EvenementSecurite.class);
		verify(evenements).saveAndFlush(evenement.capture());
		assertThat(evenement.getValue().getMotif()).isEqualTo(ProtectionConnexion.IDENTIFIANT_BLOQUE);
		assertThat(evenement.getValue().getIdentifiantConnexionId()).isNull();
	}

	@Test
	void tentativePendantUnBlocageDUnIdentifiantInconnuSansNumero() {
		when(identifiants.findByIdentifiant(IDENTIFIANT)).thenReturn(Optional.empty());
		identifiantBloque();

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			assertThatThrownBy(() -> service.connecter(IDENTIFIANT, MOT_DE_PASSE, IP))
					.isInstanceOf(TropDeTentativesException.class);

			assertThat(ligne(logs.lignes(), "ProtectionConnexion.verifierIdentifiant").mdc())
					.doesNotContainKey("identifiantConnexionId");
		}
	}

}
