package com.example.compteunique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilClientService.Etat;
import com.example.compteunique.profil.ProfilClientService.Statut;
import com.example.compteunique.profil.ProfilKyc;

// CU-412 (CAT-01B) : route GET /catalogue/supports de la cliente connectée (l'écran « Supports disponibles »).
class SupportsControllerTest {

	private static final SupportEligible SCPI = new SupportEligible("QZ0000000751", "SCPI Bureaux Europe", "SCPI", 3,
			new BigDecimal("1.60"), new BigDecimal("773.32"), LocalDate.of(2026, 11, 1), true, null);

	private final SupportsEligibles eligibles = mock(SupportsEligibles.class);
	private final ProfilClientService profils = mock(ProfilClientService.class);
	private final SupportsController routes = new SupportsController(eligibles, profils, new SimulationSupports());

	private void profil(Statut statut, String profilInvestisseur) {
		ProfilKyc profil = mock(ProfilKyc.class);
		when(profil.getProfilInvestisseur()).thenReturn(profilInvestisseur);
		when(profils.etat("jeton")).thenReturn(new Etat(statut, Optional.of(profil)));
	}

	@Test
	@DisplayName("Critère 4 : profil validé prudent, PER : la route renvoie les supports du service, avec les frais du "
			+ "contrat et le profil")
	void profilValide() {
		profil(Statut.VALIDE, "PRUDENT");
		when(eligibles.lister(Produit.PER, "PRUDENT")).thenReturn(List.of(SCPI));
		when(eligibles.fraisContrat(Produit.PER)).thenReturn(new BigDecimal("0.50"));

		var vue = routes.supports("jeton", "PER");

		assertThat(vue.profilValide()).isTrue();
		assertThat(vue.profilInvestisseur()).isEqualTo("PRUDENT");
		assertThat(vue.fraisContratPct()).isEqualByComparingTo("0.50");
		assertThat(vue.supports()).containsExactly(SCPI);
		assertThat(vue.message()).isNull();
	}

	@Test
	@DisplayName("Critère 9 : profil « à revalider » (ou « à compléter ») : aucune unité de compte, message avec le "
			+ "lien vers le questionnaire")
	void profilNonValide() {
		for (Statut statut : List.of(Statut.A_REVALIDER, Statut.A_COMPLETER)) {
			profil(statut, "DYNAMIQUE");

			var vue = routes.supports("jeton", "AV");

			assertThat(vue.profilValide()).isFalse();
			assertThat(vue.supports()).isEmpty();
			assertThat(vue.message()).isEqualTo("Votre profil doit être validé pour investir en unités de compte");
		}
		when(profils.etat("jeton")).thenReturn(new Etat(Statut.A_COMPLETER, Optional.empty()));
		assertThat(routes.supports("jeton", "AV").profilInvestisseur()).isNull();
		verify(eligibles, never()).lister(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("CU-417 critère 11 : en test, la liste des supports simulée indisponible répond 503 sans liste ; "
			+ "revenue disponible, elle répond normalement")
	void serviceIndisponibleSimule() {
		SimulationSupports simulation = new SimulationSupports();
		SupportsController avecSimulation = new SupportsController(eligibles, profils, simulation);
		profil(Statut.VALIDE, "PRUDENT");
		when(eligibles.lister(Produit.PER, "PRUDENT")).thenReturn(List.of(SCPI));

		simulation.indisponible(true);
		assertThatThrownBy(() -> avecSimulation.supports("jeton", "PER"))
				.isInstanceOf(SupportsController.ListeIndisponible.class);
		assertThat(avecSimulation.indisponible().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(avecSimulation.indisponible().getBody().message())
				.isEqualTo("La liste des supports est momentanément indisponible");

		simulation.indisponible(false);
		assertThat(avecSimulation.supports("jeton", "PER").supports()).containsExactly(SCPI);
	}

	@Test
	@DisplayName("CU-417 : page de test, l'interrupteur « liste des supports indisponible » se lit et se change")
	void interrupteurDeTest() {
		SimulationSupports simulation = new SimulationSupports();
		CatalogueTestController routes = new CatalogueTestController(mock(ChargementCatalogue.class),
				mock(CatalogueService.class), simulation);

		assertThat(routes.simulationSupports().indisponible()).isFalse();
		assertThat(routes.simulerSupports(new CatalogueTestController.Simulation(true)).indisponible()).isTrue();
		assertThat(simulation.estIndisponible()).isTrue();
	}

	@Test
	@DisplayName("Produit inconnu : 400 ; cliente non connectée : 401 (renvoi vers la connexion)")
	void refus() {
		assertThatThrownBy(() -> routes.supports("jeton", "LIVRET")).isInstanceOf(SupportsController.ProduitInconnu.class);
		assertThat(routes.produitInconnu().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		when(profils.etat(null)).thenThrow(new ClienteNonConnecteeException());
		assertThatThrownBy(() -> routes.supports(null, "PER")).isInstanceOf(ClienteNonConnecteeException.class);
		assertThat(routes.nonConnectee().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(routes.sansCompte().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
