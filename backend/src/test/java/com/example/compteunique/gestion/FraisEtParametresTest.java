package com.example.compteunique.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.gestion.FraisDuMandat.HorsMandat;
import com.example.compteunique.gestion.ModeDeGestion.ContratIntrouvable;
import com.example.compteunique.gestion.ModeDeGestion.DemandeInvalide;
import com.example.compteunique.gestion.ModeDeGestion.Mandat;
import com.example.compteunique.ordres.PositionsContrat;

// CU-314 / CU-338 : frais de la gestion sous mandat plafonnés au taux tout compris, et paramètres modifiables.
class FraisEtParametresTest {

	private static final ZonedDateTime LE = ZonedDateTime.of(2026, 11, 26, 15, 30, 0, 0, DateMetier.PARIS);

	private final ContratGestionRepository contrats = mock(ContratGestionRepository.class);
	private final FraisMandatRepository fraisRepo = mock(FraisMandatRepository.class);
	private final ModeDeGestion gestion = mock(ModeDeGestion.class);
	private final PositionsContrat positions = mock(PositionsContrat.class);
	private final CatalogueService catalogue = mock(CatalogueService.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final GrilleHorizonPerRepository grille = mock(GrilleHorizonPerRepository.class);
	private final QuotaNonCoteAvRepository quotas = mock(QuotaNonCoteAvRepository.class);
	private final ParametreMandatRepository mandats = mock(ParametreMandatRepository.class);
	private final FraisDuMandat frais = new FraisDuMandat(contrats, fraisRepo, gestion, positions, catalogue,
			dateMetier);
	private final ParametresGestion parametres = new ParametresGestion(grille, quotas, mandats, contrats, dateMetier);

	private ContratGestion contrat;

	@BeforeEach
	void preparer() {
		when(dateMetier.maintenant()).thenReturn(LE);
		contrat = new ContratGestion("AV-TEST-0001", UUID.randomUUID(), Produit.AV, null, ProfilGestion.EQUILIBRE,
				LE.toOffsetDateTime());
		when(contrats.findByNumeroContrat("AV-TEST-0001")).thenReturn(Optional.of(contrat));
		when(gestion.mandat(Produit.AV)).thenReturn(new Mandat(new BigDecimal("1.50"), new BigDecimal("50000.00")));
		when(fraisRepo.save(any())).thenAnswer(appel -> appel.getArgument(0));
		when(positions.encoursParSupport("AV-TEST-0001")).thenReturn(Map.of("CHER", new BigDecimal("60000.00"),
				"BONMARCHE", new BigDecimal("40000.00")));
		when(catalogue.support("CHER")).thenReturn(Optional.of(support("CHER", "2.50")));
		when(catalogue.support("BONMARCHE")).thenReturn(Optional.of(support("BONMARCHE", "1.00")));
	}

	private static SupportUc support(String isin, String frais) {
		return new SupportUc(isin, isin, "OPCVM", "Actions", "AV", 5, true, new BigDecimal(frais), false,
				BigDecimal.TEN, LocalDate.of(2026, 11, 25), "EUR", "quotidienne", "aucun", "Gestion");
	}

	@Test
	@DisplayName("Cliente sous mandat dont les supports coûtent plus de 1,5 % par an : elle paie 1,5 %, "
			+ "l'établissement prend l'écart à sa charge")
	void fraisPlafonnes() {
		contrat.changer(ModeGestion.MANDAT, ProfilGestion.EQUILIBRE, null, LE.toOffsetDateTime());

		FraisMandat calcule = frais.calculer(" AV-TEST-0001 ");

		assertThat(calcule.getEncours()).isEqualByComparingTo("100000");
		assertThat(calcule.getFraisCliente()).isEqualByComparingTo("1500");
		// 60 000 × 2,5 % + 40 000 × 1 % = 1 900 €
		assertThat(calcule.getCoutReelSupports()).isEqualByComparingTo("1900");
		assertThat(calcule.getEcartEtablissement()).isEqualByComparingTo("400");
		assertThat(calcule.getTauxToutComprisPct()).isEqualByComparingTo("1.5");
		assertThat(calcule.getNumeroContrat()).isEqualTo("AV-TEST-0001");
		assertThat(calcule.getCalculeLe()).isEqualTo(LE.toOffsetDateTime());
	}

	@Test
	@DisplayName("Supports moins chers que le taux tout compris : aucun écart pour l'établissement")
	void sansEcart() {
		contrat.changer(ModeGestion.MANDAT, ProfilGestion.EQUILIBRE, null, LE.toOffsetDateTime());
		when(catalogue.support("CHER")).thenReturn(Optional.empty());

		FraisMandat calcule = frais.calculer("AV-TEST-0001");

		assertThat(calcule.getCoutReelSupports()).isEqualByComparingTo("400");
		assertThat(calcule.getEcartEtablissement()).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("Frais du mandat : refusés hors mandat ou pour un contrat inconnu")
	void fraisRefuses() {
		assertThatThrownBy(() -> frais.calculer("AV-TEST-0001")).isInstanceOf(HorsMandat.class);
		when(contrats.findByNumeroContrat("")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> frais.calculer(null)).isInstanceOf(ContratIntrouvable.class);
		when(fraisRepo.findTop50ByOrderByIdDesc()).thenReturn(List.of());
		assertThat(frais.derniers()).isEmpty();
	}

	@Test
	@DisplayName("Paramètres : la grille, le quota et le mandat se modifient, avec la date métier ; valeurs contrôlées")
	void modifierLesParametres() {
		OffsetDateTime avant = OffsetDateTime.parse("2026-01-01T00:00:00Z");
		GrilleHorizonPer ligne = new GrilleHorizonPer(ProfilGestion.DYNAMIQUE, 10, BigDecimal.TEN, BigDecimal.ONE,
				avant);
		when(grille.findById(1L)).thenReturn(Optional.of(ligne));
		QuotaNonCoteAv quota = new QuotaNonCoteAv(ProfilGestion.DYNAMIQUE, new BigDecimal("8"), avant);
		when(quotas.findById(ProfilGestion.DYNAMIQUE)).thenReturn(Optional.of(quota));
		ParametreMandat mandat = new ParametreMandat(Produit.PER, new BigDecimal("1.5"), new BigDecimal("50000"),
				avant);
		when(mandats.findById(Produit.PER)).thenReturn(Optional.of(mandat));

		assertThat(parametres.modifierGrille(1L, new BigDecimal("20"), new BigDecimal("9.5"))
				.getPartMinNonCotePct()).isEqualByComparingTo("9.5");
		assertThat(ligne.getModifieLe()).isEqualTo(LE.toOffsetDateTime());
		assertThat(ligne.isAValider()).isTrue();
		assertThat(parametres.modifierQuota(ProfilGestion.DYNAMIQUE, new BigDecimal("10")).getPartMinNonCotePct())
				.isEqualByComparingTo("10");
		assertThat(parametres.modifierMandat(Produit.PER, new BigDecimal("1.20"), new BigDecimal("30000.00"))
				.getMontantMinimum()).isEqualByComparingTo("30000");

		assertThatThrownBy(() -> parametres.modifierGrille(1L, new BigDecimal("60"), new BigDecimal("50")))
				.isInstanceOf(DemandeInvalide.class).hasMessageContaining("100 %");
		assertThatThrownBy(() -> parametres.modifierGrille(1L, null, BigDecimal.ONE))
				.isInstanceOf(DemandeInvalide.class).hasMessageStartingWith("La part minimale d'actifs");
		assertThatThrownBy(() -> parametres.modifierGrille(2L, BigDecimal.ONE, BigDecimal.ONE))
				.isInstanceOf(DemandeInvalide.class).hasMessage("Ligne de grille inconnue.");
		assertThatThrownBy(() -> parametres.modifierQuota(ProfilGestion.DYNAMIQUE, new BigDecimal("101")))
				.isInstanceOf(DemandeInvalide.class).hasMessageStartingWith("Le quota");
		assertThatThrownBy(() -> parametres.modifierQuota(ProfilGestion.PRUDENT, BigDecimal.ONE))
				.isInstanceOf(DemandeInvalide.class);
		assertThatThrownBy(() -> parametres.modifierMandat(Produit.PER, new BigDecimal("1.234"), BigDecimal.ONE))
				.isInstanceOf(DemandeInvalide.class).hasMessageStartingWith("Le taux");
		assertThatThrownBy(() -> parametres.modifierMandat(Produit.PER, BigDecimal.ONE, new BigDecimal("-1")))
				.isInstanceOf(DemandeInvalide.class).hasMessageContaining("montant minimum");
		assertThatThrownBy(() -> parametres.modifierMandat(Produit.AV, BigDecimal.ONE, BigDecimal.ONE))
				.isInstanceOf(DemandeInvalide.class);
	}

	@Test
	@DisplayName("Paramètres : lectures triées pour la page de test")
	void lirePourLaPageDeTest() {
		OffsetDateTime le = LE.toOffsetDateTime();
		when(quotas.findAll()).thenReturn(List.of(new QuotaNonCoteAv(ProfilGestion.DYNAMIQUE, BigDecimal.ONE, le),
				new QuotaNonCoteAv(ProfilGestion.PRUDENT, BigDecimal.ZERO, le)));
		when(mandats.findAll()).thenReturn(List.of(new ParametreMandat(Produit.AV, BigDecimal.ONE, BigDecimal.ONE, le),
				new ParametreMandat(Produit.PER, BigDecimal.ONE, BigDecimal.ONE, le)));
		when(grille.findAllByOrderByProfilAscAnneesMinAsc()).thenReturn(List.of());
		when(contrats.findTop50ByOrderByIdDesc()).thenReturn(List.of(contrat));

		assertThat(parametres.quotas()).extracting(QuotaNonCoteAv::getProfil).containsExactly(ProfilGestion.PRUDENT,
				ProfilGestion.DYNAMIQUE);
		assertThat(parametres.mandats()).extracting(ParametreMandat::getProduit).containsExactly(Produit.PER,
				Produit.AV);
		assertThat(parametres.grille()).isEmpty();
		assertThat(parametres.contrats()).containsExactly(contrat);
	}

}
