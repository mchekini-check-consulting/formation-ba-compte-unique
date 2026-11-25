package com.example.compteunique.evenements;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;

import com.example.compteunique.alertes.AlertesEquipe;
import com.example.compteunique.datemetier.DateMetier;

/**
 * CU-382 (TECH-14) : erreurs de lecture (ADR-001, section 3 bis, « Fiabilité »). Un message en échec est réessayé
 * 2 fois (3 essais en tout), puis déposé dans le topic d'erreurs (nom du topic + suffixe, ex. .dlt), noté en base,
 * signalé au back-office (log d'erreur, Grafana) et à l'équipe (alerte commune, CU-382). Les messages suivants
 * continuent d'être traités.
 */
@Configuration
class ErreursLecture {

	static final int ESSAIS = 3;

	private static final Logger log = LoggerFactory.getLogger(ErreursLecture.class);

	@Bean
	CommonErrorHandler gestionErreursKafka(KafkaOperations<String, String> kafka, ConfigurationKafka configuration,
			MessageEnErreurRepository enErreur, DateMetier dateMetier, AlertesEquipe alertes,
			TransactionTemplate transactions) {
		DeadLetterPublishingRecoverer versTopicErreurs = new DeadLetterPublishingRecoverer(kafka,
				(message, erreur) -> new TopicPartition(configuration.topicErreurs(message.topic()), -1));
		return new DefaultErrorHandler((message, erreur) -> {
			versTopicErreurs.accept(message, erreur);
			mettreDeCote(message, erreur, configuration.topicErreurs(message.topic()), enErreur, dateMetier,
					transactions, alertes);
		}, new FixedBackOff(1000L, ESSAIS - 1L));
	}

	static void mettreDeCote(ConsumerRecord<?, ?> message, Exception erreur, String topicErreurs,
			MessageEnErreurRepository enErreur, DateMetier dateMetier, TransactionTemplate transactions,
			AlertesEquipe alertes) {
		String groupe = erreur instanceof ListenerExecutionFailedException echec && echec.getGroupId() != null
				? echec.getGroupId()
				: "inconnu";
		transactions.executeWithoutResult(statut -> enErreur.save(new MessageEnErreur(message.topic(),
				message.key() == null ? null : message.key().toString(), groupe, motif(erreur), ESSAIS,
				dateMetier.maintenant().toOffsetDateTime())));
		MDC.put("layer", "service");
		try {
			// Alerte du back-office (Grafana « Logs métier ») : texte fixe, jamais le contenu du message.
			log.error("message Kafka en erreur apres 3 essais : depose dans le topic d'erreurs");
		} finally {
			MDC.remove("layer");
		}
		// CU-382 : alerte de l'équipe (e-mail et journal), sans le contenu du message ni sa clé.
		alertes.signaler("Topic d'erreurs", "Message en erreur après 3 essais, déposé dans le topic " + topicErreurs
				+ " (groupe " + groupe + ")");
	}

	/** Motif lisible : la cause d'origine (le listener l'enveloppe dans une exception technique). */
	static String motif(Throwable erreur) {
		Throwable cause = erreur;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
	}

}
