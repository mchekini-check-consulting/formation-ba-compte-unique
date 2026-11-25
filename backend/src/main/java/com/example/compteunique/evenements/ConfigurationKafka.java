package com.example.compteunique.evenements;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * CU-382 (TECH-14) : configuration Kafka déclarée dans le helm.yaml (applications[].envList, ADR-001 section 3 bis).
 * Les noms des topics viennent uniquement de ces variables, jamais du code. Au démarrage, une variable absente ou vide
 * arrête l'application avec un message qui la nomme.
 */
@Component
public class ConfigurationKafka {

	/** Topics du projet, chacun lu dans sa variable d'environnement. */
	public enum Topic {
		ORDRES("KAFKA_TOPIC_ORDRES"),
		ORDRES_EXECUTES("KAFKA_TOPIC_ORDRES_EXECUTES"),
		MOUVEMENTS("KAFKA_TOPIC_MOUVEMENTS"),
		CATALOGUE("KAFKA_TOPIC_CATALOGUE"),
		PROFIL_CLIENT("KAFKA_TOPIC_PROFIL_CLIENT"),
		RAPPROCHEMENT("KAFKA_TOPIC_RAPPROCHEMENT");

		private final String variable;

		Topic(String variable) {
			this.variable = variable;
		}

		public String variable() {
			return variable;
		}

	}

	static final String BOOTSTRAP = "KAFKA_BOOTSTRAP_SERVERS";
	static final String SUFFIXE_ERREURS = "KAFKA_TOPIC_SUFFIXE_ERREURS";
	static final String GROUPE_PREFIXE = "KAFKA_GROUP_ID_PREFIXE";

	private final Map<Topic, String> topics = new EnumMap<>(Topic.class);
	private final String suffixeErreurs;
	private final String groupePrefixe;

	public ConfigurationKafka(Environment environnement) {
		obligatoire(environnement, BOOTSTRAP);
		for (Topic topic : Topic.values()) {
			topics.put(topic, obligatoire(environnement, topic.variable()));
		}
		this.suffixeErreurs = obligatoire(environnement, SUFFIXE_ERREURS);
		this.groupePrefixe = obligatoire(environnement, GROUPE_PREFIXE);
	}

	private static String obligatoire(Environment environnement, String variable) {
		String valeur = environnement.getProperty(variable);
		if (valeur == null || valeur.isBlank()) {
			throw new VariableManquante(variable);
		}
		return valeur.trim();
	}

	/** Nom du topic dans cet environnement (ex. cu-epargne.profil-client en dev). */
	public String nom(Topic topic) {
		return topics.get(topic);
	}

	/** Nom du topic à partir du nom de l'énumération (ex. « PROFIL_CLIENT »), pour les annotations @KafkaListener. */
	public String nomDe(String topic) {
		return nom(Topic.valueOf(topic));
	}

	/** Topic d'erreurs d'un topic : son nom suivi du suffixe (ex. cu-epargne.profil-client.dlt). */
	public String topicErreurs(String topic) {
		return topic + suffixeErreurs;
	}

	/** Groupe de consommateurs d'un module (ex. cu-epargne-journal) : chaque module lit sous son propre groupe. */
	public String groupe(String module) {
		return groupePrefixe + "-" + module;
	}

	/** Variable Kafka absente du helm.yaml : l'application ne démarre pas. */
	public static class VariableManquante extends IllegalStateException {

		VariableManquante(String variable) {
			super("Configuration Kafka incomplète : la variable " + variable
					+ " est absente. Déclarez-la dans helm.yaml (applications[].envList).");
		}

	}

}
