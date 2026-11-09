package com.example.compteunique.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.logging.CaptureLogs.Ligne;

/**
 * CU-212 P1 : le filtre IdentifiantDemandeFilter est branché sur le vrai serveur HTTP de l'application. Chaque
 * requête de connexion a son requestId, partagé par sa ligne controller et ses lignes service ; un X-Request-Id
 * valide est repris ; la réponse ne renvoie pas l'en-tête. make test-integration ; Docker requis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IdentifiantDemandeIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Value("${local.server.port}")
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	private HttpResponse<String> connexion(String identifiant, String requestId)
			throws IOException, InterruptedException {
		HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/connexion"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						"{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\"Inconnu-123!\"}"));
		if (requestId != null) {
			requete.header(IdentifiantDemandeFilter.EN_TETE, requestId);
		}
		return client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Identifiants de demande des lignes controller (une par requête), dans l'ordre des requêtes. */
	private static List<String> identifiantsController(CaptureLogs controleur, int requetes) {
		Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> controleur.lignes().size() >= requetes);
		return controleur.lignes().stream().map(ligne -> ligne.mdc().get(IdentifiantDemandeFilter.CLE_MDC)).toList();
	}

	@Test
	void chaqueRequeteDeConnexionASonIdentifiantSurToutesSesLignes() throws Exception {
		try (CaptureLogs controleur = CaptureLogs.de(LogsControleur.class);
				CaptureLogs metier = CaptureLogs.de(LogsMetierAspect.class)) {
			HttpResponse<String> premiere = connexion("INCONNU01", null);
			HttpResponse<String> seconde = connexion("INCONNU02", null);

			assertThat(premiere.statusCode()).isEqualTo(401);
			assertThat(seconde.statusCode()).isEqualTo(401);
			assertThat(premiere.headers().firstValue(IdentifiantDemandeFilter.EN_TETE)).isEmpty();

			List<String> identifiants = identifiantsController(controleur, 2);
			assertThat(identifiants).hasSize(2).doesNotContainNull().doesNotHaveDuplicates();

			// Les lignes service (vérification, journal, erreur) portent l'identifiant de leur requête.
			List<Ligne> lignesMetier = metier.lignes();
			assertThat(lignesMetier).isNotEmpty();
			Set<String> identifiantsMetier = lignesMetier.stream()
					.map(ligne -> ligne.mdc().get(IdentifiantDemandeFilter.CLE_MDC))
					.collect(Collectors.toSet());
			assertThat(identifiantsMetier).containsExactlyInAnyOrderElementsOf(identifiants);
			assertThat(lignesMetier).allSatisfy(ligne -> assertThat(ligne.mdc()).containsEntry("layer", "service"));
		}
	}

	@Test
	void unIdentifiantValideRecuEstRepris() throws Exception {
		try (CaptureLogs controleur = CaptureLogs.de(LogsControleur.class);
				CaptureLogs metier = CaptureLogs.de(LogsMetierAspect.class)) {
			HttpResponse<String> reponse = connexion("INCONNU03", "gw-it-0001");

			assertThat(reponse.statusCode()).isEqualTo(401);
			assertThat(reponse.headers().firstValue(IdentifiantDemandeFilter.EN_TETE)).isEmpty();
			assertThat(identifiantsController(controleur, 1)).containsExactly("gw-it-0001");
			assertThat(Stream.concat(controleur.lignes().stream(), metier.lignes().stream()))
					.allSatisfy(ligne -> assertThat(ligne.mdc()).containsEntry(IdentifiantDemandeFilter.CLE_MDC,
							"gw-it-0001"));
		}
	}

}
