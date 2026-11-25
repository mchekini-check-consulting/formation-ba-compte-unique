package com.example.compteunique.evenements;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.example.compteunique.evenements.ConfigurationKafka.Topic;

// CU-382 (décision PO du 25/11/2026) : dans les 4 environnements, topics cu-epargne.<environnement>.<topic>, groupes
// de consommateurs cu-epargne-<environnement>-<module>, topic d'erreurs = topic + .dlt, et adresse des alertes
// déclarée. Lit le helm.yaml du dépôt, celui que la plateforme déploie.
class HelmTopicsTest {

	private static final List<String> ENVIRONNEMENTS = List.of("dev", "qualif", "preprod", "prod");

	@SuppressWarnings("unchecked")
	private static Map<String, Object> variablesDuBackend() throws IOException {
		Map<String, Object> helm = new Yaml().load(Files.readString(Path.of("..", "helm.yaml")));
		Map<String, Object> backend = ((List<Map<String, Object>>) helm.get("applications")).stream()
				.filter(application -> "backend".equals(application.get("name")))
				.findFirst()
				.orElseThrow();
		return ((List<Map<String, Object>>) backend.get("envList")).stream()
				.collect(java.util.stream.Collectors.toMap(v -> (String) v.get("key"), v -> v.get("value")));
	}

	@SuppressWarnings("unchecked")
	private static String valeur(Object valeur, String environnement) {
		return valeur instanceof Map<?, ?> parEnvironnement
				? String.valueOf(((Map<String, Object>) parEnvironnement).get(environnement))
				: String.valueOf(valeur);
	}

	@Test
	@DisplayName("Critère 2 : en dev, un événement est publié dans cu-epargne.dev.<topic> ; en qualif, "
			+ "cu-epargne.qualif.<topic> ; de même en preprod et prod")
	void topicsParEnvironnement() throws IOException {
		Map<String, Object> variables = variablesDuBackend();
		for (Topic topic : Topic.values()) {
			String suffixe = topic.name().toLowerCase().replace('_', '-');
			for (String environnement : ENVIRONNEMENTS) {
				assertThat(valeur(variables.get(topic.variable()), environnement))
						.as(topic.variable() + " en " + environnement)
						.isEqualTo("cu-epargne." + environnement + "." + suffixe);
			}
		}
	}

	@Test
	@DisplayName("Groupes cu-epargne-<environnement>-<module>, topic d'erreurs = topic + .dlt, adresse d'alerte "
			+ "déclarée dans chaque environnement")
	void groupesErreursEtAlertes() throws IOException {
		Map<String, Object> variables = variablesDuBackend();
		for (String environnement : ENVIRONNEMENTS) {
			assertThat(valeur(variables.get("KAFKA_GROUP_ID_PREFIXE"), environnement))
					.isEqualTo("cu-epargne-" + environnement);
			assertThat(valeur(variables.get("KAFKA_TOPIC_SUFFIXE_ERREURS"), environnement)).isEqualTo(".dlt");
			assertThat(variables.get("ALERTE_EQUIPE_EMAIL")).as("ALERTE_EQUIPE_EMAIL déclarée").isNotNull();
		}
		assertThat(valeur(variables.get("ALERTE_EQUIPE_EMAIL"), "dev")).isEqualTo("alertes-equipe@hitechbank.test");
		assertThat(valeur(variables.get("ALERTE_EQUIPE_EMAIL"), "qualif")).isEqualTo("alertes-equipe@hitechbank.test");
	}

}
