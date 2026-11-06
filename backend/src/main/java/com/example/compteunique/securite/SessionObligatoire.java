package com.example.compteunique.securite;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.connexion.ConnexionService;

// CU-170 : refus par défaut. Toute adresse du backend absente de ROUTES_PUBLIQUES exige une
// session active (cookie de session), sinon 401 avant même d'exécuter la demande. Une nouvelle
// adresse est donc protégée sans rien faire ; la rendre publique est un choix explicite, ici.
// Comme GET /connexion/session : motif EXPIREE quand un cookie a été présenté mais que la session
// est expirée, révoquée ou inconnue (CU-169, l'écran affiche alors « Session expirée »).
@Configuration
public class SessionObligatoire implements WebMvcConfigurer, HandlerInterceptor {

	/** Adresses accessibles sans session. Tout le reste exige une session active. */
	public static final List<String> ROUTES_PUBLIQUES = List.of(
			"/", // réponse « Hello, World! » (vérification que le backend répond)
			"/error", // page d'erreur de Spring
			"/connexion/**", // connexion, lecture de session et déconnexion : gèrent la session eux-mêmes
			"/activation/**", // activation de l'espace (lien reçu par SMS)
			"/reinitialisation/**", // mot de passe oublié
			"/dossiers/**", // parcours d'ouverture de compte, avant toute session
			"/test/**"); // pages de test (e-mails, SMS, journal) : vides hors environnement de test

	static final String CORPS_SESSION_EXPIREE = "{\"motif\":\"EXPIREE\"}";

	private final ConnexionService connexion;

	public SessionObligatoire(ConnexionService connexion) {
		this.connexion = connexion;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).excludePathPatterns(ROUTES_PUBLIQUES);
	}

	@Override
	public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object handler)
			throws IOException {
		if (HttpMethod.OPTIONS.matches(requete.getMethod())) {
			return true; // vérification CORS du navigateur, jamais de données
		}
		Optional<String> jeton = jetonSession(requete);
		if (jeton.flatMap(connexion::idDeLaSession).isPresent()) {
			return true;
		}
		reponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		if (jeton.isPresent()) {
			reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
			reponse.getWriter().write(CORPS_SESSION_EXPIREE);
		}
		return false;
	}

	private static Optional<String> jetonSession(HttpServletRequest requete) {
		Cookie[] cookies = requete.getCookies();
		return cookies == null ? Optional.empty()
				: Arrays.stream(cookies)
						.filter(cookie -> ConnexionController.COOKIE_SESSION.equals(cookie.getName()))
						.map(Cookie::getValue)
						.filter(valeur -> !valeur.isEmpty())
						.findFirst();
	}

}
