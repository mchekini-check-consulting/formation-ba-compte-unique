package com.example.compteunique.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;
import com.example.compteunique.gestion.Allocation.AllocationImpossible;
import com.example.compteunique.gestion.Allocation.Ligne;
import com.example.compteunique.gestion.Allocation.Montant;

// CU-314 / CU-338 : allocation cible de la gestion pilotée et répartition d'un versement.
class AllocationTest {

	static SupportEligible support(String isin, int risque, boolean nonCote) {
		return new SupportEligible(isin, "Support " + isin, "Catégorie", risque, new BigDecimal("0.50"),
				BigDecimal.TEN, LocalDate.of(2026, 11, 25), nonCote, null, null);
	}

	static final List<SupportEligible> CATALOGUE = List.of(support("MONETAIRE", 1, false),
			support("OBLIG", 3, false), support("ACTIONS", 5, false), support("MONDE", 6, false),
			support("NONCOTE", 6, true));

	private static BigDecimal total(List<Ligne> lignes, java.util.function.Predicate<Ligne> filtre) {
		return lignes.stream().filter(filtre).map(Ligne::partPct).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal montant(List<Montant> montants, String isin) {
		return montants.stream().filter(m -> m.codeIsin().equals(isin)).map(Montant::montant)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	@Test
	@DisplayName("PER dynamique à 15 ans (grille : 10 % faible risque, 8 % non coté) : l'allocation respecte les deux "
			+ "parts minimales et totalise 100 %")
	void respecteLesPartsMinimales() {
		List<Ligne> lignes = Allocation.calculer(CATALOGUE, new BigDecimal("8"), new BigDecimal("10")).lignes();

		assertThat(total(lignes, l -> true)).isEqualByComparingTo("100");
		assertThat(total(lignes, Ligne::nonCote)).isEqualByComparingTo("8");
		assertThat(total(lignes, l -> !l.nonCote() && l.niveauRisque() <= 3)).isEqualByComparingTo("10");
		assertThat(lignes).extracting(Ligne::codeIsin).containsExactly("NONCOTE", "MONETAIRE", "OBLIG", "ACTIONS",
				"MONDE");
		assertThat(lignes).extracting(Ligne::partPct).containsExactly(new BigDecimal("8.00"), new BigDecimal("5.00"),
				new BigDecimal("5.00"), new BigDecimal("41.00"), new BigDecimal("41.00"));
	}

	@Test
	@DisplayName("AV dynamique : sur un versement de 1 000 €, au moins 80 € vont sur des supports non cotés")
	void versementAvecQuotaDeNonCote() {
		List<Montant> montants = Allocation.calculer(CATALOGUE, new BigDecimal("8"), BigDecimal.ZERO)
				.repartir(new BigDecimal("1000.00"));

		assertThat(montant(montants, "NONCOTE")).isGreaterThanOrEqualTo(new BigDecimal("80.00"));
		assertThat(montants.stream().map(Montant::montant).reduce(BigDecimal.ZERO, BigDecimal::add))
				.isEqualByComparingTo("1000.00");
	}

	@Test
	@DisplayName("Arrondis : la part minimale est arrondie au centime supérieur, le total reste exact")
	void arrondis() {
		List<SupportEligible> supports = List.of(support("NC1", 6, true), support("NC2", 6, true),
				support("NC3", 6, true), support("A", 5, false));
		List<Montant> montants = Allocation.calculer(supports, new BigDecimal("4"), BigDecimal.ZERO)
				.repartir(new BigDecimal("33.33"));

		BigDecimal nonCote = montant(montants, "NC1").add(montant(montants, "NC2")).add(montant(montants, "NC3"));
		assertThat(nonCote).isGreaterThanOrEqualTo(new BigDecimal("1.3332"));
		assertThat(montants.stream().map(Montant::montant).reduce(BigDecimal.ZERO, BigDecimal::add))
				.isEqualByComparingTo("33.33");
		List<Ligne> lignes = Allocation.calculer(supports, new BigDecimal("4"), BigDecimal.ZERO).lignes();
		assertThat(lignes).extracting(Ligne::partPct).containsExactly(new BigDecimal("1.33"), new BigDecimal("1.33"),
				new BigDecimal("1.34"), new BigDecimal("96.00"));
	}

	@Test
	@DisplayName("Profil prudent sans support de niveau 4 et plus : le reste va sur les supports à faible risque")
	void resteSurLeFaibleRisque() {
		List<Ligne> lignes = Allocation.calculer(List.of(support("MONETAIRE", 1, false), support("OBLIG", 3, false)),
				BigDecimal.ZERO, new BigDecimal("90")).lignes();

		assertThat(lignes).extracting(Ligne::partPct).containsExactly(new BigDecimal("50.00"),
				new BigDecimal("50.00"));
	}

	@Test
	@DisplayName("Parts minimales à 100 % ensemble : le versement n'est pas dépassé malgré les arrondis")
	void partsMinimalesAuMaximum() {
		List<Montant> montants = Allocation.calculer(CATALOGUE, new BigDecimal("50.5"), new BigDecimal("49.5"))
				.repartir(new BigDecimal("0.03"));

		assertThat(montants.stream().map(Montant::montant).reduce(BigDecimal.ZERO, BigDecimal::add))
				.isEqualByComparingTo("0.03");
	}

	@Test
	@DisplayName("Part minimale sans support pour la recevoir : allocation refusée avec un message clair")
	void refuseeSansSupport() {
		List<SupportEligible> cotes = List.of(support("ACTIONS", 5, false));

		assertThatThrownBy(() -> Allocation.calculer(cotes, new BigDecimal("8"), BigDecimal.ZERO))
				.isInstanceOf(AllocationImpossible.class).hasMessageContaining("non coté").hasMessageContaining("8 %");
		assertThatThrownBy(() -> Allocation.calculer(cotes, BigDecimal.ZERO, new BigDecimal("10")))
				.isInstanceOf(AllocationImpossible.class).hasMessageContaining("faible risque");
		assertThatThrownBy(() -> Allocation.calculer(List.of(), BigDecimal.ZERO, BigDecimal.ZERO))
				.isInstanceOf(AllocationImpossible.class).hasMessageContaining("Aucun support");
	}

}
