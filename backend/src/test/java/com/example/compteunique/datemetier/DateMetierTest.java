package com.example.compteunique.datemetier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.datemetier.DateMetier.ChangementInvalide;
import com.example.compteunique.datemetier.DateMetier.DateMetierNonModifiable;
import com.example.compteunique.datemetier.DateMetierController.Changement;
import com.example.compteunique.datemetier.DateMetierController.RetourDateReelle;

// CU-380 (TECH-12) : critères d'acceptation de la date métier.
// Non couverts ici : critère 2 (frais du 31/12, CU-352 pas encore développé) et critère 3 (annulation d'une
// demande de Livret A après 3 mois, CU-296 : le Livret A n'utilise pas la date métier, décision du PO du
// 24/11/2026). Le critère 4 est vérifié côté date métier ; le rattachement à la VL suivante viendra avec TECH-08.
class DateMetierTest {

	/** Heure réelle du test : mardi 24/11/2026, 10 h 00 à Paris. */
	private static final ZonedDateTime REEL = ZonedDateTime.of(2026, 11, 24, 10, 0, 0, 0, DateMetier.PARIS);

	private final ReglageDateMetierRepository reglages = mock(ReglageDateMetierRepository.class);
	private final ChangementDateMetierRepository journal = mock(ChangementDateMetierRepository.class);
	private final List<ChangementDateMetier> traces = new ArrayList<>();
	private ReglageDateMetier reglage;

	@BeforeEach
	void baseEnMemoire() {
		when(reglages.findById(ReglageDateMetier.ID)).thenAnswer(appel -> Optional.ofNullable(reglage));
		when(reglages.save(any())).thenAnswer(appel -> reglage = appel.getArgument(0));
		when(journal.save(any())).thenAnswer(appel -> {
			traces.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(journal.findTop100ByOrderByIdDesc()).thenAnswer(appel -> List.copyOf(traces).reversed());
	}

	private DateMetier enTest(ZonedDateTime heureReelle) {
		return new DateMetier(reglages, journal, true, Clock.fixed(heureReelle.toInstant(), DateMetier.PARIS));
	}

	private DateMetier enProduction() {
		return new DateMetier(reglages, journal, false, Clock.fixed(REEL.toInstant(), DateMetier.PARIS));
	}

	@Test
	@DisplayName("Étant donné l'environnement de production, quand quelqu'un tente de modifier la date métier, "
			+ "alors l'action est refusée")
	void enProductionLeChangementEstRefuse() {
		DateMetier production = enProduction();

		assertThatThrownBy(() -> production.changer(LocalDateTime.of(2026, 12, 31, 11, 0), "Alice"))
				.isInstanceOf(DateMetierNonModifiable.class);
		assertThatThrownBy(() -> production.revenirALaDateReelle("Alice"))
				.isInstanceOf(DateMetierNonModifiable.class);
		assertThatThrownBy(production::journal).isInstanceOf(DateMetierNonModifiable.class);
		verify(reglages, never()).save(any());
		verify(journal, never()).save(any());
	}

	@Test
	@DisplayName("Étant donné l'environnement de production, quand la page de test demande un changement, "
			+ "alors elle reçoit un refus 403 avec un message clair")
	void enProductionLaRouteRepond403() {
		DateMetierController controller = new DateMetierController(enProduction());

		var reponse = controller.nonModifiable(assertThrows(() -> controller
				.changer(new Changement(LocalDateTime.of(2026, 12, 31, 11, 0), "Alice"))));

		assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(reponse.getBody().message()).contains("ne peut pas être modifiée");
	}

	@Test
	@DisplayName("Étant donné l'environnement de production, alors la date métier est l'heure réelle de Paris, "
			+ "même si un ancien réglage traîne en base")
	void enProductionCEstLHeureReelleDeParis() {
		reglage = new ReglageDateMetier(Duration.ofDays(30).toSeconds(), REEL.toOffsetDateTime());

		DateMetier production = enProduction();

		assertThat(production.maintenant()).isEqualTo(REEL);
		assertThat(production.maintenant().getZone()).isEqualTo(DateMetier.PARIS);
		assertThat(production.etat().simulee()).isFalse();
		assertThat(production.etat().modifiable()).isFalse();
	}

	@Test
	@DisplayName("Étant donné l'environnement de test, quand la testeuse avance la date métier au 31/12, "
			+ "alors toute l'application lit le 31/12")
	void laTesteuseAvanceAu31Decembre() {
		DateMetier dateMetier = enTest(REEL);

		dateMetier.changer(LocalDateTime.of(2026, 12, 31, 23, 0), "Alice");

		assertThat(dateMetier.aujourdhui()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
		assertThat(dateMetier.etat().simulee()).isTrue();
	}

	@Test
	@DisplayName("Étant donné une date métier à 11 h 00, quand la testeuse avance l'heure à 12 h 01, "
			+ "alors la date métier lue est 12 h 01 (heure limite des ordres, TECH-08)")
	void de11hA12h01() {
		DateMetier dateMetier = enTest(REEL);
		dateMetier.changer(LocalDateTime.of(2026, 11, 24, 11, 0), "Alice");
		assertThat(dateMetier.maintenant().toLocalTime()).isEqualTo(java.time.LocalTime.of(11, 0));

		dateMetier.changer(LocalDateTime.of(2026, 11, 24, 12, 1), "Alice");

		assertThat(dateMetier.maintenant().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 24, 12, 1));
	}

	@Test
	@DisplayName("Étant donné un changement de date métier en test, quand il est enregistré, alors l'ancienne date, "
			+ "la nouvelle date, l'auteur et l'heure du changement sont tracés")
	void leChangementEstTrace() {
		DateMetier dateMetier = enTest(REEL);

		dateMetier.changer(LocalDateTime.of(2026, 12, 31, 11, 0), "  Alice  ");

		assertThat(dateMetier.journal()).singleElement().satisfies(trace -> {
			assertThat(trace.getAncienneDate()).isEqualTo(REEL.toOffsetDateTime());
			assertThat(trace.getNouvelleDate())
					.isEqualTo(ZonedDateTime.of(2026, 12, 31, 11, 0, 0, 0, DateMetier.PARIS).toOffsetDateTime());
			assertThat(trace.getAuteur()).isEqualTo("Alice");
			assertThat(trace.getChangeLe()).isEqualTo(REEL.toOffsetDateTime());
		});
	}

	@Test
	@DisplayName("Étant donné une date métier avancée, quand le temps réel passe, alors l'heure métier continue d'avancer")
	void lHeureContinueDAvancer() {
		enTest(REEL).changer(LocalDateTime.of(2026, 12, 31, 11, 0), "Alice");

		DateMetier uneMinutePlusTard = enTest(REEL.plusMinutes(1));

		assertThat(uneMinutePlusTard.maintenant().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 12, 31, 11, 1));
	}

	@Test
	@DisplayName("Étant donné une date métier simulée, quand la testeuse revient à la date réelle, "
			+ "alors la date métier redevient l'heure de Paris et le retour est tracé")
	void retourALaDateReelle() {
		DateMetier dateMetier = enTest(REEL);
		dateMetier.changer(LocalDateTime.of(2026, 12, 31, 11, 0), "Alice");

		var etat = new DateMetierController(dateMetier).revenirALaDateReelle(new RetourDateReelle("Bruno"));

		assertThat(etat.dateMetier()).isEqualTo(REEL);
		assertThat(etat.simulee()).isFalse();
		assertThat(dateMetier.journal()).first().satisfies(trace -> {
			assertThat(trace.getAuteur()).isEqualTo("Bruno");
			assertThat(trace.getNouvelleDate()).isEqualTo(REEL.toOffsetDateTime());
		});
	}

	@Test
	@DisplayName("Étant donné une demande sans nom ou sans date, alors elle est refusée (400) et rien n'est enregistré")
	void auteurEtDateObligatoires() {
		DateMetierController controller = new DateMetierController(enTest(REEL));

		ChangementInvalide sansNom = assertThrowsInvalide(
				() -> controller.changer(new Changement(LocalDateTime.of(2026, 12, 31, 11, 0), " ")));
		assertThrowsInvalide(() -> controller.changer(new Changement(null, "Alice")));
		assertThrowsInvalide(() -> controller.changer(new Changement(LocalDateTime.of(2026, 12, 31, 11, 0), "x".repeat(101))));

		assertThat(controller.invalide(sansNom).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(controller.invalide(sansNom).getBody().message()).contains("Indiquez votre nom");
		verify(journal, never()).save(any());
	}

	@Test
	@DisplayName("Étant donné l'environnement de test, quand la page lit la date métier et le journal, "
			+ "alors elle reçoit la date, son état et les changements du plus récent au plus ancien")
	void lectureParLaPageDeTest() {
		DateMetierController controller = new DateMetierController(enTest(REEL));
		controller.changer(new Changement(LocalDateTime.of(2026, 12, 1, 9, 0), "Alice"));
		controller.changer(new Changement(LocalDateTime.of(2026, 12, 31, 11, 0), "Bruno"));

		assertThat(controller.lire().modifiable()).isTrue();
		assertThat(controller.journal()).extracting(DateMetierController.LigneJournal::auteur)
				.containsExactly("Bruno", "Alice");
	}

	private static DateMetierNonModifiable assertThrows(Runnable action) {
		return org.junit.jupiter.api.Assertions.assertThrows(DateMetierNonModifiable.class, action::run);
	}

	private static ChangementInvalide assertThrowsInvalide(Runnable action) {
		return org.junit.jupiter.api.Assertions.assertThrows(ChangementInvalide.class, action::run);
	}

}
