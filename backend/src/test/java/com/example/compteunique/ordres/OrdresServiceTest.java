package com.example.compteunique.ordres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.ordres.OrdresService.DemandeInvalide;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;
import com.example.compteunique.ordres.OrdresService.Enregistrement;
import com.example.compteunique.ordres.OrdresService.MontantInsuffisant;

// CU-376 (TECH-08) : critères d'acceptation de l'enregistrement des ordres, base et Kafka simulés en mémoire.
// La publication par le relais (critères 7 et 8, tests CU-387, CU-388, CU-390) est vérifiée dans OrdresKafkaTest ;
// l'annulation complète en cas d'échec (critère 5) avec une vraie base dans OrdresIT.
class OrdresServiceTest {

	/** Mardi 01/12/2026, 10 h à Paris. */
	private static final ZonedDateTime MARDI_10H = ZonedDateTime.of(2026, 12, 1, 10, 0, 0, 0, DateMetier.PARIS);

	static final String CONTRAT = "AV-TEST-0001";
	static final String UC = "QZ0000000017";
	static final String UC_ARRIVEE = "US0378331005";

	private final OrdreRepository ordres = mock(OrdreRepository.class);
	private final PositionTestRepository positionsTest = mock(PositionTestRepository.class);
	private final CatalogueService catalogue = mock(CatalogueService.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final Evenements evenements = mock(Evenements.class);

	private final List<Ordre> baseOrdres = new ArrayList<>();
	private final List<PositionTest> basePositions = new ArrayList<>();

	private final PositionsContrat positions = new PositionsContrat(positionsTest, catalogue, dateMetier, true);
	private final OrdresService service = new OrdresService(ordres, positions, evenements, dateMetier, catalogue,
			LocalTime.NOON);

	@BeforeEach
	void baseEnMemoire() {
		when(dateMetier.maintenant()).thenReturn(MARDI_10H);
		when(catalogue.support(anyString())).thenAnswer(appel -> Optional.of(support(appel.getArgument(0), "25.00")));
		when(ordres.findById(any())).thenAnswer(
				appel -> baseOrdres.stream().filter(o -> o.getId().equals(appel.getArgument(0))).findFirst());
		when(ordres.saveAndFlush(any())).thenAnswer(appel -> {
			baseOrdres.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(ordres.montantEnCours(anyString(), anyString(), anyCollection())).thenAnswer(appel -> {
			Collection<StatutOrdre> statuts = appel.getArgument(2);
			return baseOrdres.stream()
					.filter(o -> o.getNumeroContrat().equals(appel.getArgument(0))
							&& appel.getArgument(1).equals(o.getSupportDepart()) && statuts.contains(o.getStatut()))
					.map(Ordre::getMontant)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
		});
		when(ordres.findTop50ByOrderByDemandeLeDesc()).thenAnswer(appel -> baseOrdres.reversed());
		doAnswer(appel -> {
			baseOrdres.clear();
			return null;
		}).when(ordres).deleteAllInBatch();
		when(positionsTest.save(any())).thenAnswer(appel -> {
			basePositions.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(positionsTest.findByNumeroContratAndCodeIsin(anyString(), anyString())).thenAnswer(appel -> basePositions
				.stream()
				.filter(p -> p.getNumeroContrat().equals(appel.getArgument(0))
						&& p.getCodeIsin().equals(appel.getArgument(1)))
				.toList());
		doAnswer(appel -> {
			basePositions.clear();
			return null;
		}).when(positionsTest).deleteAllInBatch();
		when(evenements.enregistrer(any(), anyString(), anyString(), any())).thenAnswer(appel -> UUID.randomUUID());
	}

	static SupportUc support(String isin, String vl) {
		return new SupportUc(isin, "Support " + isin, "Actions", "Actions monde", "PER|AV", 4, true,
				new BigDecimal("0.80"), false, new BigDecimal(vl), LocalDate.of(2026, 11, 30), "EUR", "quotidienne",
				"aucun", "Société de gestion");
	}

	private static DemandeOrdre rachat(UUID id, String montant) {
		return new DemandeOrdre(id, CONTRAT, TypeOrdre.RACHAT, UC, null, new BigDecimal(montant));
	}

	private void heure(int heures, int minutes, int secondes) {
		when(dateMetier.maintenant()).thenReturn(MARDI_10H.withHour(heures).withMinute(minutes).withSecond(secondes));
	}

	@Test
	@DisplayName("Critère 1 : étant donné 3 000 € sur une UC, quand la cliente demande un rachat de 1 000 €, alors "
			+ "l'ordre est enregistré au statut « demandé » et le montant disponible sur cette UC devient 2 000 €")
	void critere1RachatBloqueLeMontant() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);

		Enregistrement resultat = service.passer(rachat(UUID.randomUUID(), "1000"));

		assertThat(resultat.nouveau()).isTrue();
		assertThat(resultat.ordre().getStatut()).isEqualTo(StatutOrdre.DEMANDE);
		assertThat(resultat.ordre().getMontant()).isEqualByComparingTo("1000");
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("2000");
	}

	@Test
	@DisplayName("Critère 2 : étant donné un ordre déjà enregistré, quand le même ordre est reçu une seconde fois "
			+ "(double clic ou nouvel envoi), alors un seul ordre existe en base et un seul événement est créé")
	void critere2DoublonIgnore() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		UUID id = UUID.randomUUID();

		Enregistrement premier = service.passer(rachat(id, "1000"));
		Enregistrement second = service.passer(rachat(id, "1000"));

		assertThat(second.nouveau()).isFalse();
		assertThat(second.ordre()).isSameAs(premier.ordre());
		assertThat(baseOrdres).hasSize(1);
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("2000");
		verify(evenements, times(1)).enregistrer(any(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("Critère 3 : étant donné 2 000 € disponibles sur une UC, quand la cliente demande un rachat de "
			+ "2 500 €, alors la demande est refusée avec le montant disponible affiché")
	void critere3MontantInsuffisant() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		service.passer(rachat(UUID.randomUUID(), "1000"));

		assertThatThrownBy(() -> service.passer(rachat(UUID.randomUUID(), "2500")))
				.isInstanceOfSatisfying(MontantInsuffisant.class, refus -> {
					assertThat(refus.getDisponible()).isEqualByComparingTo("2000");
					assertThat(refus.getMessage()).isEqualTo("Montant disponible insuffisant : 2 000,00 € disponibles.");
				});
		assertThat(baseOrdres).hasSize(1);
		verify(evenements, times(1)).enregistrer(any(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("Un arbitrage vend le support de départ : il est limité au montant disponible de ce support ; un "
			+ "versement n'est pas limité par l'encours")
	void arbitrageEtVersement() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("500"), null);

		assertThatThrownBy(() -> service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.ARBITRAGE, UC,
				UC_ARRIVEE, new BigDecimal("600")))).isInstanceOf(MontantInsuffisant.class);
		assertThat(service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.ARBITRAGE, UC, UC_ARRIVEE,
				new BigDecimal("500"))).ordre().getStatut()).isEqualTo(StatutOrdre.DEMANDE);
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("0");
		assertThat(service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null, UC,
				new BigDecimal("10000"))).nouveau()).isTrue();
	}

	@Test
	@DisplayName("Seuls les ordres en cours (demandé, en cours) bloquent le montant ; exécutés, rejetés et annulés non")
	void seulsLesOrdresEnCoursBloquent() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		service.passer(rachat(UUID.randomUUID(), "1000"));

		ArgumentCaptor<Collection<StatutOrdre>> statuts = ArgumentCaptor.captor();
		service.montantDisponible(CONTRAT, UC);
		verify(ordres, org.mockito.Mockito.atLeastOnce()).montantEnCours(eq(CONTRAT), eq(UC), statuts.capture());
		assertThat(statuts.getValue()).containsExactlyInAnyOrder(StatutOrdre.DEMANDE, StatutOrdre.EN_COURS);
		assertThat(StatutOrdre.values()).containsExactly(StatutOrdre.DEMANDE, StatutOrdre.EN_COURS,
				StatutOrdre.EXECUTE, StatutOrdre.REJETE, StatutOrdre.ANNULE);
	}

	@Test
	@DisplayName("Critère 4 : étant donné une heure limite à 12 h, quand un ordre est enregistré à 11 h 59, alors sa "
			+ "date d'exécution prévue est la prochaine VL ; à 12 h 00 pile et à 12 h 01, c'est la VL suivante")
	void critere4HeureLimite() {
		heure(11, 59, 59);
		assertThat(service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null, UC,
				BigDecimal.TEN)).ordre().getDateExecutionPrevue()).isEqualTo(LocalDate.of(2026, 12, 1));
		heure(12, 0, 0);
		assertThat(service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null, UC,
				BigDecimal.TEN)).ordre().getDateExecutionPrevue()).isEqualTo(LocalDate.of(2026, 12, 2));
		heure(12, 1, 0);
		assertThat(service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null, UC,
				BigDecimal.TEN)).ordre().getDateExecutionPrevue()).isEqualTo(LocalDate.of(2026, 12, 2));
	}

	@Test
	@DisplayName("Heure limite et week-end : après 12 h un vendredi, ou un samedi, l'ordre part sur la VL du lundi")
	void heureLimiteEtWeekEnd() {
		ZonedDateTime vendredi = ZonedDateTime.of(2026, 12, 4, 0, 0, 0, 0, DateMetier.PARIS);
		assertThat(OrdresService.dateExecutionPrevue(vendredi.withHour(11), LocalTime.NOON))
				.isEqualTo(LocalDate.of(2026, 12, 4));
		assertThat(OrdresService.dateExecutionPrevue(vendredi.withHour(12), LocalTime.NOON))
				.isEqualTo(LocalDate.of(2026, 12, 7));
		assertThat(OrdresService.dateExecutionPrevue(vendredi.plusDays(1).withHour(9), LocalTime.NOON))
				.isEqualTo(LocalDate.of(2026, 12, 7));
		assertThat(OrdresService.dateExecutionPrevue(vendredi.plusDays(2).withHour(9), LocalTime.of(9, 30)))
				.isEqualTo(LocalDate.of(2026, 12, 7));
	}

	@Test
	@DisplayName("Critère 5 : étant donné un ordre en cours d'enregistrement, quand l'enregistrement échoue, alors "
			+ "aucun événement « ordre demandé » n'est créé (l'ordre est enregistré avant l'événement, même transaction)")
	void critere5EchecSansEvenement() {
		org.mockito.Mockito.doThrow(new IllegalStateException("base indisponible")).when(ordres).saveAndFlush(any());

		assertThatThrownBy(() -> service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT,
				null, UC, BigDecimal.TEN))).isInstanceOf(IllegalStateException.class);
		verify(evenements, never()).enregistrer(any(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("Critère 6 : l'événement « ordre demandé » va dans le topic des ordres, clé = numéro de contrat, avec "
			+ "l'identifiant de l'ordre, son type, le contrat, le montant, les supports et la date d'exécution prévue")
	void critere6ContenuDeLEvenement() {
		UUID id = UUID.randomUUID();
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);

		Ordre ordre = service.passer(new DemandeOrdre(id, CONTRAT, TypeOrdre.ARBITRAGE, UC, UC_ARRIVEE,
				new BigDecimal("1000"))).ordre();

		verify(evenements).enregistrer(Topic.ORDRES, "OrdreDemande", CONTRAT,
				Map.of("ordreId", id.toString(), "type", "ARBITRAGE", "numeroContrat", CONTRAT, "montant", "1000.00",
						"supportDepart", UC, "supportArrivee", UC_ARRIVEE, "dateExecutionPrevue", "2026-12-01"));
		assertThat(ordre.getEvenementId()).isNotNull();
		assertThat(ordre.getDemandeLe()).isEqualTo(MARDI_10H.toOffsetDateTime());
	}

	@Test
	@DisplayName("Critère 9 : étant donné l'environnement de test, quand la testeuse enregistre une position de test "
			+ "de 3 000 € sur une UC, alors le montant disponible pour cette UC est de 3 000 € ; une position en parts "
			+ "est valorisée à la dernière VL")
	void critere9PositionDeTest() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("3000");

		positions.enregistrerPositionTest(CONTRAT, UC, null, new BigDecimal("4"));
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("3100.00");
		assertThat(service.montantDisponible("AUTRE-CONTRAT", UC)).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("Critère 10 : hors environnement de test, les positions de test ne peuvent pas être enregistrées et "
			+ "ne sont jamais lues")
	void critere10PositionsDeTestRefuseesEnProduction() {
		basePositions.add(new PositionTest(CONTRAT, UC, new BigDecimal("3000"), null, MARDI_10H.toOffsetDateTime()));
		PositionsContrat production = new PositionsContrat(positionsTest, catalogue, dateMetier, false);

		assertThatThrownBy(() -> production.enregistrerPositionTest(CONTRAT, UC, BigDecimal.ONE, null))
				.isInstanceOf(PositionsContrat.PositionsDeTestRefusees.class);
		assertThatThrownBy(production::viderPositionsDeTest)
				.isInstanceOf(PositionsContrat.PositionsDeTestRefusees.class);
		assertThat(production.encours(CONTRAT, UC)).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("Demandes invalides refusées sans rien enregistrer : identifiant, contrat, montant, supports")
	void demandesInvalides() {
		UUID id = UUID.randomUUID();
		List.of(new DemandeOrdre(null, CONTRAT, TypeOrdre.VERSEMENT, null, UC, BigDecimal.TEN),
				new DemandeOrdre(id, " ", TypeOrdre.VERSEMENT, null, UC, BigDecimal.TEN),
				new DemandeOrdre(id, CONTRAT, null, null, UC, BigDecimal.TEN),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.VERSEMENT, null, UC, BigDecimal.ZERO),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.VERSEMENT, null, UC, null),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.VERSEMENT, null, UC, new BigDecimal("10.001")),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.VERSEMENT, null, null, BigDecimal.TEN),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.RACHAT, null, null, BigDecimal.TEN),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.ARBITRAGE, UC, null, BigDecimal.TEN),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.ARBITRAGE, UC, UC, BigDecimal.TEN),
				new DemandeOrdre(id, "x".repeat(31), TypeOrdre.VERSEMENT, null, UC, BigDecimal.TEN))
				.forEach(demande -> assertThatThrownBy(() -> service.passer(demande))
						.as(demande.toString()).isInstanceOf(DemandeInvalide.class));

		when(catalogue.support("INCONNU")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.passer(new DemandeOrdre(id, CONTRAT, TypeOrdre.VERSEMENT, null, "INCONNU",
				BigDecimal.TEN))).isInstanceOf(DemandeInvalide.class).hasMessage("Support inconnu : INCONNU.");
		assertThat(baseOrdres).isEmpty();
		verify(evenements, never()).enregistrer(any(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("Vider les ordres et positions de test remet l'environnement de test à zéro")
	void vider() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		service.passer(rachat(UUID.randomUUID(), "1000"));
		assertThat(service.derniers()).hasSize(1);

		service.viderOrdresEtPositionsDeTest();

		assertThat(baseOrdres).isEmpty();
		assertThat(basePositions).isEmpty();
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("0");
	}

}
