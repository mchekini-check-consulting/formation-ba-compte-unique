package com.example.compteunique.connexion;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// POST /connexion {identifiant, motDePasse} : ouvre la session (cookie) ; GET /connexion/session : session en cours.
@RestController
@RequestMapping("/connexion")
public class ConnexionController {

	static final String COOKIE_SESSION = "SESSION_CLIENT";
	static final String MESSAGE_REFUS = "Identifiant ou mot de passe incorrect";
	static final String INVITATION_ACTIVATION = "Utilisez le lien d'activation reçu par e-mail pour définir votre mot de passe.";

	private final ConnexionService connexion;

	public ConnexionController(ConnexionService connexion) {
		this.connexion = connexion;
	}

	public record DemandeConnexion(String identifiant, String motDePasse) {

		// Le mot de passe ne doit jamais apparaître dans un log.
		@Override
		public String toString() {
			return "DemandeConnexion[identifiant=" + identifiant + "]";
		}

	}

	public record SessionOuverte(String identifiant) {
	}

	public record RefusConnexion(String message, String invitation) {
	}

	@PostMapping
	public ResponseEntity<SessionOuverte> connecter(@RequestBody DemandeConnexion demande) {
		ResultatConnexion resultat = connexion.connecter(demande.identifiant(), demande.motDePasse());
		ResponseCookie cookie = ResponseCookie.from(COOKIE_SESSION, resultat.jetonSession())
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/")
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new SessionOuverte(resultat.identifiant()));
	}

	@GetMapping("/session")
	public ResponseEntity<SessionOuverte> session(
			@CookieValue(name = COOKIE_SESSION, required = false) String jetonSession) {
		return connexion.identifiantDeLaSession(jetonSession)
				.map(identifiant -> ResponseEntity.ok(new SessionOuverte(identifiant)))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	@ExceptionHandler(ConnexionRefuseeException.class)
	public ResponseEntity<RefusConnexion> refus(ConnexionRefuseeException refus) {
		String invitation = refus.isActivationAttendue() ? INVITATION_ACTIVATION : null;
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RefusConnexion(MESSAGE_REFUS, invitation));
	}

}
