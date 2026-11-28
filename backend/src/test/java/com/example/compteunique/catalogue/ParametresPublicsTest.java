package com.example.compteunique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.compteunique.catalogue.CatalogueService.Produit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// CU-24 (US-00), D1 et D7 du PO : paramètres publics des produits, lus sans connexion par la page publique et l'espace.
class ParametresPublicsTest {

	private final SupportsEligibles eligibles = mock(SupportsEligibles.class);

	@BeforeEach
	void frais() {
		when(eligibles.fraisContrat(Produit.PER)).thenReturn(new BigDecimal("0.50"));
		when(eligibles.fraisContrat(Produit.AV)).thenReturn(new BigDecimal("0.65"));
	}

	private static List<String> champs(JsonNode noeud) {
		List<String> noms = new ArrayList<>();
		noeud.propertyNames().forEach(noms::add);
		return noms;
	}

	@Test
	@DisplayName("La réponse ne contient que les paramètres publics des produits (aucune donnée de cliente), avec un cache "
			+ "court ; date et frais du fonds en euros non renseignés : null (non affichés)")
	void seulsLesChampsPublics() {
		var routes = new ParametresPublicsController(new ParametresPublics(eligibles, "2.90", "", " "));

		var reponse = routes.lire();
		JsonNode json = JsonMapper.builder().build().valueToTree(reponse.getBody());

		assertThat(champs(json)).containsExactlyInAnyOrder("fraisUcPct", "fondsEurosAv", "fondsEurosPer",
				"famillesCategories");
		assertThat(champs(json.get("fraisUcPct"))).containsExactlyInAnyOrder("PER", "AV");
		assertThat(champs(json.get("fondsEurosAv"))).containsExactlyInAnyOrder("tauxBrutVisePct", "dateMiseAJour",
				"fraisGestionPct", "tauxServiPct");
		assertThat(json.get("fraisUcPct").get("AV").decimalValue()).isEqualByComparingTo("0.65");
		assertThat(json.get("fraisUcPct").get("PER").decimalValue()).isEqualByComparingTo("0.50");
		assertThat(json.get("fondsEurosAv").get("tauxBrutVisePct").decimalValue()).isEqualByComparingTo("2.90");
		assertThat(json.get("fondsEurosAv").get("dateMiseAJour").isNull()).isTrue();
		assertThat(json.get("fondsEurosAv").get("fraisGestionPct").isNull()).isTrue();
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("max-age=300, public");
	}

	@Test
	@DisplayName("Paramètres renseignés : date de mise à jour du taux et frais du fonds en euros transmis")
	void parametresRenseignes() {
		var vue = new ParametresPublics(eligibles, "2.90", "2026-08-01", "0.60").lire();

		assertThat(vue.fondsEurosAv().dateMiseAJour()).isEqualTo(LocalDate.of(2026, 8, 1));
		assertThat(vue.fondsEurosAv().fraisGestionPct()).isEqualByComparingTo("0.60");
	}


	@Test
	@DisplayName("Lot 6 : fonds en euros du PER (frais 0,65 %, D1), taux servi et familles vides par défaut")
	void fondsEurosPerEtFamilles() {
		var vue = new ParametresPublics(eligibles, "2.90", "2026-01-01", "", "", "", "", "0.65", "", "").lire();

		assertThat(vue.fondsEurosPer().fraisGestionPct()).isEqualByComparingTo("0.65");
		assertThat(vue.fondsEurosPer().tauxBrutVisePct()).isNull();
		assertThat(vue.fondsEurosAv().tauxServiPct()).isNull();
		assertThat(vue.famillesCategories()).isEmpty();
	}

	@Test
	@DisplayName("Lot 6 : familles renseignées « Catégorie:Famille;... », paires incomplètes ignorées")
	void familles() {
		assertThat(ParametresPublics.familles("SCPI:Immobilier; OPCVM actions : Actions;bancal;:x"))
				.containsOnly(java.util.Map.entry("SCPI", "Immobilier"), java.util.Map.entry("OPCVM actions", "Actions"));
	}

}
