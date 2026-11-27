package com.example.compteunique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.compteunique.catalogue.Adequation.SupportInadapte;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;
import com.example.compteunique.datemetier.DateMetier;

// CU-348 (rapport d'adéquation) et CU-349 (contrôle à la validation d'une opération vers une UC).
class AdequationTest {

	private static final ZonedDateTime LE = ZonedDateTime.of(2026, 11, 27, 10, 0, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final SupportEligible MONETAIRE = support("FR0000000001", 1);
	private static final SupportEligible OBLIGATIONS = support("FR0000000002", 3);

	private final SupportsEligibles eligibles = mock(SupportsEligibles.class);
	private final RapportAdequationRepository rapports = mock(RapportAdequationRepository.class);
	private final OperationRefuseeRepository refus = mock(OperationRefuseeRepository.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final Adequation adequation = new Adequation(eligibles, rapports, refus, dateMetier);

	private static SupportEligible support(String isin, int risque) {
		return new SupportEligible(isin, "Support " + isin, "OPCVM", risque, BigDecimal.ONE, BigDecimal.TEN,
				LocalDate.of(2026, 11, 1), false, null);
	}

	@BeforeEach
	void datesEtCatalogue() {
		when(dateMetier.maintenant()).thenReturn(LE);
		when(eligibles.lister(Produit.PER, "PRUDENT")).thenReturn(List.of(MONETAIRE, OBLIGATIONS));
	}

	@Test
	@DisplayName("CU-348 critère 1 : le rapport d'adéquation garde la date, le profil utilisé et les supports proposés")
	void rapport() {
		adequation.rapporter(CLIENTE, Produit.PER, "PRUDENT", List.of(MONETAIRE, OBLIGATIONS));

		ArgumentCaptor<RapportAdequation> rapport = ArgumentCaptor.forClass(RapportAdequation.class);
		verify(rapports).save(rapport.capture());
		assertThat(rapport.getValue().getCustomerId()).isEqualTo(CLIENTE);
		assertThat(rapport.getValue().getProduit()).isEqualTo(Produit.PER);
		assertThat(rapport.getValue().getProfil()).isEqualTo("PRUDENT");
		assertThat(rapport.getValue().getSupports()).isEqualTo("FR0000000001|FR0000000002");
		assertThat(rapport.getValue().getProposeLe()).isEqualTo(LE.toOffsetDateTime());
	}

	@Test
	@DisplayName("CU-349 : un support éligible au profil en vigueur est accepté, sans refus enregistré")
	void supportAdapte() {
		adequation.controler(CLIENTE, Produit.PER, "PRUDENT", "FR0000000002", "VERSEMENT");

		verify(refus, never()).save(any());
	}

	@Test
	@DisplayName("CU-349 critère 2 : un support hors profil (lien direct, page restée ouverte) est refusé avec le "
			+ "message attendu, et le refus est enregistré (UC, profil, date, type d'opération)")
	void supportInadapte() {
		assertThatThrownBy(() -> adequation.controler(CLIENTE, Produit.PER, "PRUDENT", "FR0000000006", "ARBITRAGE"))
				.isInstanceOf(SupportInadapte.class).hasMessage("Ce support ne correspond pas à votre profil");

		ArgumentCaptor<OperationRefusee> refusee = ArgumentCaptor.forClass(OperationRefusee.class);
		verify(refus).save(refusee.capture());
		assertThat(refusee.getValue().getCustomerId()).isEqualTo(CLIENTE);
		assertThat(refusee.getValue().getProduit()).isEqualTo(Produit.PER);
		assertThat(refusee.getValue().getCodeIsin()).isEqualTo("FR0000000006");
		assertThat(refusee.getValue().getProfil()).isEqualTo("PRUDENT");
		assertThat(refusee.getValue().getTypeOperation()).isEqualTo("ARBITRAGE");
		assertThat(refusee.getValue().getRefuseLe()).isEqualTo(LE.toOffsetDateTime());
	}

	@Test
	@DisplayName("CU-349 : sans profil validé en vigueur, aucune UC n'est acceptée")
	void sansProfil() {
		assertThatThrownBy(() -> adequation.controler(CLIENTE, Produit.PER, null, "FR0000000001", "VERSEMENT"))
				.isInstanceOf(SupportInadapte.class);
	}

}
