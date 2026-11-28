package com.example.compteunique.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.gestion.Allocation.AllocationImpossible;
import com.example.compteunique.gestion.GestionController.Choix;
import com.example.compteunique.gestion.GestionController.DemandeContrat;
import com.example.compteunique.gestion.GestionController.DemandeVersement;
import com.example.compteunique.gestion.GestionTestController.DemandeFrais;
import com.example.compteunique.gestion.GestionTestController.ModificationGrille;
import com.example.compteunique.gestion.GestionTestController.ModificationMandat;
import com.example.compteunique.gestion.GestionTestController.ModificationQuota;
import com.example.compteunique.gestion.ModeDeGestion.ChoixRefuse;
import com.example.compteunique.gestion.ModeDeGestion.ContratIntrouvable;
import com.example.compteunique.gestion.ModeDeGestion.DemandeInvalide;
import com.example.compteunique.gestion.ModeDeGestion.Etat;
import com.example.compteunique.gestion.ModeDeGestion.Mandat;
import com.example.compteunique.gestion.ModeDeGestion.ProfilRequis;
import com.example.compteunique.gestion.ModeDeGestion.Versement;

// CU-314 / CU-338 : routes /gestion (cliente connectée) et /test/gestion (page de test).
class GestionControllersTest {

	private static final OffsetDateTime LE = OffsetDateTime.parse("2026-11-26T15:30:00+01:00");

	private final ModeDeGestion service = mock(ModeDeGestion.class);
	private final GestionController routes = new GestionController(service);
	private final ParametresGestion parametres = mock(ParametresGestion.class);
	private final FraisDuMandat frais = mock(FraisDuMandat.class);
	private final GestionTestController routesTest = new GestionTestController(parametres, frais, service);

	private static final Etat ETAT = new Etat(Produit.AV, "EQUILIBRE", true,
			new Mandat(new BigDecimal("1.50"), new BigDecimal("50000.00")), null);

	@Test
	@DisplayName("/gestion : état, création du contrat de test (201), choix (200), versement (201) ; 400 si incomplet")
	void routesDeLaCliente() {
		when(service.etat("jeton", Produit.AV)).thenReturn(ETAT);
		when(service.creerContratDeTest("jeton", Produit.AV)).thenReturn(ETAT);
		when(service.choisir("jeton", Produit.AV, ModeGestion.LIBRE, null)).thenReturn(ETAT);
		UUID id = UUID.randomUUID();
		Versement versement = new Versement(id, BigDecimal.TEN, List.of());
		when(service.verser("jeton", Produit.AV, id, BigDecimal.TEN)).thenReturn(versement);

		assertThat(routes.etat("jeton", Produit.AV)).isEqualTo(ETAT);
		assertThat(routes.creerContrat("jeton", new DemandeContrat(Produit.AV)).getStatusCode())
				.isEqualTo(HttpStatus.CREATED);
		assertThat(routes.choisir("jeton", new Choix(Produit.AV, ModeGestion.LIBRE, null)).getBody()).isEqualTo(ETAT);
		assertThat(routes.verser("jeton", new DemandeVersement(Produit.AV, id, BigDecimal.TEN)).getBody())
				.isEqualTo(versement);

		assertThat(routes.creerContrat("jeton", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(routes.choisir("jeton", new Choix(Produit.AV, null, null)).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(routes.choisir("jeton", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(routes.verser("jeton", new DemandeVersement(null, id, BigDecimal.TEN)).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("/gestion : refus métier 422 avec message, profil à revalider 409, contrat absent 404, non connectée 401")
	void codesDeRefus() {
		assertThat(routes.refuse(new ChoixRefuse(ModeDeGestion.PROFIL_TROP_RISQUE)).getStatusCode().value())
				.isEqualTo(422);
		assertThat(routes.refuse(new AllocationImpossible("x")).getBody().message()).isEqualTo("x");
		assertThat(routes.conflit(new ProfilRequis()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(routes.introuvable(new ContratIntrouvable()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(routes.invalide(new DemandeInvalide("y")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(routes.nonConnectee().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(routes.sansCompte().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("/test/gestion : paramètres, modifications et calcul des frais ; absente en preprod et prod")
	void routesDeTest() {
		GrilleHorizonPer ligne = new GrilleHorizonPer(ProfilGestion.DYNAMIQUE, 10, BigDecimal.TEN, BigDecimal.ONE, LE);
		QuotaNonCoteAv quota = new QuotaNonCoteAv(ProfilGestion.DYNAMIQUE, new BigDecimal("8"), LE);
		ParametreMandat mandat = new ParametreMandat(Produit.PER, new BigDecimal("1.5"), new BigDecimal("50000"), LE);
		ContratGestion contrat = new ContratGestion("PER-TEST-1", UUID.randomUUID(), Produit.PER, 2041,
				ProfilGestion.EQUILIBRE, LE);
		FraisMandat calcule = new FraisMandat("PER-TEST-1", LE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.ONE, BigDecimal.ZERO);
		when(parametres.grille()).thenReturn(List.of(ligne));
		when(parametres.quotas()).thenReturn(List.of(quota));
		when(parametres.mandats()).thenReturn(List.of(mandat));
		when(parametres.contrats()).thenReturn(List.of(contrat));
		when(frais.derniers()).thenReturn(List.of(calcule));
		when(parametres.modifierGrille(anyLong(), any(), any())).thenReturn(ligne);
		when(parametres.modifierQuota(any(), any())).thenReturn(quota);
		when(parametres.modifierMandat(any(), any(), any())).thenReturn(mandat);
		when(frais.calculer("PER-TEST-1")).thenReturn(calcule);

		var tout = routesTest.parametres();
		assertThat(tout.grille()).singleElement().satisfies(l -> {
			assertThat(l.aValider()).isTrue();
			assertThat(l.anneesMin()).isEqualTo(10);
		});
		assertThat(tout.quotas()).singleElement().extracting(GestionTestController.Quota::partMinNonCotePct)
				.isEqualTo(new BigDecimal("8"));
		assertThat(tout.mandats()).singleElement().extracting(GestionTestController.Mandat::produit)
				.isEqualTo(Produit.PER);
		assertThat(tout.contrats()).singleElement().extracting(GestionTestController.Contrat::mode)
				.isEqualTo(ModeGestion.PILOTEE);
		assertThat(tout.frais()).singleElement().extracting(GestionTestController.Frais::numeroContrat)
				.isEqualTo("PER-TEST-1");

		assertThat(routesTest.modifierGrille(1L, new ModificationGrille(BigDecimal.TEN, BigDecimal.ONE)).profil())
				.isEqualTo(ProfilGestion.DYNAMIQUE);
		assertThat(routesTest.modifierQuota(ProfilGestion.DYNAMIQUE, new ModificationQuota(BigDecimal.ONE)).profil())
				.isEqualTo(ProfilGestion.DYNAMIQUE);
		assertThat(routesTest.modifierMandat(Produit.PER, new ModificationMandat(BigDecimal.ONE, BigDecimal.ONE))
				.produit()).isEqualTo(Produit.PER);
		assertThat(routesTest.calculer(new DemandeFrais("PER-TEST-1")).ecartEtablissement()).isZero();

		assertThat(routesTest.invalide(new DemandeInvalide("z")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(routesTest.introuvable(new ContratIntrouvable()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(routesTest.horsMandat(new FraisDuMandat.HorsMandat()).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);

		when(service.signalements()).thenReturn(List.of(new SignalementNonCote("AV-TEST-1", "FCPR",
				new BigDecimal("1500.00"), 6, ProfilGestion.PRUDENT, LE)));
		assertThat(routesTest.signalements()).singleElement().satisfies(s -> {
			assertThat(s.codeIsin()).isEqualTo("FCPR");
			assertThat(s.niveauRisque()).isEqualTo(6);
			assertThat(s.statut()).isEqualTo("A_TRAITER");
		});

		ConditionalOnProperty condition = GestionTestController.class.getAnnotation(ConditionalOnProperty.class);
		assertThat(condition.name()).containsExactly("app.test.ordres-simules");
		assertThat(condition.havingValue()).isEqualTo("true");
	}

	@Test
	@DisplayName("Lot 4 : GET /gestion/contrats renvoie les lignes des contrats de la cliente connectée")
	void lignesDesContrats() {
		var ligne = new ModeDeGestion.LigneContrat(Produit.AV, "AV-1", LE, ModeGestion.PILOTEE, ProfilGestion.EQUILIBRE);
		when(service.contrats("jeton")).thenReturn(List.of(ligne));

		assertThat(routes.contrats("jeton")).containsExactly(ligne);
	}

}
