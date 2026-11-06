package com.example.compteunique.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionService;

// CU-170 : TOUTES les adresses du backend sont lues dans l'application démarrée ; chacune de
// celles qui ne sont pas dans SessionObligatoire.ROUTES_PUBLIQUES doit refuser (401) une demande
// sans session. Une nouvelle adresse oubliée ou rendue publique par erreur fait échouer ce test.
@SpringBootTest
@Testcontainers
class RoutesProtegeesIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private WebApplicationContext contexte;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping adresses;

	@Autowired
	private IdentifiantConnexionService identifiants;

	@Autowired
	private ConnexionService connexion;

	private MockMvc mvc;

	private record Route(HttpMethod methode, String adresse) {
	}

	@BeforeEach
	void demarrer() {
		mvc = MockMvcBuilders.webAppContextSetup(contexte).build();
	}

	private static boolean publique(String adresse) {
		PathContainer chemin = PathContainer.parsePath(adresse);
		return SessionObligatoire.ROUTES_PUBLIQUES.stream()
				.anyMatch(route -> PathPatternParser.defaultInstance.parse(route).matches(chemin));
	}

	/** Adresses protégées de l'application, variables remplacées par « 1 ». */
	private List<Route> routesProtegees() {
		List<Route> routes = new ArrayList<>();
		for (RequestMappingInfo info : adresses.getHandlerMethods().keySet()) {
			Set<RequestMethod> methodes = info.getMethodsCondition().getMethods();
			for (String modele : info.getPatternValues()) {
				String adresse = modele.replaceAll("\\{[^}]+}", "1");
				if (publique(adresse)) {
					continue;
				}
				if (methodes.isEmpty()) {
					routes.add(new Route(HttpMethod.GET, adresse));
				}
				methodes.forEach(methode -> routes.add(new Route(HttpMethod.valueOf(methode.name()), adresse)));
			}
		}
		return routes;
	}

	private int statut(Route route, Cookie... cookies) throws Exception {
		var demande = request(route.methode(), route.adresse()).contentType("application/json").content("{}");
		if (cookies.length > 0) {
			demande.cookie(cookies);
		}
		MvcResult resultat = mvc.perform(demande).andReturn();
		return resultat.getResponse().getStatus();
	}

	@Test
	void chaqueRouteProtegeeRefuseUneDemandeSansSession() throws Exception {
		List<Route> routes = routesProtegees();
		assertThat(routes).as("routes protégées trouvées").contains(new Route(HttpMethod.GET, "/comptes"));
		for (Route route : routes) {
			assertThat(statut(route)).as("%s %s sans session", route.methode(), route.adresse()).isEqualTo(401);
			assertThat(statut(route, new Cookie("SESSION_CLIENT", "jeton-revoque")))
					.as("%s %s avec une session inconnue", route.methode(), route.adresse()).isEqualTo(401);
		}
	}

	@Test
	void uneAdresseInconnueEstAussiRefuseeSansSession() throws Exception {
		assertThat(statut(new Route(HttpMethod.GET, "/items"))).isEqualTo(401);
		assertThat(statut(new Route(HttpMethod.GET, "/nouvelle-adresse"))).isEqualTo(401);
	}

	@Test
	void laRessourceItemsNExistePlus() throws Exception {
		assertThat(routesProtegees()).noneMatch(route -> route.adresse().startsWith("/items"));
		String jeton = sessionActive();
		assertThat(statut(new Route(HttpMethod.GET, "/items"), new Cookie("SESSION_CLIENT", jeton))).isEqualTo(404);
	}

	@Test
	void avecUneSessionActiveLaRouteProtegeeRepond() throws Exception {
		String jeton = sessionActive();
		assertThat(statut(new Route(HttpMethod.GET, "/comptes"), new Cookie("SESSION_CLIENT", jeton)))
				.isEqualTo(200);
	}

	private String sessionActive() {
		IdentifiantConnexion compte = identifiants.creer();
		identifiants.definirMotDePasse(compte.getId(), "Abcdefghij1!");
		return connexion.connecter(compte.getIdentifiant(), "Abcdefghij1!", "192.0.2.10").jetonSession();
	}

}
