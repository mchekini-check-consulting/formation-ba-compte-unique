package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.compteunique.connexion.ConnexionController.DemandeConnexion;
import com.example.compteunique.connexion.ConnexionController.RefusConnexion;
import com.example.compteunique.connexion.ConnexionController.SessionEnCours;
import com.example.compteunique.connexion.ConnexionController.SessionFermee;
import com.example.compteunique.connexion.ConnexionController.SessionOuverte;
import com.example.compteunique.connexion.ConnexionService.EtatSession;

class ConnexionControllerTest {

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final ConnexionController controller = new ConnexionController(connexion);

	@Test
	void connecterPoseUnCookieProtegeEtNeRenvoiePasLeJeton() {
		MockHttpServletRequest requete = new MockHttpServletRequest();
		requete.setRemoteAddr("203.0.113.7");
		when(connexion.connecter("ABC", "secret", "203.0.113.7")).thenReturn(new ResultatConnexion("ABC", "jeton"));

		ResponseEntity<SessionOuverte> reponse = controller.connecter(new DemandeConnexion("ABC", "secret"), requete);

		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new SessionOuverte("ABC"), reponse.getBody());
		assertThat(reponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
				.contains("SESSION_CLIENT=jeton", "HttpOnly", "Secure", "SameSite=Strict", "Path=/");
	}

	@Test
	void leMotDePasseNApparaitJamaisDansLeTexteDeLaDemande() {
		assertThat(new DemandeConnexion("ABC", "secret").toString()).doesNotContain("secret");
	}

	@Test
	void sessionValideRenvoieLIdentifiantEtLeTempsRestantSansProlonger() {
		when(connexion.etatSession("jeton")).thenReturn(Optional.of(new EtatSession("ABC", 420)));
		assertEquals(new SessionEnCours("ABC", 420), controller.session("jeton").getBody());
		verify(connexion, never()).activite(any());
	}

	@Test
	void sessionAbsenteRenvoie401() {
		when(connexion.etatSession(null)).thenReturn(Optional.empty());
		ResponseEntity<Object> reponse = controller.session(null);
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertNull(reponse.getBody());
	}

	// CU-18 critère 4 : un cookie présenté mais refusé (expiré, révoqué, falsifié) → « Session expirée ».
	@Test
	void sessionRefuseeAvecUnCookieRenvoieLeMotifExpiree() {
		when(connexion.etatSession("perime")).thenReturn(Optional.empty());
		ResponseEntity<Object> reponse = controller.session("perime");
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new SessionFermee("EXPIREE"), reponse.getBody());
	}

	@Test
	void unBlocageRenvoie429AvecLeMessageDuTicket() {
		ResponseEntity<RefusConnexion> reponse = controller
				.tropDeTentatives(TropDeTentativesException.identifiant(BlocageIdentifiant.DUREE_BLOCAGE));
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, reponse.getStatusCode());
		assertEquals("Trop de tentatives. Réessayez dans 5 minutes.", reponse.getBody().message());
		assertNull(reponse.getBody().invitation());

		assertEquals("Trop de tentatives depuis votre connexion. Réessayez plus tard.",
				controller.tropDeTentatives(TropDeTentativesException.adresseIp()).getBody().message());
	}

	@Test
	void unJournalIndisponibleRenvoie503AvecLeMessageDuTicket() {
		ResponseEntity<RefusConnexion> reponse = controller.journalIndisponible();
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, reponse.getStatusCode());
		assertEquals("Connexion momentanément impossible. Réessayez dans quelques minutes.",
				reponse.getBody().message());
	}

	@Test
	void uneActiviteProlongeLaSession() {
		when(connexion.activite("jeton")).thenReturn(Optional.of(new EtatSession("ABC", 600)));
		assertEquals(new SessionEnCours("ABC", 600), controller.activite("jeton").getBody());
	}

	@Test
	void uneActiviteSurUneSessionFermeeRenvoie401() {
		when(connexion.activite("jeton")).thenReturn(Optional.empty());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.activite("jeton").getStatusCode());
	}

	@Test
	void laDeconnexionRevoqueLaSessionEtSupprimeLeCookie() {
		ResponseEntity<Void> reponse = controller.deconnecter("jeton");

		verify(connexion).deconnecter("jeton");
		assertEquals(HttpStatus.NO_CONTENT, reponse.getStatusCode());
		assertThat(reponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
				.contains("SESSION_CLIENT=;", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict", "Path=/");
	}

	@Test
	void unRefusRenvoieLeMessageUniqueSansInvitation() {
		ResponseEntity<RefusConnexion> reponse = controller.refus(new ConnexionRefuseeException(false));
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals("Identifiant ou mot de passe incorrect", reponse.getBody().message());
		assertNull(reponse.getBody().invitation());
	}

	@Test
	void unCompteEnAttenteRecoitLeMemeMessageAvecInvitation() {
		ResponseEntity<RefusConnexion> reponse = controller.refus(new ConnexionRefuseeException(true));
		assertEquals("Identifiant ou mot de passe incorrect", reponse.getBody().message());
		// Texte exact du ticket CU-15.
		assertEquals("Votre espace n’est pas encore activé. Utilisez le lien d’activation reçu par SMS.",
				reponse.getBody().invitation());
	}

}
