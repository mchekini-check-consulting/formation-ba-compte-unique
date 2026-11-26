package com.example.compteunique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;

// CU-412 (CAT-01B) : règles du service « lister les supports éligibles », sur le fichier de recette
// catalogue_uc_2026-11-23.csv (joint à CU-337), chargé dans une base simulée.
class SupportsEligiblesTest {

	private final SupportRepository supports = mock(SupportRepository.class);
	private final SupportsEligibles service = new SupportsEligibles(supports, 3, 4, 6, new BigDecimal("0.50"),
			new BigDecimal("0.65"));

	@BeforeEach
	void catalogueDeRecette() {
		OffsetDateTime le = OffsetDateTime.of(2026, 11, 23, 2, 0, 0, 0, ZoneOffset.ofHours(1));
		List<Support> catalogue = LecteurCatalogue.lire(FichiersCatalogue.principal(), LocalDate.of(2026, 11, 23))
				.lignes().stream().map(ligne -> new Support(ligne, le)).toList();
		when(supports.findByStatutOrderByNom(anyString())).thenAnswer(appel -> catalogue.stream()
				.filter(s -> s.getStatut().equals(appel.getArgument(0)))
				.sorted(Comparator.comparing(Support::getNom))
				.toList());
	}

	@Test
	@DisplayName("Critères 1 et 4 : profil prudent, PER : 31 supports, tous de niveau de risque 1 à 3")
	void prudentPer() {
		List<SupportEligible> liste = service.lister(Produit.PER, "PRUDENT");

		assertThat(liste).hasSize(31).allSatisfy(s -> assertThat(s.niveauRisque()).isBetween(1, 3));
	}

	@Test
	@DisplayName("Critère 2 : profil équilibré, assurance vie : 48 supports de niveau 1 à 4, dont 12 « non coté »")
	void equilibreAv() {
		List<SupportEligible> liste = service.lister(Produit.AV, "EQUILIBRE");

		assertThat(liste).hasSize(48).allSatisfy(s -> assertThat(s.niveauRisque()).isBetween(1, 4));
		assertThat(liste).filteredOn(SupportEligible::nonCote).hasSize(12);
	}

	@Test
	@DisplayName("Critère 3 : profil dynamique, assurance vie : 92 supports, aucun de niveau 7 (ex. « Actions "
			+ "Intelligence Artificielle »)")
	void dynamiqueAv() {
		List<SupportEligible> liste = service.lister(Produit.AV, "DYNAMIQUE");

		assertThat(liste).hasSize(92).noneMatch(s -> s.niveauRisque() == 7)
				.noneMatch(s -> s.nom().equals("Actions Intelligence Artificielle"));
	}

	@Test
	@DisplayName("Le niveau 7 n'est jamais proposé en ligne, même si la matrice était réglée au-delà de 6")
	void niveau7Jamais() {
		SupportsEligibles matriceTropLarge = new SupportsEligibles(supports, 7, 7, 7, BigDecimal.ONE, BigDecimal.ONE);

		assertThat(matriceTropLarge.lister(Produit.AV, "DYNAMIQUE")).noneMatch(s -> s.niveauRisque() == 7);
	}

	@Test
	@DisplayName("Critère 5 : un support au statut « retire » (« SCPI Bureaux Paris (fermée) ») n'apparaît pas")
	void retireAbsent() {
		assertThat(service.lister(Produit.PER, "DYNAMIQUE")).extracting(SupportEligible::nom)
				.doesNotContain("SCPI Bureaux Paris (fermée)");
	}

	@Test
	@DisplayName("Critère 6 : un support ouvert à l'assurance vie seulement n'apparaît pas dans le PER")
	void ouvertAuProduitSeulement() {
		List<String> avSeulement = supports.findByStatutOrderByNom(Support.ACTIF).stream()
				.filter(s -> s.getProduitsOuverts().equals("AV") && s.getNiveauRisque() <= 6)
				.map(Support::getCodeIsin)
				.toList();
		List<String> per = service.lister(Produit.PER, "DYNAMIQUE").stream().map(SupportEligible::codeIsin).toList();
		List<String> av = service.lister(Produit.AV, "DYNAMIQUE").stream().map(SupportEligible::codeIsin).toList();

		assertThat(avSeulement).isNotEmpty();
		assertThat(av).containsAll(avSeulement);
		assertThat(per).doesNotContainAnyElementsOf(avSeulement);
	}

	@Test
	@DisplayName("Critère 7 : « SCPI Bureaux Europe » (VL mensuelle) est affichée avec sa dernière VL connue, du "
			+ "01/11/2026, et cette date")
	void derniereVlConnue() {
		assertThat(service.lister(Produit.AV, "PRUDENT")).filteredOn(s -> s.nom().equals("SCPI Bureaux Europe"))
				.singleElement().satisfies(s -> {
					assertThat(s.dateVl()).isEqualTo(LocalDate.of(2026, 11, 1));
					assertThat(s.valeurLiquidative()).isEqualByComparingTo("773.32");
					assertThat(s.nonCote()).isTrue();
				});
	}

	@Test
	@DisplayName("Critère 8 : frais de gestion du contrat (0,50 % PER, 0,65 % AV) distincts des frais du support")
	void fraisSepares() {
		assertThat(service.fraisContrat(Produit.PER)).isEqualByComparingTo("0.50");
		assertThat(service.fraisContrat(Produit.AV)).isEqualByComparingTo("0.65");
		// AV-07 : maximum de risque par profil, lu par le module Gestion.
		assertThat(service.niveauRisqueMax("PRUDENT")).isEqualTo(3);
		assertThat(service.niveauRisqueMax("EQUILIBRE")).isEqualTo(4);
		assertThat(service.niveauRisqueMax("DYNAMIQUE")).isEqualTo(6);
		assertThat(service.niveauRisqueMax(null)).isZero();
		assertThat(service.lister(Produit.AV, "PRUDENT")).filteredOn(s -> s.nom().equals("SCPI Bureaux Europe"))
				.singleElement().satisfies(s -> assertThat(s.fraisSupportPct()).isEqualByComparingTo("1.60"));
	}

	@Test
	@DisplayName("Tri par défaut : niveau de risque croissant, puis nom ; label absent si « aucun »")
	void triEtLabel() {
		List<SupportEligible> liste = service.lister(Produit.AV, "DYNAMIQUE");

		assertThat(liste).isSortedAccordingTo(
				Comparator.comparingInt(SupportEligible::niveauRisque).thenComparing(SupportEligible::nom, francais()));
		assertThat(liste).extracting(SupportEligible::label).doesNotContain("aucun")
				.contains("ISR", "Greenfin", "Finansol", null);
	}

	private static Comparator<String> francais() {
		java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.FRENCH);
		collator.setStrength(java.text.Collator.PRIMARY);
		return collator::compare;
	}

	private static Support support(String nom, int niveauRisque) {
		return new Support(new LecteurCatalogue.LigneCatalogue(1, "QZ" + Math.abs(nom.hashCode()), nom, "Actions",
				"Actions", "PER|AV", niveauRisque, true, BigDecimal.ONE, Support.ACTIF, BigDecimal.TEN,
				LocalDate.of(2026, 11, 20), "EUR", "quotidienne", "aucun", "SG"),
				OffsetDateTime.of(2026, 11, 23, 2, 0, 0, 0, ZoneOffset.ofHours(1)));
	}

	@Test
	@DisplayName("CU-417 : étant donné « Crédit Euro ISR » et « Crédit Euro Investment Grade » de même niveau de "
			+ "risque, alors « Crédit Euro Investment Grade » apparaît avant « Crédit Euro ISR » ; « Énergie » avant "
			+ "« Europe » (ordre alphabétique français, sans majuscules ni accents)")
	void triAlphabetiqueFrancais() {
		when(supports.findByStatutOrderByNom(anyString())).thenReturn(List.of(support("Crédit Euro ISR", 3),
				support("Europe", 3), support("Crédit Euro Investment Grade", 3), support("Énergie", 3),
				support("actions Asie", 3), support("Zinc", 2)));

		assertThat(service.lister(Produit.AV, "DYNAMIQUE")).extracting(SupportEligible::nom)
				.containsExactly("Zinc", "actions Asie", "Crédit Euro Investment Grade", "Crédit Euro ISR", "Énergie",
						"Europe");
	}

	@Test
	@DisplayName("Profil inconnu ou absent : aucune unité de compte")
	void profilInconnu() {
		assertThat(service.lister(Produit.AV, "INCONNU")).isEmpty();
		assertThat(service.lister(Produit.AV, null)).isEmpty();
	}

}
