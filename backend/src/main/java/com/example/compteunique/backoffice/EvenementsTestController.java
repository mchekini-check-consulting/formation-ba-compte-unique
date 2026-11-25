package com.example.compteunique.backoffice;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.evenements.ConfigurationKafka;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.EvenementADistribuer;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.evenements.MessageEnErreur;

/**
 * CU-382 (TECH-14) : page de test /test/evenements (sans session, comme les autres pages /test/...).
 * <ul>
 * <li>GET /test/evenements : boîte d'envoi (publié ou en attente), événements reçus par le journal back-office,
 * messages en erreur ;</li>
 * <li>POST /test/evenements/message-invalide : envoie un message illisible dans le topic profil-client, pour vérifier
 * qu'il part dans le topic d'erreurs après 3 essais sans bloquer les suivants.</li>
 * </ul>
 * Absente en preprod et prod (EVENEMENTS_SIMULES=false, helm.yaml : 404).
 */
@RestController
@RequestMapping("/test/evenements")
@ConditionalOnProperty(name = "app.test.evenements-simules", havingValue = "true")
public class EvenementsTestController {

	static final String MESSAGE_INVALIDE = "ceci n'est pas un evenement";

	private final Evenements evenements;
	private final JournalBackOffice journal;
	private final KafkaOperations<String, String> kafka;
	private final ConfigurationKafka configuration;

	public EvenementsTestController(Evenements evenements, JournalBackOffice journal,
			KafkaOperations<String, String> kafka, ConfigurationKafka configuration) {
		this.evenements = evenements;
		this.journal = journal;
		this.kafka = kafka;
		this.configuration = configuration;
	}

	public record ADistribuerVue(UUID id, String type, String topic, String cle, LocalDate dateMetier,
			OffsetDateTime creeLe, OffsetDateTime publieLe, int essais, String derniereErreur) {

		static ADistribuerVue de(EvenementADistribuer e) {
			return new ADistribuerVue(e.getEvenementId(), e.getType(), e.getTopic(), e.getCle(), e.getDateMetier(),
					e.getCreeLe(), e.getPublieLe(), e.getEssais(), e.getDerniereErreur());
		}

	}

	public record RecuVue(UUID id, String type, String topic, String cle, LocalDate dateMetier, OffsetDateTime recuLe) {

		static RecuVue de(EvenementRecu e) {
			return new RecuVue(e.getEvenementId(), e.getType(), e.getTopic(), e.getCle(), e.getDateMetier(),
					e.getRecuLe());
		}

	}

	public record ErreurVue(String topic, String cle, String groupe, String motif, int essais,
			OffsetDateTime enErreurLe) {

		static ErreurVue de(MessageEnErreur e) {
			return new ErreurVue(e.getTopic(), e.getCle(), e.getGroupe(), e.getMotif(), e.getEssais(),
					e.getEnErreurLe());
		}

	}

	public record Etat(List<ADistribuerVue> aDistribuer, List<RecuVue> recus, List<ErreurVue> enErreur) {
	}

	@GetMapping
	public Etat lire() {
		return new Etat(evenements.derniersADistribuer().stream().map(ADistribuerVue::de).toList(),
				journal.derniers().stream().map(RecuVue::de).toList(),
				evenements.derniersEnErreur().stream().map(ErreurVue::de).toList());
	}

	@PostMapping("/message-invalide")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void envoyerMessageInvalide() {
		kafka.send(configuration.nom(Topic.PROFIL_CLIENT), "test-message-invalide", MESSAGE_INVALIDE);
	}

}
