package com.example.compteunique.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.mock.env.MockEnvironment;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.evenements.MessageEvenement.MessageIllisible;

// CU-382 (TECH-14) : journal back-office, premier module lecteur (critères 5 et 9) et page de test.
class JournalBackOfficeTest {

	private static final ZonedDateTime MAINTENANT = ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, DateMetier.PARIS);

	private final EvenementRecuRepository recus = mock(EvenementRecuRepository.class);
	private final Evenements evenements = mock(Evenements.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final ConfigurationKafka configuration = new ConfigurationKafka(environnement());
	private final JournalBackOffice journal = new JournalBackOffice(recus, evenements, dateMetier, configuration);
	private final List<EvenementRecu> base = new ArrayList<>();
	private final Set<UUID> dejaTraites = new HashSet<>();

	static MockEnvironment environnement() {
		MockEnvironment environnement = new MockEnvironment().withProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
				.withProperty("KAFKA_TOPIC_SUFFIXE_ERREURS", ".dlt")
				.withProperty("KAFKA_GROUP_ID_PREFIXE", "cu-epargne");
		for (Topic topic : Topic.values()) {
			environnement.setProperty(topic.variable(), "cu-epargne." + topic.name().toLowerCase());
		}
		return environnement;
	}

	@BeforeEach
	void baseEnMemoire() {
		when(dateMetier.maintenant()).thenReturn(MAINTENANT);
		when(recus.save(any())).thenAnswer(appel -> {
			base.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(recus.findTop50ByOrderByIdDesc()).thenAnswer(appel -> base.reversed());
		when(evenements.premiereFois(eq("cu-epargne-journal"), any()))
				.thenAnswer(appel -> dejaTraites.add(appel.getArgument(1)));
	}

	private static ConsumerRecord<String, String> message(UUID id, String cle) {
		String json = "{\"id\":\"" + id + "\",\"type\":\"StatutProfilModifie\",\"cle\":\"" + cle
				+ "\",\"date\":\"2026-12-31\",\"version\":1,\"contenu\":{\"statut\":\"A_REVALIDER\"}}";
		return new ConsumerRecord<>("cu-epargne.profil_client", 0, 0L, cle, json);
	}

	@Test
	@DisplayName("Critère 5 : étant donné un événement publié deux fois (relais redémarré), quand le journal le "
			+ "reçoit, alors il n'est enregistré qu'une seule fois, avec la date métier du message")
	void recuUneSeuleFois() {
		UUID id = UUID.randomUUID();

		journal.recevoir(message(id, "cliente-1"));
		journal.recevoir(message(id, "cliente-1"));

		assertThat(base).singleElement().satisfies(recu -> {
			assertThat(recu.getEvenementId()).isEqualTo(id);
			assertThat(recu.getType()).isEqualTo("StatutProfilModifie");
			assertThat(recu.getTopic()).isEqualTo("cu-epargne.profil_client");
			assertThat(recu.getCle()).isEqualTo("cliente-1");
			assertThat(recu.getDateMetier()).isEqualTo(LocalDate.of(2026, 12, 31));
			assertThat(recu.getContenu()).contains("A_REVALIDER");
			assertThat(recu.getRecuLe()).isEqualTo(MAINTENANT.toOffsetDateTime());
		});
		assertThat(journal.derniers()).hasSize(1);
	}

	@Test
	@DisplayName("Critère 9 : un message illisible est refusé (il sera réessayé puis mis en erreur), sans rien "
			+ "enregistrer")
	void messageIllisible() {
		ConsumerRecord<String, String> illisible = new ConsumerRecord<>("cu-epargne.profil_client", 0, 0L, "x",
				EvenementsTestController.MESSAGE_INVALIDE);

		assertThatThrownBy(() -> journal.recevoir(illisible)).isInstanceOf(MessageIllisible.class);
		assertThat(base).isEmpty();
	}

	@Test
	@DisplayName("Page de test : boîte d'envoi, événements reçus, erreurs ; envoi d'un message invalide")
	void pageDeTest() {
		@SuppressWarnings("unchecked")
		KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
		when(evenements.derniersADistribuer()).thenReturn(List.of());
		when(evenements.derniersEnErreur()).thenReturn(List.of());
		EvenementsTestController routes = new EvenementsTestController(evenements, journal, kafka, configuration);
		journal.recevoir(message(UUID.randomUUID(), "cliente-2"));

		EvenementsTestController.Etat etat = routes.lire();
		routes.envoyerMessageInvalide();

		assertThat(etat.recus()).singleElement().extracting(EvenementsTestController.RecuVue::cle)
				.isEqualTo("cliente-2");
		assertThat(etat.aDistribuer()).isEmpty();
		assertThat(etat.enErreur()).isEmpty();
		verify(kafka).send("cu-epargne.profil_client", "test-message-invalide",
				EvenementsTestController.MESSAGE_INVALIDE);
	}

}
