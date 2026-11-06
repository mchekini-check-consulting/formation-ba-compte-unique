package com.example.compteunique.connexion;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

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

import com.example.compteunique.securite.JournalIndisponibleException;

/**
 * Connexion à l'espace client.
 * <ul>
 * <li>POST /connexion (identifiant et mot de passe) : ouvre la session, portée par un cookie ;</li>
 * <li>GET /connexion/session : session en cours ;</li>
 * <li>POST /connexion/session/activite : action de la cliente ;</li>
 * <li>POST /connexion/deconnexion : fermeture de la session (CU-17).</li>
 * </ul>
 * Refus : 401 identifiants refusés, 429 trop de tentatives (CU-16, CU-48), 503 journal indisponible (CU-48).
 */
@RestController
@RequestMapping("/connexion")
public class ConnexionController {

	public static final String COOKIE_SESSION = "SESSION_CLIENT";
	static final String MESSAGE_REFUS = "Identifiant ou mot de passe incorrect";
	static final String INVITATION_ACTIVATION = "Votre espace n’est pas encore activé. Utilisez le lien d’activation reçu par SMS.";
	/** CU-48 critère 8 : texte exact du ticket. */
	static final String MESSAGE_JOURNAL_INDISPONIBLE = "Connexion momentanément impossible. Réessayez dans quelques minutes.";
	/** CU-18 critère 4 : un cookie a été présenté mais la session est expirée, révoquée ou inconnue. */
	static final String MOTIF_EXPIREE = "EXPIREE";

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

	/** reessayerDansSecondes : seulement pour un blocage (429), durée restante (CU-171). */
	public record RefusConnexion(String message, String invitation, Long reessayerDansSecondes) {

		public RefusConnexion(String message, String invitation) {
			this(message, invitation, null);
		}

	}

	/** CU-17 : session en cours et secondes restantes avant sa fermeture faute d'activité. */
	public record SessionEnCours(String identifiant, long expireDansSecondes) {
	}

	/** CU-18 : session refusée ; motif EXPIREE quand un cookie a été présenté. */
	public record SessionFermee(String motif) {
	}

	@PostMapping
	public ResponseEntity<SessionOuverte> connecter(@RequestBody DemandeConnexion demande, HttpServletRequest requete) {
		ResultatConnexion resultat = connexion.connecter(demande.identifiant(), demande.motDePasse(),
				requete.getRemoteAddr());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie(resultat.jetonSession()).build().toString())
				.body(new SessionOuverte(resultat.identifiant()));
	}

	/** Lecture de la session : ne prolonge pas le délai (rafraîchissement automatique, CU-17). */
	@GetMapping("/session")
	public ResponseEntity<Object> session(
			@CookieValue(name = COOKIE_SESSION, required = false) String jetonSession) {
		return reponse(connexion.etatSession(jetonSession), jetonSession);
	}

	/** CU-17 : action de la cliente (clic, saisie, changement de page) : le délai repart à zéro. */
	@PostMapping("/session/activite")
	public ResponseEntity<Object> activite(
			@CookieValue(name = COOKIE_SESSION, required = false) String jetonSession) {
		return reponse(connexion.activite(jetonSession), jetonSession);
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

	private static ResponseEntity<Object> reponse(Optional<ConnexionService.EtatSession> etat, String jetonSession) {
		if (etat.isPresent()) {
			return ResponseEntity.ok(new SessionEnCours(etat.get().identifiant(), etat.get().expireDansSecondes()));
		}
		if (jetonSession == null || jetonSession.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new SessionFermee(MOTIF_EXPIREE));
	}

	@ExceptionHandler(ConnexionRefuseeException.class)
	public ResponseEntity<RefusConnexion> refus(ConnexionRefuseeException refus) {
		String invitation = refus.isActivationAttendue() ? INVITATION_ACTIVATION : null;
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RefusConnexion(MESSAGE_REFUS, invitation));
	}

	/**
	 * CU-16 et CU-48 : blocage de l'identifiant ou de l'adresse IP. CU-171 : durée restante en
	 * secondes, dans l'en-tête standard Retry-After et dans le corps (l'écran lit le corps : le
	 * gateway n'expose pas les en-têtes au navigateur).
	 */
	@ExceptionHandler(TropDeTentativesException.class)
	public ResponseEntity<RefusConnexion> tropDeTentatives(TropDeTentativesException refus) {
		long secondes = refus.getRestantSecondes();
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(secondes))
				.body(new RefusConnexion(refus.getMessage(), null, secondes));
	}

	/** CU-48 critère 8 : journal de sécurité indisponible, la connexion est refusée. */
	@ExceptionHandler(JournalIndisponibleException.class)
	public ResponseEntity<RefusConnexion> journalIndisponible() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new RefusConnexion(MESSAGE_JOURNAL_INDISPONIBLE, null));
	}

}
