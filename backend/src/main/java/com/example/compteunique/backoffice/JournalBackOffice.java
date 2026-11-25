package com.example.compteunique.backoffice;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.evenements.MessageEvenement;
import com.example.compteunique.logging.Loggable;

/**
 * CU-382 (TECH-14) : journal back-office, premier module lecteur de Kafka et modèle pour les suivants. Il lit les
 * topics profil-client et catalogue sous son propre groupe (préfixe + « -journal ») et garde une trace de chaque
 * événement reçu. Un message n'est marqué « lu » qu'après l'enregistrement en base (validation après le traitement) ;
 * reçu deux fois, il n'est enregistré qu'une fois (règle 3 de l'ADR-001). La date gardée est la date métier du
 * message, jamais l'heure d'envoi de Kafka.
 */
@Service
@Loggable
public class JournalBackOffice {

	static final String MODULE = "journal";

	private final EvenementRecuRepository recus;
	private final Evenements evenements;
	private final DateMetier dateMetier;
	private final ConfigurationKafka configuration;

	public JournalBackOffice(EvenementRecuRepository recus, Evenements evenements, DateMetier dateMetier,
			ConfigurationKafka configuration) {
		this.recus = recus;
		this.evenements = evenements;
		this.dateMetier = dateMetier;
		this.configuration = configuration;
	}

	@KafkaListener(id = "journal-back-office",
			topics = { "#{@configurationKafka.nomDe('PROFIL_CLIENT')}", "#{@configurationKafka.nomDe('CATALOGUE')}" },
			groupId = "#{@configurationKafka.groupe('" + MODULE + "')}")
	@Transactional
	public void recevoir(ConsumerRecord<String, String> message) {
		MessageEvenement evenement = MessageEvenement.lire(message.value());
		if (!evenements.premiereFois(configuration.groupe(MODULE), evenement.id())) {
			return;
		}
		recus.save(new EvenementRecu(evenement.id(), evenement.type(), message.topic(), evenement.cle(),
				evenement.date(), message.value(), dateMetier.maintenant().toOffsetDateTime()));
	}

	/** Les 50 derniers événements reçus (page de test). */
	@Transactional(readOnly = true)
	public List<EvenementRecu> derniers() {
		return recus.findTop50ByOrderByIdDesc();
	}

}
