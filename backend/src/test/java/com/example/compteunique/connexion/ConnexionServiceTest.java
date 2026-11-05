package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.connexion.ConnexionService.EtatSession;

class ConnexionServiceTest {

	private static final String MOT_DE_PASSE = "Abcdefghij1!";

	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final SessionClientRepository sessions = mock(SessionClientRepository.class);
	private final PasswordEncoder hacheur = spy(new HachageConfig().hacheurMotDePasse());
	private final ConnexionService service = new ConnexionService(identifiants, sessions, hacheur);

	private IdentifiantConnexion compteActif() {
		IdentifiantConnexion compte = new IdentifiantConnexion("ABC");
		compte.definirMotDePasse(hacheur.encode(MOT_DE_PASSE), OffsetDateTime.now());
		when(identifiants.findByIdentifiant("ABC")).thenReturn(Optional.of(compte));
		return compte;
	}

	@Test
	void identifiantsValidesOuvrentUneSessionSansJetonEnClairEtRemettentLeCompteurAZero() {
		IdentifiantConnexion compte = compteActif();
		ReflectionTestUtils.setField(compte, "compteurErreurs", 2);

		ResultatConnexion resultat = service.connecter("ABC", MOT_DE_PASSE);

		assertEquals("ABC", resultat.identifiant());
		assertEquals(0, compte.getCompteurErreurs());
		ArgumentCaptor<SessionClient> session = ArgumentCaptor.forClass(SessionClient.class);
		verify(sessions).save(session.capture());
		assertEquals(SecretsAleatoires.empreinte(resultat.jetonSession()), session.getValue().getJetonHache());
		assertThat(session.getValue().getJetonHache()).isNotEqualTo(resultat.jetonSession());
		assertThat(resultat.toString()).doesNotContain(resultat.jetonSession());
	}

	@Test
	void motDePasseIncorrectEstRefuseSansSession() {
		compteActif();
		ConnexionRefuseeException refus = assertThrows(ConnexionRefuseeException.class,
				() -> service.connecter("ABC", "mauvais"));
		assertFalse(refus.isActivationAttendue());
		verify(sessions, never()).save(any());
	}

	@Test
	void identifiantInconnuEstRefuseCommeUnMauvaisMotDePasseApresUneComparaison() {
		when(identifiants.findByIdentifiant("INCONNU")).thenReturn(Optional.empty());
		ConnexionRefuseeException refus = assertThrows(ConnexionRefuseeException.class,
				() -> service.connecter("INCONNU", MOT_DE_PASSE));
		assertFalse(refus.isActivationAttendue());
		verify(hacheur, times(1)).matches(anyString(), anyString());
		verify(sessions, never()).save(any());
	}

	@Test
	void compteEnAttenteEstRefuseAvecInvitationALActivation() {
		when(identifiants.findByIdentifiant("ABC")).thenReturn(Optional.of(new IdentifiantConnexion("ABC")));
		ConnexionRefuseeException refus = assertThrows(ConnexionRefuseeException.class,
				() -> service.connecter("ABC", MOT_DE_PASSE));
		assertTrue(refus.isActivationAttendue());
		verify(hacheur, times(1)).matches(anyString(), anyString());
		verify(sessions, never()).save(any());
	}

	@Test
	void uneSessionConnueRenvoieSonIdentifiant() {
		IdentifiantConnexion compte = new IdentifiantConnexion("ABC");
		when(sessions.findByJetonHache(SecretsAleatoires.empreinte("jeton")))
				.thenReturn(Optional.of(new SessionClient(1L, "empreinte", OffsetDateTime.now())));
		when(identifiants.findById(1L)).thenReturn(Optional.of(compte));
		assertEquals(Optional.of("ABC"), service.identifiantDeLaSession("jeton"));
	}

	@Test
	void uneSessionAbsenteOuInconnueNeRenvoieRien() {
		assertEquals(Optional.empty(), service.identifiantDeLaSession(null));
		when(sessions.findByJetonHache(anyString())).thenReturn(Optional.empty());
		assertEquals(Optional.empty(), service.identifiantDeLaSession("inconnu"));
	}

	@Test
	void uneSessionConnueRenvoieLIdTechniqueDeLaCliente() {
		when(sessions.findByJetonHache(SecretsAleatoires.empreinte("jeton")))
				.thenReturn(Optional.of(new SessionClient(1L, "empreinte", OffsetDateTime.now())));
		assertEquals(Optional.of(1L), service.idDeLaSession("jeton"));
		assertEquals(Optional.empty(), service.idDeLaSession(null));
	}

	// CU-17 : horloge simulée, session ouverte à 10:00.
	private static final OffsetDateTime OUVERTURE = OffsetDateTime.parse("2026-11-05T10:00:00Z");

	private final MutableClock horloge = new MutableClock(OUVERTURE.toInstant());
	private final ConnexionService serviceHorloge = new ConnexionService(identifiants, sessions, hacheur, horloge);

	private SessionClient sessionOuverte() {
		SessionClient session = new SessionClient(1L, "empreinte", OUVERTURE);
		when(sessions.findByJetonHache(SecretsAleatoires.empreinte("jeton"))).thenReturn(Optional.of(session));
		when(identifiants.findById(1L)).thenReturn(Optional.of(new IdentifiantConnexion("ABC")));
		return session;
	}

	@Test
	void a9Minutes59LaSessionEstActiveEtA10MinutesElleEstFermee() {
		SessionClient session = sessionOuverte();

		horloge.avancer(Duration.ofSeconds(599));
		assertEquals(Optional.of(new EtatSession("ABC", 1)), serviceHorloge.etatSession("jeton"));
		assertFalse(session.estRevoquee());

		horloge.avancer(Duration.ofSeconds(1));
		assertEquals(Optional.empty(), serviceHorloge.etatSession("jeton"));
		assertTrue(session.estRevoquee());
	}

	@Test
	void uneDemandeApresExpirationEstRefuseeMemeSiLEcranEstResteOuvert() {
		SessionClient session = sessionOuverte();
		horloge.avancer(Duration.ofMinutes(11));

		assertEquals(Optional.empty(), serviceHorloge.idDeLaSession("jeton"));
		assertEquals(Optional.empty(), serviceHorloge.activite("jeton"));
		assertTrue(session.estRevoquee());
	}

	@Test
	void uneActiviteRemetLeDelaiAZero() {
		SessionClient session = sessionOuverte();
		horloge.avancer(Duration.ofMinutes(8));

		assertEquals(Optional.of(new EtatSession("ABC", 600)), serviceHorloge.activite("jeton"));
		assertEquals(OUVERTURE.plusMinutes(8).toInstant(), session.getDerniereActiviteLe().toInstant());

		horloge.avancer(Duration.ofMinutes(9));
		assertEquals(Optional.of(1L), serviceHorloge.idDeLaSession("jeton"));
	}

	@Test
	void unRafraichissementAutomatiqueNeProlongePasLaSession() {
		SessionClient session = sessionOuverte();
		horloge.avancer(Duration.ofMinutes(5));
		serviceHorloge.etatSession("jeton");
		serviceHorloge.idDeLaSession("jeton");

		assertEquals(OUVERTURE.toInstant(), session.getDerniereActiviteLe().toInstant());
		horloge.avancer(Duration.ofMinutes(5));
		assertEquals(Optional.empty(), serviceHorloge.idDeLaSession("jeton"));
	}

	@Test
	void laDeconnexionRevoqueLaSessionPourTousLesOnglets() {
		SessionClient session = sessionOuverte();

		serviceHorloge.deconnecter("jeton");

		assertTrue(session.estRevoquee());
		assertEquals(Optional.empty(), serviceHorloge.etatSession("jeton"));
		assertEquals(Optional.empty(), serviceHorloge.idDeLaSession("jeton"));
		assertEquals(Optional.empty(), serviceHorloge.activite("jeton"));
	}

	@Test
	void seDeconnecterSansSessionNeFaitRien() {
		when(sessions.findByJetonHache(anyString())).thenReturn(Optional.empty());
		serviceHorloge.deconnecter(null);
		serviceHorloge.deconnecter("inconnu");
		verify(sessions, never()).save(any());
	}

	/** Horloge de test que l'on fait avancer à la main. */
	private static final class MutableClock extends Clock {

		private Instant maintenant;

		MutableClock(Instant depart) {
			this.maintenant = depart;
		}

		void avancer(Duration duree) {
			maintenant = maintenant.plus(duree);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return maintenant;
		}

	}

}
