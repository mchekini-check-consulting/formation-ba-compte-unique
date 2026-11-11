package com.example.compteunique.beneficiaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.beneficiaire.BeneficiaireService.AjoutEnAttente;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Iban;

// CU-146 (US-22) : un cas de test par critère d'acceptation (le critère 17 côté base : BeneficiairesEtVirementsIT).
class BeneficiaireServiceTest {

	private static final String JETON = "jeton";
	private static final String IBAN = "FR7699999000019900000000202";
	private static final String NOM = "Test Cliente-B";
	private static final String CODE = "123456";
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-11T09:00:00Z"), ZoneId.of("Europe/Paris"));
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.now(HORLOGE);

	private final CompteService comptes = mock(CompteService.class);
	private final BeneficiaireRepository beneficiaires = mock(BeneficiaireRepository.class);
	private final AjoutBeneficiaireRepository ajouts = mock(AjoutBeneficiaireRepository.class);
	private final VerificationBeneficiaire verification = mock(VerificationBeneficiaire.class);
	private final CodesSms codes = mock(CodesSms.class);
	private final Compte compte = new Compte(3L, CLIENTE, Iban.de("99000000001"), 7L, MAINTENANT);

	private BeneficiaireService service(boolean ibanDeTestAcceptes) {
		return new BeneficiaireService(comptes, beneficiaires, ajouts, verification, codes, ibanDeTestAcceptes,
				HORLOGE);
	}

	private final BeneficiaireService service = service(true);

	@BeforeEach
	void clienteConnecteeVerificationOk() {
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(verification.verifier(IBAN, NOM)).thenReturn(new Verification(ResultatVerification.CORRESPOND, NOM));
		when(codes.envoyer(any(), any())).thenReturn(CodesSms.hacher(CODE));
		when(ajouts.save(any())).thenAnswer(appel -> {
			AjoutBeneficiaire ajout = appel.getArgument(0);
			ReflectionTestUtils.setField(ajout, "id", 11L);
			return ajout;
		});
		when(beneficiaires.save(any())).thenAnswer(appel -> appel.getArgument(0));
	}

	private static void refus(HttpStatus statut, String message, Executable demande) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, demande);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	private AjoutBeneficiaire ajoutEnAttente(OffsetDateTime envoyeLe, int codesFaux) {
		AjoutBeneficiaire ajout = new AjoutBeneficiaire(CLIENTE, IBAN,
				new Verification(ResultatVerification.CORRESPOND, NOM), "Papa", CodesSms.hacher(CODE), envoyeLe);
		ReflectionTestUtils.setField(ajout, "codesFaux", codesFaux);
		when(ajouts.verrouiller(11L, CLIENTE)).thenReturn(Optional.of(ajout));
		return ajout;
	}

	private Beneficiaire beneficiaireActif(Long id) {
		AjoutBeneficiaire ajout = new AjoutBeneficiaire(CLIENTE, IBAN,
				new Verification(ResultatVerification.CORRESPOND, NOM), "Papa", "x$y", MAINTENANT);
		Beneficiaire beneficiaire = new Beneficiaire(ajout, MAINTENANT);
		ReflectionTestUtils.setField(beneficiaire, "id", id);
		when(beneficiaires.findByIdAndCustomerIdAndStatut(id, CLIENTE, Beneficiaire.ACTIF))
				.thenReturn(Optional.of(beneficiaire));
		return beneficiaire;
	}

	@Test
	void critere1et8_uneSaisieVerifieeEnvoieUnCodeEtNEnregistreRienEncore() {
		AjoutEnAttente ajout = service.demanderAjout(JETON, "fr76 9999 9000 0199 0000 0000 202", " " + NOM, "Papa");

		assertEquals(new AjoutEnAttente(11L, NOM, BeneficiaireService.CODE_ENVOYE), ajout);
		ArgumentCaptor<AjoutBeneficiaire> enAttente = ArgumentCaptor.forClass(AjoutBeneficiaire.class);
		verify(ajouts).save(enAttente.capture());
		assertEquals(IBAN, enAttente.getValue().getIban());
		assertEquals("Papa", enAttente.getValue().getLibelle());
		assertEquals(MAINTENANT, enAttente.getValue().getCodeEnvoyeLe());
		verify(beneficiaires, never()).save(any());
	}

	@Test
	void leSmsContientLeCodeEtSansLibelleLeNomVerifieSertDeLibelle() {
		service.demanderAjout(JETON, IBAN, NOM, "  ");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<UnaryOperator<String>> texte = ArgumentCaptor.forClass(UnaryOperator.class);
		verify(codes).envoyer(any(), texte.capture());
		assertThat(texte.getValue().apply("654321")).contains("654321").contains("bénéficiaire");
		ArgumentCaptor<AjoutBeneficiaire> enAttente = ArgumentCaptor.forClass(AjoutBeneficiaire.class);
		verify(ajouts).save(enAttente.capture());
		assertEquals(NOM, enAttente.getValue().getLibelle());
	}

	@Test
	void critere2_unIbanInvalideEstRefuse() {
		refus(HttpStatus.BAD_REQUEST, "IBAN invalide", () -> service.demanderAjout(JETON, "FR76 1234", NOM, "Papa"));
		verify(verification, never()).verifier(anyString(), anyString());
	}

	@Test
	void critere3_unNomVideEstRefuse() {
		refus(HttpStatus.BAD_REQUEST, "Indiquez un nom pour ce bénéficiaire",
				() -> service.demanderAjout(JETON, IBAN, "  ", "Papa"));
		refus(HttpStatus.BAD_REQUEST, "Indiquez un nom pour ce bénéficiaire",
				() -> service.demanderAjout(JETON, IBAN, null, "Papa"));
	}

	@Test
	void unLibelleTropLongEstRefuse() {
		refus(HttpStatus.BAD_REQUEST, "Libellé trop long",
				() -> service.demanderAjout(JETON, IBAN, NOM, "x".repeat(71)));
	}

	@Test
	void critere4_unNomQuiNeCorrespondPasEstRefuse() {
		when(verification.verifier(IBAN, "Autre Nom")).thenReturn(Verification.sans(ResultatVerification.NE_CORRESPOND_PAS));

		refus(HttpStatus.UNPROCESSABLE_CONTENT, "Le nom ne correspond pas à ce compte. Vérifiez la saisie.",
				() -> service.demanderAjout(JETON, IBAN, "Autre Nom", "Papa"));
		verify(ajouts, never()).save(any());
	}

	@Test
	void critere5_unIbanIntrouvableEstRefuse() {
		when(verification.verifier(IBAN, NOM)).thenReturn(Verification.sans(ResultatVerification.INTROUVABLE));

		refus(HttpStatus.UNPROCESSABLE_CONTENT, "Ce compte n’a pas pu être trouvé. Vérifiez l’IBAN.",
				() -> service.demanderAjout(JETON, IBAN, NOM, "Papa"));
	}

	@Test
	void critere6_uneVerificationIndisponibleEstRefusee() {
		when(verification.verifier(IBAN, NOM)).thenReturn(Verification.sans(ResultatVerification.INDISPONIBLE));

		refus(HttpStatus.SERVICE_UNAVAILABLE, "La vérification est momentanément indisponible. Réessayez plus tard.",
				() -> service.demanderAjout(JETON, IBAN, NOM, "Papa"));
		verify(codes, never()).envoyer(any(), any());
	}

	@Test
	void critere7_unIbanDejaEnregistreEtActifEstRefuse() {
		when(beneficiaires.existsByCustomerIdAndIbanAndStatut(CLIENTE, IBAN, Beneficiaire.ACTIF)).thenReturn(true);

		refus(HttpStatus.CONFLICT, "Ce bénéficiaire existe déjà", () -> service.demanderAjout(JETON, IBAN, NOM, "Papa"));
		verify(verification, never()).verifier(anyString(), anyString());
	}

	@Test
	void critere8_leBonCodeEnregistreLeBeneficiaire() {
		AjoutBeneficiaire ajout = ajoutEnAttente(MAINTENANT.minusMinutes(4), 0);

		Beneficiaire beneficiaire = service.confirmerAjout(JETON, 11L, CODE);

		assertEquals(AjoutBeneficiaire.CONFIRME, ajout.getStatut());
		assertThat(beneficiaire.isActif()).isTrue();
		assertEquals(IBAN, beneficiaire.getIban());
		assertEquals(NOM, beneficiaire.getNomVerifie());
		assertEquals("Papa", beneficiaire.getLibelle());
		assertEquals(ResultatVerification.CORRESPOND, beneficiaire.getResultatVerification());
		assertEquals(CLIENTE, beneficiaire.getCustomerId());
	}

	@Test
	void critere9_unCodeIncorrectNEnregistreRienEtLeTroisiemeAnnuleLAjout() {
		AjoutBeneficiaire ajout = ajoutEnAttente(MAINTENANT, 0);

		refus(HttpStatus.BAD_REQUEST, "Code incorrect", () -> service.confirmerAjout(JETON, 11L, "000000"));
		refus(HttpStatus.BAD_REQUEST, "Code incorrect", () -> service.confirmerAjout(JETON, 11L, "000000"));
		DemandeRefusee troisieme = assertThrows(DemandeRefusee.class, () -> service.confirmerAjout(JETON, 11L, "000000"));

		assertEquals(BeneficiaireService.AJOUT_ANNULE, troisieme.getMessage());
		assertThat(troisieme.isTermine()).isTrue();
		assertEquals(3, ajout.getCodesFaux());
		assertEquals(AjoutBeneficiaire.ANNULE, ajout.getStatut());
		verify(beneficiaires, never()).save(any());
	}

	@Test
	void critere10_unCodeExpireOuDejaUtiliseEstRefuse() {
		AjoutBeneficiaire ajout = ajoutEnAttente(MAINTENANT.minusMinutes(5).minusSeconds(1), 0);

		refus(HttpStatus.GONE, "Code expiré", () -> service.confirmerAjout(JETON, 11L, CODE));
		assertEquals(AjoutBeneficiaire.EXPIRE, ajout.getStatut());

		ajout.terminer(AjoutBeneficiaire.CONFIRME);
		refus(HttpStatus.GONE, "Code expiré", () -> service.confirmerAjout(JETON, 11L, CODE));
		verify(beneficiaires, never()).save(any());
	}

	@Test
	void unAjoutDUneAutreClienteEstTraiteCommeExpire() {
		refus(HttpStatus.GONE, "Code expiré", () -> service.confirmerAjout(JETON, 99L, CODE));
	}

	@Test
	void unDoublonCreeEntreLaDemandeEtLeCodeAnnuleLAjout() {
		AjoutBeneficiaire ajout = ajoutEnAttente(MAINTENANT, 0);
		when(beneficiaires.existsByCustomerIdAndIbanAndStatut(CLIENTE, IBAN, Beneficiaire.ACTIF)).thenReturn(true);

		DemandeRefusee refus = assertThrows(DemandeRefusee.class, () -> service.confirmerAjout(JETON, 11L, CODE));

		assertEquals("Ce bénéficiaire existe déjà", refus.getMessage());
		assertThat(refus.isTermine()).isTrue();
		assertEquals(AjoutBeneficiaire.ANNULE, ajout.getStatut());
	}

	@Test
	void critere11et15_laListeNeContientQueLesBeneficiairesActifsDeLaCliente() {
		Beneficiaire actif = beneficiaireActif(5L);
		when(beneficiaires.findByCustomerIdAndStatutOrderByAjouteLeDescIdDesc(CLIENTE, Beneficiaire.ACTIF))
				.thenReturn(List.of(actif));

		assertEquals(List.of(actif), service.lister(JETON));
	}

	@Test
	void critere12_auDelaDe50BeneficiairesActifsLAjoutEstRefuse() {
		when(beneficiaires.countByCustomerIdAndStatut(CLIENTE, Beneficiaire.ACTIF)).thenReturn(50L);

		refus(HttpStatus.CONFLICT, "Vous avez atteint le nombre maximum de bénéficiaires",
				() -> service.demanderAjout(JETON, IBAN, NOM, "Papa"));
	}

	@Test
	void critere13_seulLeLibelleChange() {
		Beneficiaire beneficiaire = beneficiaireActif(5L);

		service.renommer(JETON, 5L, " Maman ");

		assertEquals("Maman", beneficiaire.getLibelle());
		assertEquals(NOM, beneficiaire.getNomVerifie());
		assertEquals(IBAN, beneficiaire.getIban());
		service.renommer(JETON, 5L, null);
		assertEquals(NOM, beneficiaire.getLibelle());
		refus(HttpStatus.BAD_REQUEST, "Libellé trop long", () -> service.renommer(JETON, 5L, "x".repeat(71)));
	}

	@Test
	void critere15et16_laSuppressionChangeLeStatutEtUneSecondeSuppressionEstRefusee() {
		Beneficiaire beneficiaire = beneficiaireActif(5L);

		service.supprimer(JETON, 5L);

		assertEquals(Beneficiaire.SUPPRIME, beneficiaire.getStatut());
		assertEquals(MAINTENANT, beneficiaire.getSupprimeLe());
		when(beneficiaires.findByIdAndCustomerIdAndStatut(5L, CLIENTE, Beneficiaire.ACTIF)).thenReturn(Optional.empty());
		refus(HttpStatus.NOT_FOUND, "Bénéficiaire introuvable", () -> service.supprimer(JETON, 5L));
	}

	@Test
	void critere17_leBeneficiaireDUneAutreClienteEstIntrouvable() {
		refus(HttpStatus.NOT_FOUND, "Bénéficiaire introuvable", () -> service.renommer(JETON, 77L, "x"));
		refus(HttpStatus.NOT_FOUND, "Bénéficiaire introuvable", () -> service.actif(UUID.randomUUID(), 5L));
	}

	@Test
	void critere18_laDateDAjoutEstEnregistree() {
		ajoutEnAttente(MAINTENANT, 0);

		assertEquals(MAINTENANT, service.confirmerAjout(JETON, 11L, CODE).getAjouteLe());
	}

	@Test
	void critere19_enProductionUnIbanDeTestEstRefuse() {
		refus(HttpStatus.UNPROCESSABLE_CONTENT, "Ce compte n’a pas pu être trouvé. Vérifiez l’IBAN.",
				() -> service(false).demanderAjout(JETON, IBAN, NOM, "Papa"));
		verify(verification, never()).verifier(anyString(), anyString());
	}

}
