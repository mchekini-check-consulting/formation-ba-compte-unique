package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteNonActifException;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.compte.MontantInvalideException;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.compte.SoldeInsuffisantException;

class EmissionVirementTest {

	private static final String IBAN = "FR7699999000019900000000105";
	private static final String IBAN_BENEFICIAIRE = "DE89370400440532013000";
	private static final OffsetDateTime OUVERTURE = OffsetDateTime.parse("2026-10-01T09:00:00+02:00");

	private final CompteRepository comptes = mock(CompteRepository.class);
	private final MouvementRepository mouvements = mock(MouvementRepository.class);
	private final EmissionVirement emission = new EmissionVirement(comptes, mouvements);
	private final Compte compte = new Compte(1L, UUID.randomUUID(), Iban.de("99000000001"), 7L, OUVERTURE);

	@BeforeEach
	void compteDeLIbanAvecUnSolde() {
		ReflectionTestUtils.setField(compte, "id", 5L);
		ReflectionTestUtils.setField(compte, "soldeCentimes", 10000L);
		when(comptes.verrouillerParIban(IBAN)).thenReturn(Optional.of(compte));
	}

	private static VirementSortant virement(long montant, String ibanBeneficiaire) {
		return new VirementSortant(IBAN, montant, "Beneficiaire Test", ibanBeneficiaire, "Remboursement", true);
	}

	@Test
	void unVirementAvecUnSoldeSuffisantDiminueLeSoldeEtCreeUnMouvementSimuleDuJour() {
		assertEquals(7000L, emission.emettre(virement(3000, IBAN_BENEFICIAIRE)));

		assertEquals(7000L, compte.getSoldeCentimes());
		assertThat(compte.getSoldeMisAJourLe()).isAfter(OUVERTURE);
		ArgumentCaptor<Mouvement> mouvement = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements).save(mouvement.capture());
		Mouvement enregistre = mouvement.getValue();
		assertEquals(5L, enregistre.getCompteId());
		assertEquals(Mouvement.VIREMENT_SORTANT, enregistre.getType());
		assertThat(enregistre.isSortant()).isTrue();
		assertEquals(3000L, enregistre.getMontantCentimes());
		assertEquals("Remboursement", enregistre.getLibelle());
		assertEquals("Beneficiaire Test", enregistre.getBeneficiaire());
		assertEquals(IBAN_BENEFICIAIRE, enregistre.getIbanBeneficiaire());
		assertThat(enregistre.getEmetteur()).isNull();
		assertThat(enregistre.isSimule()).isTrue();
		assertEquals(compte.getSoldeMisAJourLe(), enregistre.getEffectueLe());
	}

	@Test
	void toutLeSoldePeutEtreVireEtLeSoldeTombeAZero() {
		assertEquals(0L, emission.emettre(virement(10000, IBAN_BENEFICIAIRE)));
	}

	@Test
	void lesIbanSontReconnusAvecEspacesEtMinuscules() {
		VirementSortant virement = new VirementSortant("fr76 9999 9000 0199 0000 0000 105", 100, " Beneficiaire ",
				"de89 3704 0044 0532 0130 00", " Libellé ", true);

		emission.emettre(virement);

		assertEquals("DE89370400440532013000", virement.ibanBeneficiaire());
		assertEquals("Beneficiaire", virement.beneficiaire());
		assertEquals("Libellé", virement.libelle());
	}

	@Test
	void unMontantSuperieurAuSoldeEstRefuseEtLeSoldeNeDevientJamaisNegatif() {
		assertThrows(SoldeInsuffisantException.class, () -> emission.emettre(virement(10001, IBAN_BENEFICIAIRE)));

		assertEquals(10000L, compte.getSoldeCentimes());
		verify(mouvements, never()).save(any());
	}

	@Test
	void unMontantNulOuNegatifEstRefuseSansChercherLeCompte() {
		assertThrows(MontantInvalideException.class, () -> emission.emettre(virement(0, IBAN_BENEFICIAIRE)));
		assertThrows(MontantInvalideException.class, () -> emission.emettre(virement(-100, IBAN_BENEFICIAIRE)));

		verify(comptes, never()).verrouillerParIban(anyString());
		assertEquals(10000L, compte.getSoldeCentimes());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "DE89370400440532013001", "FR7699999000019900000000106", "DE8937040044",
			"1289370400440532013000", "DE89 3704 0044 0532 0130 0!" })
	void unIbanDeBeneficiaireInvalideEstRefuseSansChercherLeCompte(String ibanBeneficiaire) {
		assertThrows(IbanInvalideException.class, () -> emission.emettre(virement(100, ibanBeneficiaire)));

		verify(comptes, never()).verrouillerParIban(anyString());
	}

	@Test
	void unIbanDeBeneficiaireAbsentEstRefuse() {
		assertThrows(IbanInvalideException.class, () -> emission.emettre(virement(100, null)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "FR7630006000011234567890189", "BE68539007547034", "GB82WEST12345698765432",
			"FR7699999000019900000000105" })
	void desIbanValidesDePlusieursPaysSontAcceptes(String ibanBeneficiaire) {
		assertThat(IbanBeneficiaire.estValide(ibanBeneficiaire)).isTrue();
	}

	@Test
	void unCompteClotureEstRefuseSansToucherAuSolde() {
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);

		assertThrows(CompteNonActifException.class, () -> emission.emettre(virement(100, IBAN_BENEFICIAIRE)));

		assertEquals(10000L, compte.getSoldeCentimes());
		verify(mouvements, never()).save(any());
	}

	@Test
	void unCompteInconnuEstRefuse() {
		when(comptes.verrouillerParIban(IBAN)).thenReturn(Optional.empty());

		assertThrows(CompteIntrouvableException.class, () -> emission.emettre(virement(100, IBAN_BENEFICIAIRE)));
	}

	@Test
	void unBeneficiaireOuUnLibelleAbsentOuTropLongEstRefuse() {
		assertThrows(InformationsVirementIncompletesException.class, () -> emission.emettre(
				new VirementSortant(IBAN, 100, " ", IBAN_BENEFICIAIRE, "Libellé", true)));
		assertThrows(InformationsVirementIncompletesException.class, () -> emission.emettre(
				new VirementSortant(IBAN, 100, "Beneficiaire", IBAN_BENEFICIAIRE, null, true)));
		assertThrows(InformationsVirementIncompletesException.class, () -> emission.emettre(
				new VirementSortant(IBAN, 100, "B".repeat(71), IBAN_BENEFICIAIRE, "Libellé", true)));
		assertThrows(InformationsVirementIncompletesException.class, () -> emission.emettre(
				new VirementSortant(IBAN, 100, "Beneficiaire", IBAN_BENEFICIAIRE, "L".repeat(141), true)));
		verify(mouvements, never()).save(any());
	}

	@Test
	void leCompteRefuseLuiMemeUnDebitNulOuSurUnCompteCloture() {
		assertThrows(MontantInvalideException.class, () -> compte.debiter(0, OUVERTURE));
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);
		assertThrows(CompteNonActifException.class, () -> compte.debiter(100, OUVERTURE));
	}

}
