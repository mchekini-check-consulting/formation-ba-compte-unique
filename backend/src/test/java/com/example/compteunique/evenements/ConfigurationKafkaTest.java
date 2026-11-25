package com.example.compteunique.evenements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.ConfigurationKafka.VariableManquante;

// CU-382 (TECH-14) : configuration Kafka lue dans les variables du helm.yaml.
class ConfigurationKafkaTest {

	static MockEnvironment complet() {
		MockEnvironment environnement = new MockEnvironment()
				.withProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
				.withProperty("KAFKA_TOPIC_SUFFIXE_ERREURS", ".dlt")
				.withProperty("KAFKA_GROUP_ID_PREFIXE", "cu-epargne");
		for (Topic topic : Topic.values()) {
			environnement.setProperty(topic.variable(),
					"cu-epargne." + topic.name().toLowerCase().replace('_', '-'));
		}
		return environnement;
	}

	@Test
	@DisplayName("Les noms des topics, du topic d'erreurs et des groupes viennent des variables du helm.yaml")
	void nomsLusDansLesVariables() {
		ConfigurationKafka configuration = new ConfigurationKafka(complet());

		assertThat(configuration.nom(Topic.PROFIL_CLIENT)).isEqualTo("cu-epargne.profil-client");
		assertThat(configuration.nomDe("ORDRES")).isEqualTo("cu-epargne.ordres");
		assertThat(configuration.topicErreurs("cu-epargne.ordres")).isEqualTo("cu-epargne.ordres.dlt");
		assertThat(configuration.groupe("journal")).isEqualTo("cu-epargne-journal");
	}

	@ParameterizedTest
	@ValueSource(strings = { "KAFKA_BOOTSTRAP_SERVERS", "KAFKA_TOPIC_ORDRES", "KAFKA_TOPIC_PROFIL_CLIENT",
		"KAFKA_TOPIC_SUFFIXE_ERREURS", "KAFKA_GROUP_ID_PREFIXE" })
	@DisplayName("Critère 2 : étant donné une variable absente du helm.yaml, quand l'application démarre, alors elle "
			+ "s'arrête avec un message qui nomme la variable manquante")
	void variableAbsente(String variable) {
		MockEnvironment environnement = complet();
		environnement.setProperty(variable, " ");

		assertThatThrownBy(() -> new ConfigurationKafka(environnement)).isInstanceOf(VariableManquante.class)
				.hasMessageContaining("la variable " + variable + " est absente");
	}

}
