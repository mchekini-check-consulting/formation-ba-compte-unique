package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.logging.CaptureLogs;
import com.example.compteunique.logging.CaptureLogs.Ligne;
import com.example.compteunique.logging.LogMetier;
import com.example.compteunique.logging.LogsControleur;
import com.example.compteunique.logging.LogsMetierAspect;
import com.example.compteunique.logging.RequestLoggingInterceptor;
import com.example.compteunique.securite.JournalIndisponibleException;
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

	// Messages métier.
	@Test
	void messagesMetierDuParcoursDeConnexion() {
		compteActif();
		BlocageIdentifiant compteur = new BlocageIdentifiant(IDENTIFIANT);
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(compteur));
		when(blocagesIdentifiant.existsById(IDENTIFIANT)).thenReturn(true);

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			assertThatThrownBy(() -> service.connecter(IDENTIFIANT, "mauvais", IP))
					.isInstanceOf(ConnexionRefuseeException.class);
			ResultatConnexion resultat = service.connecter(IDENTIFIANT, MOT_DE_PASSE, IP);
			when(sessions.findByJetonHache(SecretsAleatoires.empreinte(resultat.jetonSession())))
					.thenReturn(Optional.of(new SessionClient(NUMERO, "empreinte", MAINTENANT)));
			service.deconnecter(resultat.jetonSession());

			assertThat(logs.lignes()).extracting(ligne -> ligne.mdc().get("method"), Ligne::message).containsExactly(
					tuple("ProtectionConnexion.verifierIdentifiant", "service call completed"),
					tuple("JournalSecurite.enregistrer", "service call completed"),
					tuple("ProtectionConnexion.erreur", "erreur de connexion enregistrée"),
					tuple("ProtectionConnexion.verifierIdentifiant", "service call completed"),
					tuple("JournalSecurite.enregistrer", "service call completed"),
					tuple("ProtectionConnexion.reussite", "connexion réussie"),
					tuple("JournalSecurite.enregistrer", "service call completed"),
					tuple("ConnexionService.deconnecter", "déconnexion volontaire"));
		}
	}

	@Test
	void troisiemeErreurEtTentativePendantLeBlocage() {
		compteActif();
		BlocageIdentifiant compteur = new BlocageIdentifiant(IDENTIFIANT);
		compteur.erreur(MAINTENANT.minusMinutes(1));
		compteur.erreur(MAINTENANT.minusMinutes(1));
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(compteur));

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			assertThatThrownBy(() -> service.connecter(IDENTIFIANT, "mauvais", IP))
					.isInstanceOf(TropDeTentativesException.class);
			assertThatThrownBy(() -> service.connecter(IDENTIFIANT, MOT_DE_PASSE, IP))
					.isInstanceOf(TropDeTentativesException.class);

			assertThat(ligne(logs.lignes(), "ProtectionConnexion.erreur").message())
					.isEqualTo("erreur de connexion enregistrée, identifiant bloqué");
			assertThat(logs.lignes()).filteredOn(ligne -> "ProtectionConnexion.verifierIdentifiant"
					.equals(ligne.mdc().get("method"))).extracting(Ligne::message)
					.containsExactly("service call completed", "identifiant bloqué");
		}
	}

	// Requête HTTP en cours : le journal y lit l'adresse IP (elle doit rester hors des logs).
	@BeforeEach
	void requeteDepuisUneAdresseIp() {
		MockHttpServletRequest requete = new MockHttpServletRequest();
		requete.setRemoteAddr(IP);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));
	}

	@AfterEach
	void finDeRequete() {
		RequestContextHolder.resetRequestAttributes();
	}

	private static void sansDonneeSensible(List<Ligne> lignes, String... secrets) {
		assertThat(lignes).isNotEmpty().allSatisfy(ligne -> {
			assertThat(ligne.mdc().keySet()).noneMatch(cle -> cle.contains("identifiantSaisi")
					|| cle.contains("adresseIp") || cle.contains("motDePasse") || cle.contains("jeton"));
			for (String secret : secrets) {
				assertThat(ligne.message()).doesNotContain(secret);
				assertThat(ligne.mdc().values()).noneMatch(valeur -> valeur.contains(secret));
			}
		});
	}

	// L5 : adresse IP bloquée.
	@Test
	void adresseIpBloqueeJournaliseeSansLAdresse() {
		ProtectionConnexion limiteParAdresse = avecLogs(new ProtectionConnexion(blocagesIdentifiant, blocagesAdresse,
				evenements, journal, true));
		when(blocagesAdresse.findById(IP)).thenReturn(Optional.of(new BlocageAdresseIp(IP, MAINTENANT.plusMinutes(10))));

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			assertThatThrownBy(() -> limiteParAdresse.verifierAdresse(IP, IDENTIFIANT, MAINTENANT))
					.isInstanceOf(TropDeTentativesException.class);

			assertThat(ligne(logs.lignes(), "JournalSecurite.enregistrer").mdc())
					.containsEntry("evenement.evenement", EvenementSecurite.CONNEXION_ECHOUEE)
					.containsEntry("evenement.motif", ProtectionConnexion.ADRESSE_IP_BLOQUEE);
			sansDonneeSensible(logs.lignes(), IP, IDENTIFIANT);
		}
		// L'adresse est bien connue (enregistrée dans le journal en base), mais pas dans les logs.
		ArgumentCaptor<EvenementSecurite> evenement = ArgumentCaptor.forClass(EvenementSecurite.class);
		verify(evenements).saveAndFlush(evenement.capture());
		assertThat(evenement.getValue().getAdresseIp()).isEqualTo(IP);
	}

	@Test
	void blocageDeLAdresseAuVingtiemeEchecJournaliseSansLAdresse() {
		ProtectionConnexion limiteParAdresse = avecLogs(new ProtectionConnexion(blocagesIdentifiant, blocagesAdresse,
				evenements, journal, true));
		when(evenements.countByAdresseIpAndEvenementAndMotifInAndSurvenuLeAfter(any(), any(), any(), any()))
				.thenReturn((long) ProtectionConnexion.ECHECS_MAX_ADRESSE_IP);

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			assertThatThrownBy(() -> limiteParAdresse.verifierAdresse(IP, IDENTIFIANT, MAINTENANT))
					.isInstanceOf(TropDeTentativesException.class);

			assertThat(logs.lignes()).extracting(ligne -> ligne.mdc().get("evenement.evenement"))
					.containsExactly(EvenementSecurite.BLOCAGE_ADRESSE_IP, EvenementSecurite.CONNEXION_ECHOUEE);
			sansDonneeSensible(logs.lignes(), IP, IDENTIFIANT);
		}
	}

	// L6 : session expirée, journalisée une seule fois.
	@Test
	void expirationDeSessionJournaliseeUneSeuleFois() {
		SessionClient expiree = new SessionClient(NUMERO, "empreinte",
				MAINTENANT.minus(SessionClient.DELAI_INACTIVITE).minusMinutes(1));
		when(sessions.findByJetonHache(any())).thenReturn(Optional.of(expiree));

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
			assertThat(service.etatSession("jeton-expire")).isEmpty();
			assertThat(service.etatSession("jeton-expire")).isEmpty();
			assertThat(service.activite("jeton-expire")).isEmpty();

			assertThat(logs.lignes()).filteredOn(ligne -> EvenementSecurite.EXPIRATION_SESSION
					.equals(ligne.mdc().get("evenement.evenement"))).singleElement()
					.satisfies(ligne -> assertThat(ligne.mdc()).containsEntry("evenement.identifiantConnexionId",
							String.valueOf(NUMERO)));
			sansDonneeSensible(logs.lignes(), IP, "jeton-expire");
		}
		verify(evenements).saveAndFlush(any());
	}

	// L8 : journal de sécurité indisponible.
	@Test
	void journalIndisponibleSignaleEtExceptionPropagee() {
		compteActif();
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT))
				.thenReturn(Optional.of(new BlocageIdentifiant(IDENTIFIANT)));
		when(evenements.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("base indisponible"));

		try (CaptureLogs metier = CaptureLogs.de(LogsMetierAspect.class);
				CaptureLogs erreurs = CaptureLogs.de(JournalSecurite.class)) {
			assertThatThrownBy(() -> service.connecter(IDENTIFIANT, MOT_DE_PASSE, IP))
					.isInstanceOf(JournalIndisponibleException.class);

			assertThat(erreurs.lignes()).singleElement().satisfies(ligne -> {
				assertThat(ligne.niveau()).isEqualTo("ERROR");
				assertThat(ligne.message()).startsWith("Journal de sécurité indisponible")
						.contains(EvenementSecurite.CONNEXION_REUSSIE);
				assertThat(ligne.mdc()).containsEntry("layer", "service");
			});
			Ligne echec = ligne(metier.lignes(), "JournalSecurite.enregistrer");
			assertThat(echec.niveau()).isEqualTo("WARN");
			assertThat(echec.message()).isEqualTo("service call failed");
			assertThat(echec.mdc()).containsEntry("err", "JournalIndisponibleException");
			sansDonneeSensible(erreurs.lignes(), IP, IDENTIFIANT, MOT_DE_PASSE);
		}
		verify(sessions, never()).save(any());
	}

	// Confidentialité : parcours complet, aucune donnée sensible dans les lignes capturées.
	@Test
	void parcoursCompletSansDonneeSensible() {
		compteActif();
		BlocageIdentifiant compteur = new BlocageIdentifiant(IDENTIFIANT);
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(compteur));

		try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class);
				CaptureLogs erreurs = CaptureLogs.de(JournalSecurite.class)) {
			assertThatThrownBy(() -> service.connecter(IDENTIFIANT, "mauvais-mot", IP))
					.isInstanceOf(ConnexionRefuseeException.class);
			ResultatConnexion resultat = service.connecter(IDENTIFIANT, MOT_DE_PASSE, IP);
			when(sessions.findByJetonHache(any())).thenReturn(Optional.of(new SessionClient(NUMERO, "empreinte",
					MAINTENANT)));
			service.deconnecter(resultat.jetonSession());

			assertThat(erreurs.lignes()).isEmpty();
			sansDonneeSensible(logs.lignes(), IDENTIFIANT, MOT_DE_PASSE, "mauvais-mot", IP,
					resultat.jetonSession());
		}
	}

	/** Aucun champ annoté des classes de la connexion ne désigne l'identifiant saisi ou l'adresse IP. */
	@Test
	void aucunChampAnnoteNeDesigneUneDonneePersonnelle() {
		List<String> champs = Stream.of(ConnexionController.class, ConnexionService.class, ProtectionConnexion.class,
				JournalSecurite.class)
				.flatMap(classe -> Arrays.stream(classe.getDeclaredMethods()))
				.filter(methode -> methode.isAnnotationPresent(LogMetier.class))
				.flatMap(methode -> Arrays.stream(methode.getAnnotation(LogMetier.class).champs()))
				.toList();

		assertThat(champs).isNotEmpty().noneMatch(champ -> champ.contains("identifiantSaisi")
				|| champ.contains("adresseIp") || champ.contains("motDePasse") || champ.contains("jeton"));
	}

	/** Le contrôleur POST /connexion n'écrit qu'une ligne controller par requête (pas de doublon). */
	@Test
	void unSeulMessageControllerParConnexion() throws Exception {
		ConnexionService connexion = mock(ConnexionService.class);
		when(connexion.connecter(any(), any(), any())).thenReturn(new ResultatConnexion(IDENTIFIANT, "jeton"));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(avecLogs(new ConnexionController(connexion)))
				.addInterceptors(new RequestLoggingInterceptor(), new LogsControleur())
				.build();

		try (CaptureLogs anciennes = CaptureLogs.de(RequestLoggingInterceptor.class);
				CaptureLogs detaillees = CaptureLogs.de(LogsControleur.class)) {
			mockMvc.perform(post("/connexion").contentType(MediaType.APPLICATION_JSON)
					.content("{\"identifiant\":\"" + IDENTIFIANT + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
					.andExpect(status().isOk());

			assertThat(anciennes.lignes()).isEmpty();
			assertThat(detaillees.lignes()).singleElement().satisfies(ligne -> {
				assertThat(ligne.message()).isEqualTo("http request handled");
				assertThat(ligne.mdc()).containsEntry("layer", "controller").containsEntry("route", "/connexion")
						.containsEntry("status", "200");
			});
		}
	}

}
