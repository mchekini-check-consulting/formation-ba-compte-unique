package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.securite.JournalIndisponibleException;
import com.example.compteunique.securite.JournalSecurite;

class ConnexionServiceTest {

	private static final String MOT_DE_PASSE = "Abcdefghij1!";

	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final SessionClientRepository sessions = mock(SessionClientRepository.class);
	private final ProtectionConnexion protection = mock(ProtectionConnexion.class);
	private final JournalSecurite journal = mock(JournalSecurite.class);
	private final PasswordEncoder hacheur = spy(new HachageConfig().hacheurMotDePasse());

	{
		when(protection.verifierIdentifiant(anyString(), any())).thenAnswer(appel -> new BlocageIdentifiant(appel.getArgument(0)));
	}
	private final ConnexionService service = new ConnexionService(identifiants, sessions, hacheur, protection, journal);

	private IdentifiantConnexion compteActif() {
		IdentifiantConnexion compte = new IdentifiantConnexion("ABC12345");
		compte.definirMotDePasse(hacheur.encode(MOT_DE_PASSE), OffsetDateTime.now());
		when(identifiants.findByIdentifiant("ABC12345")).thenReturn(Optional.of(compte));
		return compte;
	}

	@Test
	void identifiantsValidesOuvrentUneSessionSansJetonEnClairEtRemettentLeCompteurAZero() {
		IdentifiantConnexion compte = compteActif();
		ReflectionTestUtils.setField(compte, "compteurErreurs", 2);

		ResultatConnexion resultat = service.connecter("ABC12345", MOT_DE_PASSE, "10.0.0.1");

		assertEquals("ABC12345", resultat.identifiant());
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
				() -> service.connecter("ABC12345", "mauvais", "10.0.0.1"));
		assertFalse(refus.isActivationAttendue());
		verify(sessions, never()).save(any());
	}

	@Test
	void identifiantInconnuEstRefuseCommeUnMauvaisMotDePasseApresUneComparaison() {
		when(identifiants.findByIdentifiant("INCONNU1")).thenReturn(Optional.empty());
		ConnexionRefuseeException refus = assertThrows(ConnexionRefuseeException.class,
				() -> service.connecter("INCONNU1", MOT_DE_PASSE, "10.0.0.1"));
		assertFalse(refus.isActivationAttendue());
		verify(hacheur, times(1)).matches(anyString(), anyString());
		verify(sessions, never()).save(any());
	}

	@Test
	void compteEnAttenteEstRefuseAvecInvitationALActivation() {
		when(identifiants.findByIdentifiant("ABC12345")).thenReturn(Optional.of(new IdentifiantConnexion("ABC12345")));
		ConnexionRefuseeException refus = assertThrows(ConnexionRefuseeException.class,
				() -> service.connecter("ABC12345", MOT_DE_PASSE, "10.0.0.1"));
		assertTrue(refus.isActivationAttendue());
		verify(hacheur, times(1)).matches(anyString(), anyString());
		verify(sessions, never()).save(any());
	}

	@Test
	void uneSessionConnueRenvoieSonIdentifiant() {
		IdentifiantConnexion compte = new IdentifiantConnexion("ABC12345");
		when(sessions.findByJetonHache(SecretsAleatoires.empreinte("jeton")))
				.thenReturn(Optional.of(new SessionClient(1L, "empreinte", OffsetDateTime.now())));
		when(identifiants.findById(1L)).thenReturn(Optional.of(compte));
		assertEquals(Optional.of("ABC12345"), service.identifiantDeLaSession("jeton"));
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
	private final ConnexionService serviceHorloge = new ConnexionService(identifiants, sessions, hacheur, protection, journal, horloge);

	private SessionClient sessionOuverte() {
		SessionClient session = new SessionClient(1L, "empreinte", OUVERTURE);
		when(sessions.findByJetonHache(SecretsAleatoires.empreinte("jeton"))).thenReturn(Optional.of(session));
		when(identifiants.findById(1L)).thenReturn(Optional.of(new IdentifiantConnexion("ABC12345")));
		return session;
	}

	@Test
	void a9Minutes59LaSessionEstActiveEtA10MinutesElleEstFermee() {
		SessionClient session = sessionOuverte();

		horloge.avancer(Duration.ofSeconds(599));
		assertEquals(Optional.of(new EtatSession("ABC12345", 1)), serviceHorloge.etatSession("jeton"));
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

		assertEquals(Optional.of(new EtatSession("ABC12345", 600)), serviceHorloge.activite("jeton"));
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

	// CU-16 et CU-48 : limitation des tentatives et journal d'authentification.

	private EvenementSecurite seulEvenementJournalise() {
		ArgumentCaptor<EvenementSecurite> evenement = ArgumentCaptor.forClass(EvenementSecurite.class);
		verify(journal).enregistrer(evenement.capture());
		return evenement.getValue();
	}

	@Test
	void uneTentativeIncompleteNEstNiCompteeNiJournalisee() {
		assertThrows(ConnexionRefuseeException.class, () -> service.connecter("1234567", MOT_DE_PASSE, "10.0.0.1"));
		assertThrows(ConnexionRefuseeException.class, () -> service.connecter("ABC12345", "", "10.0.0.1"));
		assertThrows(ConnexionRefuseeException.class, () -> service.connecter(null, MOT_DE_PASSE, "10.0.0.1"));
		assertThrows(ConnexionRefuseeException.class, () -> service.connecter("ABC12345", null, "10.0.0.1"));
		verifyNoInteractions(protection, journal, identifiants);
	}

	@Test
	void unEchecEstJournaliseAvecSonMotifEtCompte() {
		IdentifiantConnexion compte = compteActif();
		ReflectionTestUtils.setField(compte, "id", 7L);

		assertThrows(ConnexionRefuseeException.class, () -> service.connecter("ABC12345", "mauvais", "10.0.0.1"));

		EvenementSecurite echec = seulEvenementJournalise();
		assertEquals(EvenementSecurite.CONNEXION_ECHOUEE, echec.getEvenement());
		assertEquals(ProtectionConnexion.MOT_DE_PASSE_INCORRECT, echec.getMotif());
		assertEquals(EvenementSecurite.ECHEC, echec.getResultat());
		assertEquals("ABC12345", echec.getIdentifiantSaisi());
		assertEquals(7L, echec.getIdentifiantConnexionId());
		verify(protection).erreur(any(), any(), any());
	}

	@Test
	void unIdentifiantInconnuEstCompteCommeUneErreur() {
		when(identifiants.findByIdentifiant("INCONNU1")).thenReturn(Optional.empty());

		assertThrows(ConnexionRefuseeException.class, () -> service.connecter("INCONNU1", MOT_DE_PASSE, "10.0.0.1"));

		assertEquals(ProtectionConnexion.IDENTIFIANT_INCONNU, seulEvenementJournalise().getMotif());
		verify(protection).erreur(any(), any(), any());
	}

	@Test
	void uneConnexionReussieEstJournaliseeEtRemetLeCompteurAZero() {
		compteActif();

		service.connecter("ABC12345", MOT_DE_PASSE, "10.0.0.1");

		EvenementSecurite reussite = seulEvenementJournalise();
		assertEquals(EvenementSecurite.CONNEXION_REUSSIE, reussite.getEvenement());
		assertEquals(EvenementSecurite.REUSSITE, reussite.getResultat());
		assertNull(reussite.getMotif());
		verify(protection).reussite(any());
		verify(protection, never()).erreur(any(), any(), any());
	}

	@Test
	void unIdentifiantBloqueEstRefuseSansVerifierLeMotDePasse() {
		compteActif();
		when(protection.verifierIdentifiant(anyString(), any()))
				.thenThrow(TropDeTentativesException.identifiant(Duration.ofMinutes(3)));

		TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
				() -> service.connecter("ABC12345", MOT_DE_PASSE, "10.0.0.1"));

		assertEquals("Trop de tentatives. Réessayez dans 3 minutes.", refus.getMessage());
		verify(hacheur, never()).matches(anyString(), anyString());
		verify(sessions, never()).save(any());
	}

	@Test
	void uneAdresseBloqueeRefuseMemeDesIdentifiantsValides() {
		compteActif();
		doThrow(TropDeTentativesException.adresseIp()).when(protection).verifierAdresse(any(), any(), any());

		assertThrows(TropDeTentativesException.class, () -> service.connecter("ABC12345", MOT_DE_PASSE, "10.0.0.1"));

		verify(protection, never()).verifierIdentifiant(anyString(), any());
		verify(sessions, never()).save(any());
	}

	@Test
	void unJournalIndisponibleRefuseLaConnexion() {
		compteActif();
		doThrow(new JournalIndisponibleException(new IllegalStateException())).when(journal).enregistrer(any());

		assertThrows(JournalIndisponibleException.class, () -> service.connecter("ABC12345", MOT_DE_PASSE, "10.0.0.1"));

		verify(sessions, never()).save(any());
	}

	@Test
	void unIdentifiantTropLongEstTronqueALaTailleDeLaColonne() {
		String long40 = "1234567890123456789012345678901234567890";
		when(identifiants.findByIdentifiant(anyString())).thenReturn(Optional.empty());

		assertThrows(ConnexionRefuseeException.class, () -> service.connecter(long40, MOT_DE_PASSE, "10.0.0.1"));

		verify(identifiants).findByIdentifiant(long40.substring(0, 32));
	}

	@Test
	void lExpirationDeLaSessionEstJournaliseeUneSeuleFois() {
		sessionOuverte();
		horloge.avancer(Duration.ofMinutes(11));

		serviceHorloge.etatSession("jeton");
		serviceHorloge.idDeLaSession("jeton");

		EvenementSecurite expiration = seulEvenementJournalise();
		assertEquals(EvenementSecurite.EXPIRATION_SESSION, expiration.getEvenement());
		assertEquals("ABC12345", expiration.getIdentifiantSaisi());
		assertEquals(1L, expiration.getIdentifiantConnexionId());
	}

	@Test
	void laDeconnexionEstJournaliseeUneSeuleFois() {
		sessionOuverte();

		serviceHorloge.deconnecter("jeton");
		serviceHorloge.deconnecter("jeton");

		assertEquals(EvenementSecurite.DECONNEXION, seulEvenementJournalise().getEvenement());
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
