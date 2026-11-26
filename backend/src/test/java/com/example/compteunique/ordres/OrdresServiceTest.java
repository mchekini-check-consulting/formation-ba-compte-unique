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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.ordres.OrdresService.AnnulationImpossible;
import com.example.compteunique.ordres.OrdresService.DemandeInvalide;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;
import com.example.compteunique.ordres.OrdresService.Enregistrement;
import com.example.compteunique.ordres.OrdresService.MontantInsuffisant;
import com.example.compteunique.ordres.OrdresService.OrdreIntrouvable;

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
		when(ordres.verrouillerOrdre(any())).thenAnswer(
				appel -> baseOrdres.stream().filter(o -> o.getId().equals(appel.getArgument(0))).findFirst());
		when(ordres.montantEnCours(anyString(), anyString(), anyCollection()))
				.thenAnswer(appel -> somme(appel.getArgument(0), appel.getArgument(1), appel.getArgument(2),
						Ordre::getMontant));
		when(ordres.partsEnCours(anyString(), anyString(), anyCollection()))
				.thenAnswer(appel -> somme(appel.getArgument(0), appel.getArgument(1), appel.getArgument(2),
						Ordre::getNombreParts));
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

	private BigDecimal somme(String contrat, String support, Collection<StatutOrdre> statuts,
			Function<Ordre, BigDecimal> valeur) {
		return baseOrdres.stream()
				.filter(o -> o.getNumeroContrat().equals(contrat) && support.equals(o.getSupportDepart())
						&& statuts.contains(o.getStatut()))
				.map(valeur)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
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
		assertThat(production.encoursParSupport(CONTRAT)).isEmpty();
	}

	@Test
	@DisplayName("CU-314 / CU-338 : encours du contrat par support, montants et parts valorisées additionnés")
	void encoursParSupport() {
		when(positionsTest.findByNumeroContrat(CONTRAT)).thenReturn(List.of(
				new PositionTest(CONTRAT, UC, new BigDecimal("1000.00"), null, MARDI_10H.toOffsetDateTime()),
				new PositionTest(CONTRAT, UC, new BigDecimal("500.004"), null, MARDI_10H.toOffsetDateTime())));

		assertThat(positions.encoursParSupport(CONTRAT)).containsExactly(java.util.Map.entry(UC,
				new BigDecimal("1500.00")));
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

	// --- Compléments CU-376 : ordre en nombre de parts (rachat, arbitrage) ---

	private static DemandeOrdre rachatEnParts(UUID id, String parts) {
		return new DemandeOrdre(id, CONTRAT, TypeOrdre.RACHAT, UC, null, null, new BigDecimal(parts));
	}

	@Test
	@DisplayName("Ordre en parts : étant donné 3 000 € sur une UC à 25 €, quand la cliente rachète 40 parts, alors "
			+ "l'ordre est enregistré en parts et 1 000 € (40 × dernière VL) sont bloqués")
	void rachatEnPartsBloqueLaValeurDesParts() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);

		Ordre ordre = service.passer(rachatEnParts(UUID.randomUUID(), "40")).ordre();

		assertThat(ordre.enParts()).isTrue();
		assertThat(ordre.getNombreParts()).isEqualByComparingTo("40");
		assertThat(ordre.getMontant()).isNull();
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("2000");

		service.passer(rachat(UUID.randomUUID(), "500"));
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("1500");
	}

	@Test
	@DisplayName("Ordre en parts : le montant bloqué suit la dernière VL du catalogue, recalculée à chaque lecture")
	void partsRevaloriseesALaDerniereVl() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		service.passer(rachatEnParts(UUID.randomUUID(), "40"));

		when(catalogue.support(UC)).thenReturn(Optional.of(support(UC, "30.00")));

		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("1800");
	}

	@Test
	@DisplayName("Ordre en parts : une demande qui dépasse le montant disponible est refusée avec ce montant")
	void partsAuDelaDuDisponible() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);

		assertThatThrownBy(() -> service.passer(rachatEnParts(UUID.randomUUID(), "120.5")))
				.isInstanceOfSatisfying(MontantInsuffisant.class,
						refus -> assertThat(refus.getDisponible()).isEqualByComparingTo("3000"));
		assertThat(service.passer(rachatEnParts(UUID.randomUUID(), "120")).nouveau()).isTrue();
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("Ordre en parts : l'événement « ordre demandé » contient le nombre de parts à la place du montant")
	void evenementDUnOrdreEnParts() {
		UUID id = UUID.randomUUID();
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);

		service.passer(new DemandeOrdre(id, CONTRAT, TypeOrdre.ARBITRAGE, UC, UC_ARRIVEE, null, new BigDecimal("2.5")));

		verify(evenements).enregistrer(Topic.ORDRES, "OrdreDemande", CONTRAT,
				Map.of("ordreId", id.toString(), "type", "ARBITRAGE", "numeroContrat", CONTRAT, "nombreParts",
						"2.500000", "supportDepart", UC, "supportArrivee", UC_ARRIVEE, "dateExecutionPrevue",
						"2026-12-01"));
	}

	@Test
	@DisplayName("Ordre en parts refusé : versement en parts, montant et parts ensemble, parts nulles ou trop précises, "
			+ "support sans valeur liquidative")
	void ordresEnPartsInvalides() {
		UUID id = UUID.randomUUID();
		List.of(new DemandeOrdre(id, CONTRAT, TypeOrdre.VERSEMENT, null, UC, null, BigDecimal.ONE),
				new DemandeOrdre(id, CONTRAT, TypeOrdre.RACHAT, UC, null, BigDecimal.TEN, BigDecimal.ONE),
				rachatEnParts(id, "0"),
				rachatEnParts(id, "-1"),
				rachatEnParts(id, "1.0000001"))
				.forEach(demande -> assertThatThrownBy(() -> service.passer(demande))
						.as(demande.toString()).isInstanceOf(DemandeInvalide.class));

		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		when(catalogue.support(UC)).thenReturn(Optional.of(new SupportUc(UC, "Support", "Actions", "Actions monde",
				"PER|AV", 4, true, new BigDecimal("0.80"), false, null, null, "EUR", "quotidienne", "aucun",
				"Société de gestion")));
		assertThatThrownBy(() -> service.passer(rachatEnParts(id, "1"))).isInstanceOf(DemandeInvalide.class)
				.hasMessage("Aucune valeur liquidative connue pour " + UC + " : ordre en parts impossible.");
		assertThat(baseOrdres).isEmpty();
	}

	// --- Compléments CU-376 : annulation d'un ordre ---

	@Test
	@DisplayName("Annulation : un ordre « demandé » annulé avant l'heure limite passe à « annulé », le montant redevient "
			+ "disponible et l'événement « ordre annulé » est enregistré dans le topic des ordres")
	void annulationAvantLHeureLimite() {
		positions.enregistrerPositionTest(CONTRAT, UC, new BigDecimal("3000"), null);
		Ordre ordre = service.passer(rachat(UUID.randomUUID(), "1000")).ordre();
		heure(11, 30, 0);

		var annulation = service.annuler(ordre.getId());

		assertThat(annulation.nouvelle()).isTrue();
		assertThat(ordre.getStatut()).isEqualTo(StatutOrdre.ANNULE);
		assertThat(ordre.getAnnuleLe()).isEqualTo(MARDI_10H.withHour(11).withMinute(30).toOffsetDateTime());
		assertThat(service.montantDisponible(CONTRAT, UC)).isEqualByComparingTo("3000");
		verify(evenements).enregistrer(Topic.ORDRES, "OrdreAnnule", CONTRAT,
				Map.of("ordreId", ordre.getId().toString(), "type", "RACHAT", "numeroContrat", CONTRAT, "annuleLe",
						"2026-12-01T11:30+01:00"));
	}

	@Test
	@DisplayName("Annulation : annuler deux fois le même ordre ne crée pas de second événement « ordre annulé »")
	void doubleAnnulation() {
		Ordre ordre = service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null, UC,
				BigDecimal.TEN)).ordre();

		service.annuler(ordre.getId());
		var seconde = service.annuler(ordre.getId());

		assertThat(seconde.nouvelle()).isFalse();
		verify(evenements, times(1)).enregistrer(eq(Topic.ORDRES), eq("OrdreAnnule"), anyString(), any());
	}

	@Test
	@DisplayName("Annulation : à 12 h 00 pile le jour de la VL d'exécution, ou après, l'annulation est refusée ; un "
			+ "ordre passé après 12 h (VL du lendemain) peut encore être annulé le jour même")
	void annulationEtHeureLimite() {
		Ordre duJour = service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null, UC,
				BigDecimal.TEN)).ordre();
		heure(12, 0, 0);
		Ordre duLendemain = service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null,
				UC, BigDecimal.TEN)).ordre();

		assertThatThrownBy(() -> service.annuler(duJour.getId())).isInstanceOf(AnnulationImpossible.class)
				.hasMessage("L'heure limite est passée (01/12/2026 à 12 h 00) : l'ordre ne peut plus être annulé.");
		assertThat(duJour.getStatut()).isEqualTo(StatutOrdre.DEMANDE);

		heure(18, 0, 0);
		assertThat(service.annuler(duLendemain.getId()).ordre().getStatut()).isEqualTo(StatutOrdre.ANNULE);
	}

	@Test
	@DisplayName("Annulation : un ordre en cours, exécuté ou rejeté ne peut pas être annulé ; un ordre inconnu est "
			+ "introuvable")
	void annulationRefusee() {
		Ordre ordre = service.passer(new DemandeOrdre(UUID.randomUUID(), CONTRAT, TypeOrdre.VERSEMENT, null, UC,
				BigDecimal.TEN)).ordre();
		for (StatutOrdre statut : List.of(StatutOrdre.EN_COURS, StatutOrdre.EXECUTE, StatutOrdre.REJETE)) {
			ReflectionTestUtils.setField(ordre, "statut", statut);
			assertThatThrownBy(() -> service.annuler(ordre.getId())).isInstanceOf(AnnulationImpossible.class)
					.hasMessageContaining("seul un ordre « demandé » peut être annulé");
		}
		assertThatThrownBy(() -> service.annuler(UUID.randomUUID())).isInstanceOf(OrdreIntrouvable.class);
		verify(evenements, never()).enregistrer(eq(Topic.ORDRES), eq("OrdreAnnule"), anyString(), any());
	}

}
