package com.example.compteunique.ordres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.evenements.ConfigurationKafka;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.evenements.MessageEvenement;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;

// CU-376 (TECH-08) contre un vrai PostgreSQL et un vrai Kafka jetables (Testcontainers) : migration V30, annulation
// complète en cas d'échec (critère 5), doublon (critère 2) et publication dans le topic des ordres avec le numéro de
// contrat comme clé (critère 7, test CU-387). Le catalogue est simulé : seul le module Ordres est vérifié ici.
@SpringBootTest(properties = { "app.test.ordres-simules=true", "app.kafka.relais-ms=200" })
@Testcontainers
class OrdresIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Container
	@ServiceConnection
	static KafkaContainer kafkaConteneur = new KafkaContainer("apache/kafka:4.1.0");

	private static final String UC = "QZ0000000017";

	@Autowired
	private OrdresService service;

	@Autowired
	private OrdreRepository ordres;

	@Autowired
	private PositionsContrat positions;

	@Autowired
	private ConfigurationKafka configuration;

	@MockitoBean
	private CatalogueService catalogue;

	@MockitoSpyBean
	private Evenements evenements;

	@BeforeEach
	void catalogueEtTopic() throws InterruptedException {
		when(catalogue.support(anyString()))
				.thenAnswer(appel -> Optional.of(OrdresServiceTest.support(appel.getArgument(0), "25.00")));
		try (Admin admin = Admin
				.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConteneur.getBootstrapServers()))) {
			admin.createTopics(List.of(new NewTopic(configuration.nom(Topic.ORDRES), 1, (short) 1))).all().get();
		} catch (ExecutionException e) {
			if (!(e.getCause() instanceof TopicExistsException)) {
				throw new IllegalStateException("Création du topic des ordres impossible", e);
			}
		}
	}

	private static String contrat() {
		return "IT-" + UUID.randomUUID().toString().substring(0, 8);
	}

	@Test
	@DisplayName("Critère 5 : étant donné un ordre en cours d'enregistrement, quand l'enregistrement de l'événement "
			+ "échoue, alors ni l'ordre ni l'événement « ordre demandé » n'existent en base")
	void critere5AnnulationComplete() {
		UUID id = UUID.randomUUID();
		doThrow(new IllegalStateException("panne de la boîte d'envoi")).when(evenements)
				.enregistrer(eq(Topic.ORDRES), anyString(), anyString(), any());

		assertThatThrownBy(() -> service.passer(
				new DemandeOrdre(id, contrat(), TypeOrdre.VERSEMENT, null, UC, new BigDecimal("100"))))
				.isInstanceOf(IllegalStateException.class);

		assertThat(ordres.findById(id)).isEmpty();
	}

	@Test
	@DisplayName("Critères 1 et 2 en base réelle : rachat de 1 000 € sur 3 000 €, puis le même ordre renvoyé : un seul "
			+ "ordre, 2 000 € disponibles")
	void criteres1Et2EnBase() {
		String contrat = contrat();
		positions.enregistrerPositionTest(contrat, UC, new BigDecimal("3000"), null);
		DemandeOrdre rachat = new DemandeOrdre(UUID.randomUUID(), contrat, TypeOrdre.RACHAT, UC, null,
				new BigDecimal("1000"));

		assertThat(service.passer(rachat).nouveau()).isTrue();
		assertThat(service.passer(rachat).nouveau()).isFalse();

		assertThat(ordres.findAll()).filteredOn(o -> o.getNumeroContrat().equals(contrat)).hasSize(1);
		assertThat(service.montantDisponible(contrat, UC)).isEqualByComparingTo("2000");
	}

	@Test
	@DisplayName("Critère 7 / CU-387 : l'ordre enregistré, le relais publie « ordre demandé » dans le topic des ordres "
			+ "avec le numéro de contrat comme clé")
	void critere7PublieDansKafka() {
		String contrat = contrat();
		Ordre ordre = service.passer(new DemandeOrdre(UUID.randomUUID(), contrat, TypeOrdre.VERSEMENT, null, UC,
				new BigDecimal("250"))).ordre();

		ConsumerRecord<String, String> recu = lire(configuration.nom(Topic.ORDRES), contrat);

		MessageEvenement message = MessageEvenement.lire(recu.value());
		assertThat(message.id()).isEqualTo(ordre.getEvenementId());
		assertThat(message.type()).isEqualTo("OrdreDemande");
		assertThat(message.contenu()).containsEntry("montant", "250.00");
	}

	private ConsumerRecord<String, String> lire(String topic, String cle) {
		Properties proprietes = new Properties();
		proprietes.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConteneur.getBootstrapServers());
		proprietes.put(ConsumerConfig.GROUP_ID_CONFIG, "ordres-it-" + UUID.randomUUID());
		proprietes.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		try (KafkaConsumer<String, String> consommateur = new KafkaConsumer<>(proprietes, new StringDeserializer(),
				new StringDeserializer())) {
			consommateur.subscribe(List.of(topic));
			long fin = System.nanoTime() + Duration.ofSeconds(60).toNanos();
			List<ConsumerRecord<String, String>> recus = new ArrayList<>();
			while (System.nanoTime() < fin) {
				consommateur.poll(Duration.ofMillis(500)).forEach(recus::add);
				var trouve = recus.stream().filter(r -> cle.equals(r.key())).findFirst();
				if (trouve.isPresent()) {
					return trouve.get();
				}
			}
		}
		throw new AssertionError("Aucun message de clé " + cle + " dans " + topic);
	}

}
