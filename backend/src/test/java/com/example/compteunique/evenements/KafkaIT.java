package com.example.compteunique.evenements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.backoffice.EvenementRecu;
import com.example.compteunique.backoffice.JournalBackOffice;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.profil.ProfilClientService.Statut;
import com.example.compteunique.profil.PublicationStatutProfil;

// CU-382 (TECH-14) contre un vrai Kafka et un vrai PostgreSQL, jetables (Testcontainers), jamais le cluster partagé
// (critère 10). Lecture et relais activés pour ce test seulement.
@SpringBootTest(properties = { "app.test.date-metier-modifiable=true", "spring.kafka.listener.auto-startup=true",
	"app.kafka.relais-ms=200", "spring.kafka.consumer.properties.metadata.max.age.ms=1000" })
@Testcontainers
class KafkaIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Container
	@ServiceConnection
	static KafkaContainer kafkaConteneur = new KafkaContainer("apache/kafka:4.1.0");

	private static final Duration DELAI = Duration.ofSeconds(60);

	@Autowired
	private PublicationStatutProfil publication;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private DateMetier dateMetier;

	@Autowired
	private JournalBackOffice journal;

	@Autowired
	private Evenements evenements;

	@Autowired
	private KafkaOperations<String, String> kafka;

	@Autowired
	private ConfigurationKafka configuration;

	// Topics créés avant chaque test, comme le fait la plateforme dans les environnements : sans cela, le premier
	// envoi attend leur création automatique par un Kafka tout juste démarré, au-delà du délai du producteur (5 s).
	@BeforeEach
	void topicsCrees() throws InterruptedException {
		String topic = configuration.nom(Topic.PROFIL_CLIENT);
		try (Admin admin = Admin
				.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConteneur.getBootstrapServers()))) {
			for (String nom : List.of(topic, configuration.topicErreurs(topic))) {
				try {
					admin.createTopics(List.of(new NewTopic(nom, 1, (short) 1))).all().get();
				} catch (ExecutionException e) {
					if (!(e.getCause() instanceof TopicExistsException)) {
						throw new IllegalStateException("Création du topic " + nom + " impossible", e);
					}
				}
			}
		}
	}

	@AfterEach
	void dateReelle() {
		dateMetier.revenirALaDateReelle("Test CU-382");
	}

	private List<EvenementRecu> recus(String cle) {
		return journal.derniers().reversed().stream().filter(r -> cle.equals(r.getCle())).toList();
	}

	private static String message(UUID id, String cle) {
		return new MessageEvenement(id, "Test", cle, LocalDate.of(2026, 11, 25), 1, Map.of("rang", 1)).json();
	}

	@Test
	void unStatutDeProfilEstPublieAvecSaCleEtLaDateMetierPuisLuUneFois() {
		dateMetier.changer(LocalDateTime.of(2026, 12, 31, 11, 0), "Test CU-382");
		UUID cliente = UUID.randomUUID();

		transactions.executeWithoutResult(statut -> publication.changer(cliente, Statut.VALIDE, Statut.A_REVALIDER));

		await().atMost(DELAI).until(() -> evenements.derniersADistribuer().stream()
				.anyMatch(e -> e.getCle().equals(cliente.toString()) && e.getPublieLe() != null));
		ConsumerRecord<String, String> publie = lireDansKafka(configuration.nom(Topic.PROFIL_CLIENT),
				cliente.toString());
		MessageEvenement message = MessageEvenement.lire(publie.value());
		assertThat(message.type()).isEqualTo("StatutProfilModifie");
		assertThat(message.date()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(message.contenu()).containsOnlyKeys("statut", "ancienStatut").containsEntry("statut",
				"A_REVALIDER");

		await().atMost(DELAI).until(() -> recus(cliente.toString()).size() == 1);
		assertThat(recus(cliente.toString()).getFirst().getDateMetier()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void unMessageRecuDeuxFoisNestTraiteQuUneFoisEtLOrdreDUnContratEstGarde() {
		String contrat = "CONTRAT-" + UUID.randomUUID();
		UUID premier = UUID.randomUUID();
		UUID deuxieme = UUID.randomUUID();
		UUID troisieme = UUID.randomUUID();
		String topic = configuration.nom(Topic.PROFIL_CLIENT);

		kafka.send(topic, contrat, message(premier, contrat));
		kafka.send(topic, contrat, message(premier, contrat));
		kafka.send(topic, contrat, message(deuxieme, contrat));
		kafka.send(topic, contrat, message(troisieme, contrat));

		await().atMost(DELAI).until(() -> recus(contrat).size() == 3);
		assertThat(recus(contrat)).extracting(EvenementRecu::getEvenementId).containsExactly(premier, deuxieme,
				troisieme);
	}

	@Test
	void unMessageEnEchecTroisFoisPartDansLeTopicDErreursSansBloquerLesSuivants() {
		String topic = configuration.nom(Topic.PROFIL_CLIENT);
		String cleInvalide = "invalide-" + UUID.randomUUID();
		String cleSuivante = "suivant-" + UUID.randomUUID();

		kafka.send(topic, cleInvalide, "ceci n'est pas un evenement");
		kafka.send(topic, cleSuivante, message(UUID.randomUUID(), cleSuivante));

		await().atMost(DELAI).until(() -> evenements.derniersEnErreur().stream()
				.anyMatch(e -> cleInvalide.equals(e.getCle())));
		assertThat(evenements.derniersEnErreur()).filteredOn(e -> cleInvalide.equals(e.getCle())).singleElement()
				.satisfies(e -> {
					assertThat(e.getGroupe()).isEqualTo(configuration.groupe("journal"));
					assertThat(e.getEssais()).isEqualTo(3);
					assertThat(e.getMotif()).isEqualTo("message illisible (JSON attendu)");
				});
		assertThat(lireDansKafka(configuration.topicErreurs(topic), cleInvalide).value())
				.isEqualTo("ceci n'est pas un evenement");
		await().atMost(DELAI).until(() -> recus(cleSuivante).size() == 1);
	}

	private static ConsumerRecord<String, String> lireDansKafka(String topic, String cle) {
		Properties proprietes = new Properties();
		proprietes.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConteneur.getBootstrapServers());
		proprietes.put(ConsumerConfig.GROUP_ID_CONFIG, "verification-" + UUID.randomUUID());
		proprietes.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		proprietes.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		proprietes.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		try (KafkaConsumer<String, String> lecteur = new KafkaConsumer<>(proprietes)) {
			lecteur.subscribe(List.of(topic));
			List<ConsumerRecord<String, String>> trouves = new ArrayList<>();
			await().atMost(DELAI).until(() -> {
				lecteur.poll(Duration.ofMillis(500)).forEach(m -> {
					if (cle.equals(m.key())) {
						trouves.add(m);
					}
				});
				return !trouves.isEmpty();
			});
			return trouves.getFirst();
		}
	}

}
