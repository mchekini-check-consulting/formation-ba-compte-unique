package com.example.compteunique.evenements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.MessageEvenement.MessageIllisible;

// CU-382 (TECH-14) : boîte d'envoi, relais vers Kafka, format des messages et erreurs de lecture (base et Kafka
// simulés). Les mêmes critères sont vérifiés avec un vrai Kafka dans KafkaIT.
class EvenementsTest {

	/** Date métier avancée au 31/12 en test (critère 7). */
	private static final ZonedDateTime FIN_ANNEE = ZonedDateTime.of(2026, 12, 31, 11, 0, 0, 0, DateMetier.PARIS);

	private final EvenementADistribuerRepository aDistribuer = mock(EvenementADistribuerRepository.class);
	private final EvenementTraiteRepository traites = mock(EvenementTraiteRepository.class);
	private final MessageEnErreurRepository enErreur = mock(MessageEnErreurRepository.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	@SuppressWarnings("unchecked")
	private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
	private final ConfigurationKafka configuration = new ConfigurationKafka(ConfigurationKafkaTest.complet());
	private final Evenements evenements = new Evenements(aDistribuer, traites, enErreur, configuration, dateMetier);
	private final RelaisEvenements relais = new RelaisEvenements(aDistribuer, kafka, dateMetier, 100);
	private final List<EvenementADistribuer> boite = new ArrayList<>();

	@BeforeEach
	void baseEnMemoire() {
		when(dateMetier.maintenant()).thenReturn(FIN_ANNEE);
		when(dateMetier.aujourdhui()).thenReturn(FIN_ANNEE.toLocalDate());
		when(aDistribuer.save(any())).thenAnswer(appel -> {
			boite.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(aDistribuer.verrouiller()).thenReturn(true);
		when(aDistribuer.enAttente())
				.thenAnswer(appel -> boite.stream().filter(e -> e.getPublieLe() == null).toList());
	}

	private UUID enregistrer(String cle) {
		return evenements.enregistrer(Topic.PROFIL_CLIENT, "StatutProfilModifie", cle, Map.of("statut", "VALIDE"));
	}

	private static CompletableFuture<SendResult<String, String>> succes() {
		return CompletableFuture.completedFuture(null);
	}

	@Test
	@DisplayName("Critère 7 : l'événement enregistré porte la date métier (31/12 en test), le topic de "
			+ "l'environnement, sa clé, la version du format et son contenu")
	void enregistrementDansLaBoiteDEnvoi() {
		UUID id = enregistrer("cliente-1");

		assertThat(boite).singleElement().satisfies(e -> {
			assertThat(e.getEvenementId()).isEqualTo(id);
			assertThat(e.getType()).isEqualTo("StatutProfilModifie");
			assertThat(e.getTopic()).isEqualTo("cu-epargne.profil-client");
			assertThat(e.getCle()).isEqualTo("cliente-1");
			assertThat(e.getDateMetier()).isEqualTo(LocalDate.of(2026, 12, 31));
			assertThat(e.getVersion()).isEqualTo(2);
			assertThat(e.getContenu()).isEqualTo("{\"statut\":\"VALIDE\"}");
			assertThat(e.getCreeLe()).isEqualTo(FIN_ANNEE.toOffsetDateTime());
			assertThat(e.getPublieLe()).isNull();
		});
		verify(kafka, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("Critère 3 : le relais publie l'événement dans son topic avec sa clé, au format stable, puis le "
			+ "marque publié")
	void relaisPublie() {
		UUID id = enregistrer("cliente-1");
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());

		assertThat(relais.publierEnAttente()).isEqualTo(1);

		ArgumentCaptor<String> valeur = ArgumentCaptor.forClass(String.class);
		verify(kafka).send(eq("cu-epargne.profil-client"), eq("cliente-1"), valeur.capture());
		assertThat(MessageEvenement.lire(valeur.getValue())).isEqualTo(new MessageEvenement(id,
				"StatutProfilModifie", "cliente-1", LocalDate.of(2026, 12, 31), "2026-12-31T11:00:00+01:00", 2,
				Map.of("statut", "VALIDE")));
		assertThat(boite.getFirst().getPublieLe()).isEqualTo(FIN_ANNEE.toOffsetDateTime());
		assertThat(relais.publierEnAttente()).isZero();
	}

	@Test
	@DisplayName("Critère 4 : étant donné Kafka indisponible, l'événement reste en base, non publié ; il est publié "
			+ "dès que Kafka répond, sans perte, et les suivants attendent pour garder l'ordre")
	void kafkaIndisponible() {
		enregistrer("contrat-1");
		enregistrer("contrat-1");
		when(kafka.send(anyString(), anyString(), anyString()))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker absent")));

		assertThat(relais.publierEnAttente()).isZero();
		assertThat(boite).allMatch(e -> e.getPublieLe() == null);
		assertThat(boite.getFirst().getEssais()).isEqualTo(1);
		assertThat(boite.getFirst().getDerniereErreur()).isEqualTo("Kafka indisponible : ExecutionException");
		verify(kafka).send(anyString(), anyString(), anyString());

		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());
		assertThat(relais.publierEnAttente()).isEqualTo(2);
		assertThat(boite).allMatch(e -> e.getPublieLe() != null && e.getDerniereErreur() == null);
	}

	@Test
	@DisplayName("Kafka qui ne répond pas dans le délai : l'événement attend le passage suivant")
	void kafkaTropLent() {
		enregistrer("contrat-1");
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

		assertThat(relais.publierEnAttente()).isZero();
		assertThat(boite.getFirst().getDerniereErreur()).isEqualTo("Kafka indisponible : TimeoutException");
	}

	@Test
	@DisplayName("Relais interrompu (arrêt de l'application) : l'événement n'est pas perdu")
	void relaisInterrompu() throws Exception {
		enregistrer("contrat-1");
		@SuppressWarnings("unchecked")
		CompletableFuture<SendResult<String, String>> envoi = mock(CompletableFuture.class);
		when(envoi.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(envoi);

		assertThat(relais.publierEnAttente()).isZero();
		assertThat(boite.getFirst().getDerniereErreur()).isEqualTo("publication interrompue");
		assertThat(Thread.interrupted()).isTrue();
	}

	@Test
	@DisplayName("Avec 2 réplicas, un seul relais publie à la fois (l'autre passe son tour)")
	void unSeulRelais() {
		enregistrer("contrat-1");
		when(aDistribuer.verrouiller()).thenReturn(false);

		assertThat(relais.publierEnAttente()).isZero();
		verify(kafka, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("Le passage planifié du relais publie les événements en attente")
	void passagePlanifie() {
		enregistrer("contrat-1");
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());

		new PlanificationRelais(relais).publier();

		assertThat(boite.getFirst().getPublieLe()).isNotNull();
	}

	@Test
	@DisplayName("Critère 5 : un événement reçu une deuxième fois par le même module n'est pas retraité")
	void premiereFois() {
		UUID id = UUID.randomUUID();
		when(traites.noter("cu-epargne-journal", id, FIN_ANNEE.toOffsetDateTime())).thenReturn(1, 0);

		assertThat(evenements.premiereFois("cu-epargne-journal", id)).isTrue();
		assertThat(evenements.premiereFois("cu-epargne-journal", id)).isFalse();
	}

	@Test
	@DisplayName("CU-413 : étant donné une date métier réglée au 31/12/2026 à 10:00, quand un événement est publié "
			+ "dans Kafka, alors sa date correspond au 31/12/2026 à 10:00 heure de Paris")
	void evenementALHeureDeParis() {
		ZonedDateTime dixHeures = ZonedDateTime.of(2026, 12, 31, 10, 0, 0, 0, DateMetier.PARIS);
		when(dateMetier.maintenant()).thenReturn(dixHeures);
		when(dateMetier.aujourdhui()).thenReturn(dixHeures.toLocalDate());
		enregistrer("cliente-1");
		// Relu de la base, l'horodatage revient en UTC (09:00Z) : le message doit quand même porter 10:00 à Paris.
		EvenementADistribuer relu = new EvenementADistribuer(UUID.randomUUID(), "StatutProfilModifie",
				"cu-epargne.profil-client", "cliente-2", dixHeures.toLocalDate(), Evenements.VERSION, "{}",
				dixHeures.toOffsetDateTime().withOffsetSameInstant(java.time.ZoneOffset.UTC));
		boite.add(relu);
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());

		assertThat(relais.publierEnAttente()).isEqualTo(2);

		ArgumentCaptor<String> valeurs = ArgumentCaptor.forClass(String.class);
		verify(kafka, org.mockito.Mockito.times(2)).send(anyString(), anyString(), valeurs.capture());
		assertThat(valeurs.getAllValues()).allSatisfy(json -> {
			assertThat(json).contains("\"dateHeure\":\"2026-12-31T10:00:00+01:00\"");
			MessageEvenement message = MessageEvenement.lire(json);
			assertThat(message.date()).isEqualTo(LocalDate.of(2026, 12, 31));
			assertThat(message.version()).isEqualTo(2);
		});
	}

	@Test
	@DisplayName("Un message reçu est relu au format stable ; illisible ou incomplet, il est refusé")
	void formatDuMessage() {
		UUID id = UUID.randomUUID();
		MessageEvenement message = new MessageEvenement(id, "CatalogueMisAJour", "2026-12-31",
				LocalDate.of(2026, 12, 31), "2026-12-31T02:00:00+01:00", 2, Map.of("ajoutes", 102));

		MessageEvenement relu = MessageEvenement.lire(message.json());
		assertThat(relu.id()).isEqualTo(id);
		assertThat(relu.date()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(relu.contenu()).containsEntry("ajoutes", 102);

		assertThatThrownBy(() -> MessageEvenement.lire("ceci n'est pas un evenement"))
				.isInstanceOf(MessageIllisible.class).hasMessage("message illisible (JSON attendu)");
		assertThatThrownBy(() -> MessageEvenement.lire("{\"type\":\"X\",\"version\":1}")).isInstanceOf(MessageIllisible.class)
				.hasMessage("message incomplet (id, type et date obligatoires)");
		assertThatThrownBy(() -> MessageEvenement.lire("null")).isInstanceOf(MessageIllisible.class);
	}

	@Test
	@DisplayName("Critère 9 : un message en échec après 3 essais est noté en erreur avec son motif et son groupe")
	void miseDeCote() {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		org.mockito.Mockito.doAnswer(appel -> {
			appel.<java.util.function.Consumer<Object>>getArgument(0).accept(null);
			return null;
		}).when(transactions).executeWithoutResult(any());
		List<MessageEnErreur> erreurs = new ArrayList<>();
		when(enErreur.save(any())).thenAnswer(appel -> {
			erreurs.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		ConsumerRecord<String, String> message = new ConsumerRecord<>("cu-epargne.profil-client", 0, 7L, "cle-1",
				"ceci n'est pas un evenement");
		Exception erreur = new ListenerExecutionFailedException("echec", "cu-epargne-journal",
				new MessageIllisible("message illisible (JSON attendu)"));

		ErreursLecture.mettreDeCote(message, erreur, enErreur, dateMetier, transactions);

		assertThat(erreurs).singleElement().satisfies(e -> {
			assertThat(e.getTopic()).isEqualTo("cu-epargne.profil-client");
			assertThat(e.getCle()).isEqualTo("cle-1");
			assertThat(e.getGroupe()).isEqualTo("cu-epargne-journal");
			assertThat(e.getMotif()).isEqualTo("message illisible (JSON attendu)");
			assertThat(e.getEssais()).isEqualTo(3);
			assertThat(e.getEnErreurLe()).isEqualTo(FIN_ANNEE.toOffsetDateTime());
		});

		ErreursLecture.mettreDeCote(new ConsumerRecord<>("t", 0, 0L, null, "x"), new IllegalStateException(),
				enErreur, dateMetier, transactions);
		assertThat(erreurs.getLast().getGroupe()).isEqualTo("inconnu");
		assertThat(erreurs.getLast().getCle()).isNull();
		assertThat(erreurs.getLast().getMotif()).isEqualTo("IllegalStateException");
		assertThat(new ErreursLecture().gestionErreursKafka(kafka, configuration, enErreur, dateMetier,
				transactions)).isNotNull();
	}

	@Test
	@DisplayName("Les textes trop longs (motif, erreur, clé) sont raccourcis pour tenir en base")
	void textesRaccourcis() {
		String long400 = "x".repeat(400);
		MessageEnErreur erreur = new MessageEnErreur("t", long400, "g", long400, 3, FIN_ANNEE.toOffsetDateTime());
		assertThat(erreur.getCle()).hasSize(100);
		assertThat(erreur.getMotif()).hasSize(300);
		assertThat(new MessageEnErreur("t", "c", "g", null, 3, null).getMotif()).isEqualTo("erreur inconnue");

		EvenementADistribuer evenement = new EvenementADistribuer(UUID.randomUUID(), "T", "t", "c",
				LocalDate.of(2026, 12, 31), 1, "{}", FIN_ANNEE.toOffsetDateTime());
		evenement.echec(long400);
		assertThat(evenement.getDerniereErreur()).hasSize(300);
		evenement.echec(null);
		assertThat(evenement.getDerniereErreur()).isEqualTo("erreur inconnue");
		assertThat(evenement.getEssais()).isEqualTo(2);
	}

	@Test
	@DisplayName("La page de test liste la boîte d'envoi et les messages en erreur")
	void listes() {
		when(aDistribuer.findTop50ByOrderByIdDesc()).thenReturn(List.of());
		when(enErreur.findTop50ByOrderByIdDesc()).thenReturn(List.of());

		assertThat(evenements.derniersADistribuer()).isEmpty();
		assertThat(evenements.derniersEnErreur()).isEmpty();
	}

	@Test
	void interruptionEnveloppee() {
		assertThat(ErreursLecture.motif(new ExecutionException(new IllegalArgumentException("cause")))).isEqualTo(
				"cause");
	}

}
