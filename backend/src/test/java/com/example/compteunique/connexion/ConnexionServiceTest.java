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

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

}
