package com.example.compteunique.evenements;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * CU-382 : format stable d'un événement dans Kafka (règle 2 de l'ADR-001) : identifiant unique, type, clé (numéro
 * de contrat, ISIN, identifiant client ou date métier), date métier, version du format, contenu. Les modules lecteurs
 * utilisent la date métier de ce message, jamais l'heure d'envoi de Kafka.
 */
public record MessageEvenement(UUID id, String type, String cle, LocalDate date, int version,
		Map<String, Object> contenu) {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Lit un message reçu de Kafka ; un message illisible ou incomplet est refusé (puis mis en erreur). */
	public static MessageEvenement lire(String json) {
		MessageEvenement message;
		try {
			message = JSON.readValue(json, MessageEvenement.class);
		} catch (JacksonException | IllegalArgumentException illisible) {
			throw new MessageIllisible("message illisible (JSON attendu)");
		}
		if (message == null || message.id() == null || message.type() == null || message.date() == null) {
			throw new MessageIllisible("message incomplet (id, type et date obligatoires)");
		}
		return message;
	}

	String json() {
		return JSON.writeValueAsString(this);
	}

	static String json(Map<String, Object> contenu) {
		return JSON.writeValueAsString(contenu);
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> contenu(String json) {
		return JSON.readValue(json, Map.class);
	}

	/** Message reçu qui ne respecte pas le format : il sera déposé dans le topic d'erreurs après 3 essais. */
	public static class MessageIllisible extends RuntimeException {

		MessageIllisible(String motif) {
			super(motif);
		}

	}

}
