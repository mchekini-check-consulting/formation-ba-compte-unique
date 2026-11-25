package com.example.compteunique.datemetier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// CU-413 : date métier enregistrée une seconde trop tôt et avec une heure de décalage. L'heure réelle des tests a
// volontairement des fractions de seconde (14:12:07,6) : c'est ce qui faisait perdre une seconde.
class DateMetierHeureDeParisTest {

	/** Heure réelle au moment du changement : mercredi 25/11/2026, 14:12:07,6 à Paris. */
	private static final ZonedDateTime REEL = ZonedDateTime.of(2026, 11, 25, 14, 12, 7, 600_000_000, DateMetier.PARIS);

	private final ReglageDateMetierRepository reglages = mock(ReglageDateMetierRepository.class);
	private final ChangementDateMetierRepository journal = mock(ChangementDateMetierRepository.class);
	private final List<ChangementDateMetier> traces = new ArrayList<>();
	private ReglageDateMetier reglage;

	@BeforeEach
	void baseEnMemoire() {
		when(reglages.findById(ReglageDateMetier.ID_UNIQUE)).thenAnswer(appel -> Optional.ofNullable(reglage));
		when(reglages.save(any())).thenAnswer(appel -> reglage = appel.getArgument(0));
		when(journal.save(any())).thenAnswer(appel -> {
			traces.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
	}

	/** La date métier vue à un instant réel donné (même base de réglages). */
	private DateMetier a(ZonedDateTime heureReelle) {
		return new DateMetier(reglages, journal, true, Clock.fixed(heureReelle.toInstant(), DateMetier.PARIS));
	}

	@Test
	@DisplayName("Étant donné l'environnement de test, quand la testeuse saisit le 31/12/2026 à 23:30, alors 23:30 est "
			+ "affiché et 23:30:00 (heure de Paris) est enregistré")
	void finDAnnee2330() {
		var etat = a(REEL).changer(LocalDateTime.of(2026, 12, 31, 23, 30), "Alice");

		ZonedDateTime attendu = ZonedDateTime.of(2026, 12, 31, 23, 30, 0, 0, DateMetier.PARIS);
		assertThat(etat.dateMetier()).isEqualTo(attendu);
		assertThat(a(REEL).maintenant()).isEqualTo(attendu);
		assertThat(traces).singleElement()
				.satisfies(trace -> assertThat(trace.getNouvelleDate()).isEqualTo(attendu.toOffsetDateTime()));
		// Heure d'hiver : 23:30 à Paris = 22:30 UTC, le même instant.
		assertThat(traces.getFirst().getNouvelleDate().toInstant())
				.isEqualTo(OffsetDateTime.of(2026, 12, 31, 22, 30, 0, 0, ZoneOffset.UTC).toInstant());
	}

	@Test
	@DisplayName("Étant donné l'environnement de test, quand la testeuse saisit le 01/07/2026 à 10:00 (heure d'été), "
			+ "alors 10:00 est affiché et enregistré en heure de Paris")
	void heureDEte() {
		var etat = a(REEL).changer(LocalDateTime.of(2026, 7, 1, 10, 0), "Alice");

		assertThat(etat.dateMetier().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 0, 0));
		assertThat(etat.dateMetier().getOffset()).isEqualTo(ZoneOffset.ofHours(2));
		assertThat(traces.getFirst().getNouvelleDate())
				.isEqualTo(OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.ofHours(2)));
	}

	@Test
	@DisplayName("Étant donné une heure limite à 12:00 et une date métier réglée sur 12:00, quand l'heure courante est "
			+ "lue, alors elle vaut 12:00:00 et non 11:59:59")
	void midiPile() {
		a(REEL).changer(LocalDateTime.of(2026, 12, 31, 12, 0), "Alice");

		assertThat(a(REEL).maintenant().toLocalTime()).hasToString("12:00");
		// Lue un peu plus tard (0,3 s réelles), elle ne recule jamais sous 12:00:00.
		assertThat(a(REEL.plusNanos(300_000_000)).maintenant().toLocalDateTime())
				.isEqualTo(LocalDateTime.of(2026, 12, 31, 12, 0, 0));
		// Et elle continue d'avancer avec le temps réel.
		assertThat(a(REEL.plusSeconds(61)).maintenant().toLocalDateTime())
				.isEqualTo(LocalDateTime.of(2026, 12, 31, 12, 1, 1));
	}

	@Test
	@DisplayName("Étant donné l'environnement de test, quand la testeuse saisit le 01/01/2026 (dans le passé), alors "
			+ "la date est acceptée et le changement est tracé dans le journal")
	void dateDansLePasse() {
		var etat = a(REEL).changer(LocalDateTime.of(2026, 1, 1, 0, 0), "Alice");

		ZonedDateTime attendu = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, DateMetier.PARIS);
		assertThat(etat.dateMetier()).isEqualTo(attendu);
		assertThat(etat.simulee()).isTrue();
		assertThat(traces).singleElement().satisfies(trace -> {
			assertThat(trace.getNouvelleDate()).isEqualTo(attendu.toOffsetDateTime());
			assertThat(trace.getAuteur()).isEqualTo("Alice");
		});
	}

	@Test
	@DisplayName("Une date saisie avec des secondes ou des fractions est enregistrée à la seconde, sans fraction")
	void secondesSaisies() {
		var etat = a(REEL).changer(LocalDateTime.of(2026, 12, 31, 23, 30, 15, 900_000_000), "Alice");

		assertThat(etat.dateMetier().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 30, 15));
	}

	@Test
	@DisplayName("Une heure stockée en UTC (relue de la base) est convertie en heure de Paris à un seul endroit")
	void conversionEnHeureDeParis() {
		OffsetDateTime relueEnUtc = OffsetDateTime.of(2026, 12, 31, 9, 0, 0, 0, ZoneOffset.UTC);

		assertThat(DateMetier.heureDeParis(relueEnUtc))
				.isEqualTo(OffsetDateTime.of(2026, 12, 31, 10, 0, 0, 0, ZoneOffset.ofHours(1)));
		assertThat(DateMetier.heureDeParis(OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)))
				.isEqualTo(OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.ofHours(2)));
	}

}
