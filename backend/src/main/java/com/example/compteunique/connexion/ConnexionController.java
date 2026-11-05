package com.example.compteunique.connexion;

import java.util.Optional;

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

// POST /connexion {identifiant, motDePasse} : ouvre la session (cookie) ; GET /connexion/session : session en cours ;
// POST /connexion/session/activite : action de la cliente ; POST /connexion/deconnexion (CU-17).
@RestController
@RequestMapping("/connexion")
public class ConnexionController {

	public static final String COOKIE_SESSION = "SESSION_CLIENT";
	static final String MESSAGE_REFUS = "Identifiant ou mot de passe incorrect";
	static final String INVITATION_ACTIVATION = "Votre espace n’est pas encore activé. Utilisez le lien d’activation reçu par SMS.";

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

	/** CU-17 : session en cours et secondes restantes avant sa fermeture faute d'activité. */
	public record SessionEnCours(String identifiant, long expireDansSecondes) {
	}

	@PostMapping
	public ResponseEntity<SessionOuverte> connecter(@RequestBody DemandeConnexion demande) {
		ResultatConnexion resultat = connexion.connecter(demande.identifiant(), demande.motDePasse());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie(resultat.jetonSession()).build().toString())
				.body(new SessionOuverte(resultat.identifiant()));
	}

	/** Lecture de la session : ne prolonge pas le délai (rafraîchissement automatique, CU-17). */
	@GetMapping("/session")
	public ResponseEntity<SessionEnCours> session(
			@CookieValue(name = COOKIE_SESSION, required = false) String jetonSession) {
		return reponse(connexion.etatSession(jetonSession));
	}

	/** CU-17 : action de la cliente (clic, saisie, changement de page) : le délai repart à zéro. */
	@PostMapping("/session/activite")
	public ResponseEntity<SessionEnCours> activite(
			@CookieValue(name = COOKIE_SESSION, required = false) String jetonSession) {
		return reponse(connexion.activite(jetonSession));
	}

	/** CU-17 : « Me déconnecter » : session révoquée et cookie supprimé, même si elle était déjà fermée. */
	@PostMapping("/deconnexion")
	public ResponseEntity<Void> deconnecter(
			@CookieValue(name = COOKIE_SESSION, required = false) String jetonSession) {
		connexion.deconnecter(jetonSession);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookie("").maxAge(0).build().toString())
				.build();
	}

	// CU-17 critère 8 : cookie HttpOnly, Secure et SameSite=Strict.
	private static ResponseCookie.ResponseCookieBuilder cookie(String valeur) {
		return ResponseCookie.from(COOKIE_SESSION, valeur)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/");
	}

	private static ResponseEntity<SessionEnCours> reponse(Optional<ConnexionService.EtatSession> etat) {
		return etat.map(e -> ResponseEntity.ok(new SessionEnCours(e.identifiant(), e.expireDansSecondes())))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	@ExceptionHandler(ConnexionRefuseeException.class)
	public ResponseEntity<RefusConnexion> refus(ConnexionRefuseeException refus) {
		String invitation = refus.isActivationAttendue() ? INVITATION_ACTIVATION : null;
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RefusConnexion(MESSAGE_REFUS, invitation));
	}

}
