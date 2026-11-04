package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.connexion.ConnexionController.DemandeConnexion;
import com.example.compteunique.connexion.ConnexionController.RefusConnexion;
import com.example.compteunique.connexion.ConnexionController.SessionOuverte;

class ConnexionControllerTest {

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final ConnexionController controller = new ConnexionController(connexion);

	@Test
	void connecterPoseUnCookieProtegeEtNeRenvoiePasLeJeton() {
		when(connexion.connecter("ABC", "secret")).thenReturn(new ResultatConnexion("ABC", "jeton"));

		ResponseEntity<SessionOuverte> reponse = controller.connecter(new DemandeConnexion("ABC", "secret"));

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
	void sessionValideRenvoieLIdentifiant() {
		when(connexion.identifiantDeLaSession("jeton")).thenReturn(Optional.of("ABC"));
		assertEquals(new SessionOuverte("ABC"), controller.session("jeton").getBody());
	}

	@Test
	void sessionAbsenteRenvoie401() {
		when(connexion.identifiantDeLaSession(null)).thenReturn(Optional.empty());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.session(null).getStatusCode());
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
		assertThat(reponse.getBody().invitation()).contains("lien", "SMS");
	}

}
