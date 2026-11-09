package com.example.compteunique.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.logging.CaptureLogs.Ligne;

class IdentifiantDemandeFilterTest {

	@RestController
	static class ControleurDeTest {

		private static final Logger log = LoggerFactory.getLogger(ControleurDeTest.class);

		@GetMapping("/exemple")
		public String exemple() {
			log.info("premiere ligne");
			log.info("seconde ligne");
			return "ok";
		}

	}

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ControleurDeTest())
			.addFilters(new IdentifiantDemandeFilter())
			.build();

	private static List<String> identifiants(List<Ligne> lignes) {
		return lignes.stream().map(ligne -> ligne.mdc().get(IdentifiantDemandeFilter.CLE_MDC)).toList();
	}

	@Test
	void chaqueRequeteASonIdentifiantPartageParToutesSesLignes() throws Exception {
		try (CaptureLogs logs = CaptureLogs.de(ControleurDeTest.class)) {
			mockMvc.perform(get("/exemple")).andExpect(status().isOk());
			mockMvc.perform(get("/exemple")).andExpect(status().isOk());

			List<String> identifiants = identifiants(logs.lignes());
			assertThat(identifiants).hasSize(4).doesNotContainNull();
			assertThat(identifiants.get(0)).isEqualTo(identifiants.get(1));
			assertThat(identifiants.get(2)).isEqualTo(identifiants.get(3));
			assertThat(identifiants.get(0)).isNotEqualTo(identifiants.get(2));
			Set<String> differents = identifiants.stream().collect(Collectors.toSet());
			assertThat(differents).allSatisfy(identifiant -> assertThat(UUID.fromString(identifiant)).isNotNull());
		}
		assertThat(MDC.get(IdentifiantDemandeFilter.CLE_MDC)).isNull();
	}

	@Test
	void repriseDeLEnTeteValideSansModifierLaReponse() throws Exception {
		try (CaptureLogs logs = CaptureLogs.de(ControleurDeTest.class)) {
			mockMvc.perform(get("/exemple").header(IdentifiantDemandeFilter.EN_TETE, "gw-1234.abc_def"))
					.andExpect(status().isOk())
					.andExpect(header().doesNotExist(IdentifiantDemandeFilter.EN_TETE));

			assertThat(identifiants(logs.lignes())).containsOnly("gw-1234.abc_def");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "marie dupont", "a;b", "<script>", "x\ny" })
	void enTeteInvalideRemplaceParUnUuid(String recu) {
		String identifiant = IdentifiantDemandeFilter.identifiant(recu);

		assertThat(identifiant).isNotEqualTo(recu);
		assertThat(UUID.fromString(identifiant)).isNotNull();
	}

	/** L'encodeur de logback-spring.xml écrit les clés MDC, donc requestId, dans chaque ligne JSON. */
	@Test
	void lEncodeurJsonEcritLIdentifiantDeDemande() {
		LoggerContext contexte = new LoggerContext();
		LogstashEncoder encodeur = new LogstashEncoder();
		encodeur.setContext(contexte);
		encodeur.start();
		LoggingEvent evenement = new LoggingEvent("fqcn", contexte.getLogger("exemple"), Level.INFO, "ligne", null,
				null);
		evenement.setMDCPropertyMap(Map.of(IdentifiantDemandeFilter.CLE_MDC, "gw-1234", "layer", "service"));

		String ligne = new String(encodeur.encode(evenement), StandardCharsets.UTF_8);

		assertThat(ligne).contains("\"requestId\":\"gw-1234\"").contains("\"layer\":\"service\"");
		encodeur.stop();
	}

	@Test
	void enTeteTropLongOuAbsentRemplaceParUnUuid() {
		assertThat(UUID.fromString(IdentifiantDemandeFilter.identifiant("a".repeat(65)))).isNotNull();
		assertThat(UUID.fromString(IdentifiantDemandeFilter.identifiant(null))).isNotNull();
		assertThat(IdentifiantDemandeFilter.identifiant("a".repeat(64))).isEqualTo("a".repeat(64));
	}

}
