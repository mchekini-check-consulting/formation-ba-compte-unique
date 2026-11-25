package com.example.compteunique.evenements;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.logging.Loggable;

/**
 * CU-382 (TECH-14) : fonctions publiques du socle d'événements (ADR-001, règles 2 et 3).
 * <ul>
 * <li>{@link #enregistrer} : un module enregistre un événement dans la boîte d'envoi, dans la même transaction que
 * son opération (jamais d'écriture directe dans Kafka) ; le relais le publie ensuite ;</li>
 * <li>{@link #premiereFois} : un module lecteur vérifie qu'il n'a pas déjà traité un événement reçu.</li>
 * </ul>
 */
@Service
@Loggable
public class Evenements {

	/** Version du format des événements (règle 2). 2 : ajout de l'heure métier de Paris (CU-413). */
	static final int VERSION = 2;

	private final EvenementADistribuerRepository aDistribuer;
	private final EvenementTraiteRepository traites;
	private final MessageEnErreurRepository enErreur;
	private final ConfigurationKafka configuration;
	private final DateMetier dateMetier;

	public Evenements(EvenementADistribuerRepository aDistribuer, EvenementTraiteRepository traites,
			MessageEnErreurRepository enErreur, ConfigurationKafka configuration, DateMetier dateMetier) {
		this.aDistribuer = aDistribuer;
		this.traites = traites;
		this.enErreur = enErreur;
		this.configuration = configuration;
		this.dateMetier = dateMetier;
	}

	/**
	 * Enregistre un événement à distribuer, daté de la date métier (TECH-12). Doit être appelé dans la transaction de
	 * l'opération : si elle échoue, ni l'opération ni l'événement n'existent.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID enregistrer(Topic topic, String type, String cle, Map<String, Object> contenu) {
		UUID id = UUID.randomUUID();
		aDistribuer.save(new EvenementADistribuer(id, type, configuration.nom(topic), cle, dateMetier.aujourdhui(),
				VERSION, MessageEvenement.json(contenu), dateMetier.maintenant().toOffsetDateTime()));
		return id;
	}

	/**
	 * Vrai la première fois que ce module (groupe) traite cet événement, faux s'il l'a déjà traité. À appeler dans la
	 * transaction du traitement : si le traitement échoue, l'événement n'est pas noté traité.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean premiereFois(String groupe, UUID evenementId) {
		return traites.noter(groupe, evenementId, dateMetier.maintenant().toOffsetDateTime()) == 1;
	}

	/** Un événement de la boîte d'envoi, pour suivre sa publication (ex. page /test/ordres). */
	@Transactional(readOnly = true)
	public Optional<EvenementADistribuer> aDistribuer(UUID evenementId) {
		return aDistribuer.findByEvenementId(evenementId);
	}

	/** Les 50 derniers événements de la boîte d'envoi (page de test). */
	@Transactional(readOnly = true)
	public List<EvenementADistribuer> derniersADistribuer() {
		return aDistribuer.findTop50ByOrderByIdDesc();
	}

	/** Les 50 derniers messages en erreur (page de test, back-office). */
	@Transactional(readOnly = true)
	public List<MessageEnErreur> derniersEnErreur() {
		return enErreur.findTop50ByOrderByIdDesc();
	}

}
