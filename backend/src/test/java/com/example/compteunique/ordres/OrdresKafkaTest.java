package com.example.compteunique.ordres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.BoiteDEnvoiEnMemoire;
import com.example.compteunique.evenements.EvenementADistribuer;
import com.example.compteunique.evenements.MessageEvenement;
import com.example.compteunique.evenements.RelaisEvenements;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;

// CU-376 critères 6 à 8, avec la vraie boîte d'envoi et le vrai relais de TECH-14 (base et Kafka simulés). Rejoue
// aussi en automatique les tests de TECH-14 qui attendaient les ordres : CU-387 (ordre demandé publié dans
// cu-epargne.ordres, clé = contrat), CU-388 (Kafka indisponible) et CU-390 (3 ordres d'un contrat dans l'ordre).
// Les mêmes publications avec un vrai Kafka : OrdresIT.
class OrdresKafkaTest {

	private static final ZonedDateTime MARDI_10H = ZonedDateTime.of(2026, 12, 1, 10, 0, 0, 0, DateMetier.PARIS);

	private final OrdreRepository ordres = mock(OrdreRepository.class);
	private final CatalogueService catalogue = mock(CatalogueService.class);
	private final List<Ordre> baseOrdres = new ArrayList<>();

	private final DateMetier dateMetier = mock(DateMetier.class);
	@SuppressWarnings("unchecked")
	private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
	private final BoiteDEnvoiEnMemoire boiteDEnvoi = new BoiteDEnvoiEnMemoire(dateMetier, kafka);
	private final List<EvenementADistribuer> boite = boiteDEnvoi.contenu();
	private final RelaisEvenements relais = boiteDEnvoi.relais();
	private final OrdresService service = new OrdresService(ordres,
			new PositionsContrat(mock(PositionTestRepository.class), catalogue, dateMetier, true), boiteDEnvoi.evenements(),
			dateMetier, catalogue, LocalTime.NOON);

	@BeforeEach
	void baseEnMemoire() {
		when(dateMetier.maintenant()).thenReturn(MARDI_10H);
		when(dateMetier.aujourdhui()).thenReturn(MARDI_10H.toLocalDate());
		when(catalogue.support(anyString()))
				.thenAnswer(appel -> Optional.of(OrdresServiceTest.support(appel.getArgument(0), "25.00")));
		when(ordres.findById(any())).thenReturn(Optional.empty());
		when(ordres.saveAndFlush(any())).thenAnswer(appel -> {
			baseOrdres.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(ordres.montantEnCours(anyString(), anyString(), anyCollection())).thenReturn(BigDecimal.ZERO);
	}

	private Ordre versement(String contrat, String montant) {
		return service.passer(new DemandeOrdre(UUID.randomUUID(), contrat, TypeOrdre.VERSEMENT, null,
				OrdresServiceTest.UC, new BigDecimal(montant))).ordre();
	}

	private static CompletableFuture<SendResult<String, String>> succes() {
		return CompletableFuture.completedFuture(null);
	}

	@Test
	@DisplayName("Critère 6 : l'événement « ordre demandé » contient un identifiant unique, le type, le numéro de "
			+ "contrat, la date métier, la version du format et le contenu (montant, supports)")
	void critere6FormatDuMessage() {
		Ordre ordre = versement("PER-TEST-0001", "150");
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());
		relais.publierEnAttente();

		ArgumentCaptor<String> valeur = ArgumentCaptor.forClass(String.class);
		verify(kafka).send(anyString(), anyString(), valeur.capture());
		MessageEvenement message = MessageEvenement.lire(valeur.getValue());
		assertThat(message.id()).isEqualTo(ordre.getEvenementId());
		assertThat(message.type()).isEqualTo("OrdreDemande");
		assertThat(message.cle()).isEqualTo("PER-TEST-0001");
		assertThat(message.date()).isEqualTo(MARDI_10H.toLocalDate());
		assertThat(message.dateHeure()).isEqualTo("2026-12-01T10:00:00+01:00");
		assertThat(message.version()).isEqualTo(2);
		assertThat(message.contenu()).containsEntry("montant", "150.00")
				.containsEntry("supportArrivee", OrdresServiceTest.UC)
				.containsEntry("ordreId", ordre.getId().toString());
	}

	@Test
	@DisplayName("Critère 7 / CU-387 : quand le relais s'exécute, l'événement « ordre demandé » est publié dans "
			+ "cu-epargne.ordres avec le numéro de contrat comme clé")
	void critere7PublieDansLeTopicDesOrdres() {
		versement("AV-TEST-0001", "100");
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());

		assertThat(relais.publierEnAttente()).isEqualTo(1);

		verify(kafka).send(eq("cu-epargne.ordres"), eq("AV-TEST-0001"), anyString());
		assertThat(boite.getFirst().getPublieLe()).isNotNull();
	}

	@Test
	@DisplayName("Critère 8 / CU-388 : étant donné Kafka indisponible, quand la cliente valide un ordre, alors l'ordre "
			+ "est enregistré et confirmé, et l'événement est publié dès que Kafka répond")
	void critere8KafkaIndisponible() {
		when(kafka.send(anyString(), anyString(), anyString()))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker absent")));

		Ordre ordre = versement("AV-TEST-0001", "100");
		assertThat(relais.publierEnAttente()).isZero();

		assertThat(ordre.getStatut()).isEqualTo(StatutOrdre.DEMANDE);
		assertThat(baseOrdres).containsExactly(ordre);
		assertThat(boite).singleElement().satisfies(e -> assertThat(e.getPublieLe()).isNull());

		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());
		assertThat(relais.publierEnAttente()).isEqualTo(1);
		assertThat(boite.getFirst().getPublieLe()).isNotNull();
	}

	@Test
	@DisplayName("CU-390 : 3 ordres d'un même contrat sont publiés avec la même clé, dans l'ordre d'enregistrement")
	void cu390TroisOrdresDansLOrdre() {
		Ordre premier = versement("AV-TEST-0001", "1");
		Ordre deuxieme = versement("AV-TEST-0001", "2");
		Ordre troisieme = versement("AV-TEST-0001", "3");
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(succes());

		assertThat(relais.publierEnAttente()).isEqualTo(3);

		ArgumentCaptor<String> valeurs = ArgumentCaptor.forClass(String.class);
		verify(kafka, times(3)).send(eq("cu-epargne.ordres"), eq("AV-TEST-0001"), valeurs.capture());
		assertThat(valeurs.getAllValues()).extracting(json -> MessageEvenement.lire(json).contenu().get("ordreId"))
				.containsExactly(premier.getId().toString(), deuxieme.getId().toString(),
						troisieme.getId().toString());
	}

}
