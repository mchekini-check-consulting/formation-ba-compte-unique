package com.example.compteunique.evenements;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.datemetier.DateMetier;

/**
 * CU-382 (TECH-14) : relais de la boîte d'envoi vers Kafka (ADR-001, règle 3 et section 3 bis). Toutes les 2 secondes,
 * il publie les événements pas encore distribués, dans l'ordre d'enregistrement, avec leur clé. Un événement n'est
 * marqué « publié » qu'une fois enregistré par Kafka (acks=all, producteur idempotent). Kafka indisponible : rien n'est
 * perdu, le relais s'arrête au premier échec (pour garder l'ordre) et réessaie au passage suivant.
 */
@Service
public class RelaisEvenements {

	private static final Logger log = LoggerFactory.getLogger(RelaisEvenements.class);

	private final EvenementADistribuerRepository aDistribuer;
	private final KafkaOperations<String, String> kafka;
	private final DateMetier dateMetier;
	private final long attenteMs;

	public RelaisEvenements(EvenementADistribuerRepository aDistribuer, KafkaOperations<String, String> kafka,
			DateMetier dateMetier, @Value("${app.kafka.attente-envoi-ms:10000}") long attenteMs) {
		this.aDistribuer = aDistribuer;
		this.kafka = kafka;
		this.dateMetier = dateMetier;
		this.attenteMs = attenteMs;
	}

	/** Publie les événements en attente ; renvoie le nombre d'événements publiés lors de ce passage. */
	@Transactional
	public int publierEnAttente() {
		if (!aDistribuer.verrouiller()) {
			return 0;
		}
		List<EvenementADistribuer> lot = aDistribuer.enAttente();
		int publies = 0;
		for (EvenementADistribuer evenement : lot) {
			if (!envoyer(evenement)) {
				break;
			}
			evenement.publie(dateMetier.maintenant().toOffsetDateTime());
			publies++;
		}
		return publies;
	}

	private boolean envoyer(EvenementADistribuer evenement) {
		MessageEvenement message = new MessageEvenement(evenement.getEvenementId(), evenement.getType(),
				evenement.getCle(), evenement.getDateMetier(),
				DateMetier.heureDeParis(evenement.getCreeLe()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
				evenement.getVersion(),
				MessageEvenement.contenu(evenement.getContenu()));
		try {
			kafka.send(evenement.getTopic(), evenement.getCle(), message.json()).get(attenteMs, TimeUnit.MILLISECONDS);
			return true;
		} catch (InterruptedException interruption) {
			Thread.currentThread().interrupt();
			evenement.echec("publication interrompue");
		} catch (ExecutionException | TimeoutException | KafkaException indisponible) {
			evenement.echec("Kafka indisponible : " + indisponible.getClass().getSimpleName());
			MDC.put("layer", "service");
			try {
				log.warn("publication Kafka en attente : Kafka indisponible, nouvel essai au prochain passage");
			} finally {
				MDC.remove("layer");
			}
		}
		return false;
	}

}
