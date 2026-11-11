package com.example.compteunique.beneficiaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.virement.IbanBeneficiaire;

// CU-145 (TECH-06) : un cas de test par critère d'acceptation.
class VerificationBeneficiaireTest {

	private static final String IBAN_TEST = "FR7699999000019900000000105";
	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-11T09:00:00Z"), ZoneId.of("Europe/Paris"));

	private final CompteRepository comptes = mock(CompteRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final AppelVerificationRepository journal = mock(AppelVerificationRepository.class);
	private final BouchonVerificationRepository bouchon = mock(BouchonVerificationRepository.class);

	private VerificationBeneficiaire bouchon(boolean actif) {
		return new VerificationBeneficiaire(comptes, dossiers, journal, bouchon, actif, HORLOGE);
	}

	@BeforeEach
	void compteDeTestDeTestClienteA() {
		Compte compte = new Compte(3L, UUID.randomUUID(), Iban.de("99000000001"), 7L, OffsetDateTime.now());
		Dossier dossier = new Dossier("demo");
		ReflectionTestUtils.setField(dossier, "prenoms", "Test");
		ReflectionTestUtils.setField(dossier, "nomNaissance", "Cliente-A");
		when(comptes.findByIban(IBAN_TEST)).thenReturn(Optional.of(compte));
		when(dossiers.findById(3L)).thenReturn(Optional.of(dossier));
		when(bouchon.findById(BouchonVerification.ID)).thenReturn(Optional.of(new BouchonVerification(false)));
	}

	private AppelVerification appelTrace() {
		ArgumentCaptor<AppelVerification> appel = ArgumentCaptor.forClass(AppelVerification.class);
		verify(journal).save(appel.capture());
		return appel.getValue();
	}

	@Test
	void critere1_leNomDuTitulaireCorrespondEtEstRenvoye() {
		Verification verification = bouchon(true).verifier(IBAN_TEST, "Test Cliente-A");

		assertEquals(ResultatVerification.CORRESPOND, verification.resultat());
		assertEquals("Test Cliente-A", verification.nomVerifie());
	}

	@Test
	void critere2_unAutreNomNeCorrespondPas() {
		Verification verification = bouchon(true).verifier(IBAN_TEST, "Test Cliente-B");

		assertEquals(ResultatVerification.NE_CORRESPOND_PAS, verification.resultat());
		assertNull(verification.nomVerifie());
	}

	@Test
	void critere3_unIbanAbsentDeLaListeDeTestEstIntrouvable() {
		assertEquals(ResultatVerification.INTROUVABLE,
				bouchon(true).verifier("GB82WEST12345698765432", "Test Cliente-A").resultat());
		assertEquals(ResultatVerification.INTROUVABLE,
				bouchon(true).verifier("FR7699999000019900000000881", "Test Cliente-A").resultat());
	}

	@Test
	void unCompteDUneAutreBanqueConnuDuBouchonCorrespondASonTitulaire() {
		Verification verification = bouchon(true).verifier("DE89370400440532013000", "jean martin");

		assertEquals(ResultatVerification.CORRESPOND, verification.resultat());
		assertEquals("Jean Martin", verification.nomVerifie());
		assertEquals(ResultatVerification.NE_CORRESPOND_PAS,
				bouchon(true).verifier("FR7630006000011234567890189", "Jean Martin").resultat());
		assertEquals(ResultatVerification.CORRESPOND,
				bouchon(true).verifier("FR7616958000091234568778868", "gabriel garcia").resultat());
	}

	@Test
	void lesComptesDesAutresBanquesOntUnIbanValideUniqueHorsBanqueDeTest() {
		assertThat(VerificationBeneficiaire.COMPTES_EXTERNES).hasSize(15)
				.extracting(VerificationBeneficiaire.CompteExterne::iban).doesNotHaveDuplicates();
		assertThat(VerificationBeneficiaire.COMPTES_EXTERNES).allSatisfy(compte -> {
			assertThat(IbanBeneficiaire.estValide(compte.iban())).as(compte.iban()).isTrue();
			assertThat(IbanBeneficiaire.estDeTest(compte.iban())).isFalse();
		});
	}

	@Test
	void critere4_leSelecteurServiceIndisponibleRendIndisponible() {
		when(bouchon.findById(BouchonVerification.ID)).thenReturn(Optional.of(new BouchonVerification(true)));

		assertEquals(ResultatVerification.INDISPONIBLE, bouchon(true).verifier(IBAN_TEST, "Test Cliente-A").resultat());
	}

	@Test
	void critere5_casseAccentsEtTiretsSontIgnores() {
		assertEquals(ResultatVerification.CORRESPOND,
				bouchon(true).verifier(IBAN_TEST, "TÉST cliente a").resultat());
	}

	@Test
	void critere6_enProductionToutesLesDemandesSontRefuseesSansJournal() {
		assertEquals(ResultatVerification.INDISPONIBLE, bouchon(false).verifier(IBAN_TEST, "Test Cliente-A").resultat());

		verify(journal, never()).save(any());
	}

	@Test
	void critere7_chaqueAppelEstTraceAvecLaDateLIbanEtLeResultat() {
		bouchon(true).verifier(IBAN_TEST, "Test Cliente-B");

		AppelVerification appel = appelTrace();
		assertEquals(OffsetDateTime.now(HORLOGE), appel.getAppeleLe());
		assertEquals(IBAN_TEST, appel.getIban());
		assertEquals(ResultatVerification.NE_CORRESPOND_PAS, appel.getResultat());
	}

	@Test
	void leSelecteurEstEnregistreEtLeJournalLu() {
		BouchonVerification selecteur = new BouchonVerification(false);
		when(bouchon.findById(BouchonVerification.ID)).thenReturn(Optional.of(selecteur));
		AppelVerification appel = new AppelVerification(OffsetDateTime.now(), IBAN_TEST, ResultatVerification.CORRESPOND);
		when(journal.findTop50ByOrderByAppeleLeDescIdDesc()).thenReturn(List.of(appel));
		VerificationBeneficiaire verification = bouchon(true);

		verification.rendreIndisponible(true);

		assertThat(selecteur.isIndisponible()).isTrue();
		verify(bouchon).save(selecteur);
		assertEquals(List.of(appel), verification.journal());
	}

	@Test
	void sansLigneDeSelecteurLeServiceEstDisponibleEtLaLigneEstCreee() {
		when(bouchon.findById(BouchonVerification.ID)).thenReturn(Optional.empty());
		VerificationBeneficiaire verification = bouchon(true);

		assertThat(verification.estIndisponible()).isFalse();
		verification.rendreIndisponible(true);

		ArgumentCaptor<BouchonVerification> selecteur = ArgumentCaptor.forClass(BouchonVerification.class);
		verify(bouchon).save(selecteur.capture());
		assertThat(selecteur.getValue().isIndisponible()).isTrue();
	}

	@Test
	void unNomDeTitulaireTropLongEstCoupeA70Caracteres() {
		Dossier dossier = new Dossier("long");
		ReflectionTestUtils.setField(dossier, "prenoms", "P".repeat(50));
		ReflectionTestUtils.setField(dossier, "nomNaissance", "N".repeat(50));

		assertEquals(Beneficiaire.NOM_MAX, VerificationBeneficiaire.titulaire(dossier).length());
	}

}
