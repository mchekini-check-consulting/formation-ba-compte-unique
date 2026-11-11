package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.beneficiaire.Beneficiaire;
import com.example.compteunique.beneficiaire.BeneficiaireService;
import com.example.compteunique.beneficiaire.CodesSms;
import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.virement.VirementService.Execution;
import com.example.compteunique.virement.VirementService.Recapitulatif;

// CU-147 (US-21) : un cas de test par critère d'acceptation (critère 13 sur une vraie base : BeneficiairesEtVirementsIT).
class VirementServiceTest {

	private static final String JETON = "jeton";
	private static final String CODE = "123456";
	private static final String IBAN_BENEFICIAIRE = "FR7699999000019900000000202";
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-11T09:00:00Z"), ZoneId.of("Europe/Paris"));
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.now(HORLOGE);

	private final CompteService compteService = mock(CompteService.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final MouvementRepository mouvements = mock(MouvementRepository.class);
	private final VirementRepository virements = mock(VirementRepository.class);
	private final BeneficiaireService beneficiaires = mock(BeneficiaireService.class);
	private final CodesSms codes = mock(CodesSms.class);
	private final Beneficiaire beneficiaire = mock(Beneficiaire.class);
	private final Compte compte = new Compte(3L, CLIENTE, Iban.de("99000000001"), 7L, MAINTENANT.minusDays(30));

	private VirementService service(boolean ibanDeTestAcceptes, long plafond) {
		return new VirementService(compteService, comptes, dossiers, mouvements, virements, beneficiaires, codes,
				ibanDeTestAcceptes, plafond, HORLOGE);
	}

	private final VirementService service = service(true, 0);

	@BeforeEach
	void clienteAvecSoldeEtBeneficiaireAncien() {
		ReflectionTestUtils.setField(compte, "id", 5L);
		ReflectionTestUtils.setField(compte, "soldeCentimes", 100_000L);
		when(compteService.compteCourant(JETON)).thenReturn(compte);
		when(compteService.compteCourantId(JETON)).thenReturn(5L);
		when(comptes.verrouiller(5L)).thenReturn(Optional.of(compte));
		when(beneficiaire.getId()).thenReturn(8L);
		when(beneficiaire.getIban()).thenReturn(IBAN_BENEFICIAIRE);
		when(beneficiaire.getNomVerifie()).thenReturn("Test Cliente-B");
		when(beneficiaire.getLibelle()).thenReturn("Papa");
		when(beneficiaire.getAjouteLe()).thenReturn(MAINTENANT.minusDays(2));
		when(beneficiaires.actif(CLIENTE, 8L)).thenReturn(beneficiaire);
		when(codes.envoyer(any(), any())).thenReturn(CodesSms.hacher(CODE));
		when(virements.save(any())).thenAnswer(appel -> {
			Virement virement = appel.getArgument(0);
			ReflectionTestUtils.setField(virement, "id", 21L);
			return virement;
		});
	}

	private static void refus(HttpStatus statut, String message, Executable demande) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, demande);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	private Virement virementEnAttente(long montant, OffsetDateTime envoyeLe, int codesFaux) {
		Virement virement = new Virement(5L, beneficiaire, montant, "Loyer", CodesSms.hacher(CODE), envoyeLe);
		ReflectionTestUtils.setField(virement, "id", 21L);
		ReflectionTestUtils.setField(virement, "codesFaux", codesFaux);
		when(virements.verrouiller(21L, 5L)).thenReturn(Optional.of(virement));
		return virement;
	}

	@Test
	void critere1_2et9_uneSaisieValideDonneUnRecapitulatifEtEnvoieUnCodeSansRienDebiter() {
		Recapitulatif recap = service.preparer(JETON, 8L, "12,50", " Loyer ");

		assertEquals(new Recapitulatif(21L, "Papa", "Test Cliente-B", "•••• 0202", 1250, "Loyer",
				VirementService.CODE_ENVOYE), recap);
		ArgumentCaptor<Virement> cree = ArgumentCaptor.forClass(Virement.class);
		verify(virements).save(cree.capture());
		assertEquals(Virement.EN_ATTENTE_SIGNATURE, cree.getValue().getStatut());
		assertEquals(IBAN_BENEFICIAIRE, cree.getValue().getIbanBeneficiaire());
		assertEquals("Test Cliente-B", cree.getValue().getNomBeneficiaire());
		assertEquals(100_000L, compte.getSoldeCentimes());
		verify(mouvements, never()).save(any());
	}

	@Test
	void leSmsIndiqueLeMontantEtLeCodeEtUnLibelleVideDevientVirement() {
		Recapitulatif recap = service.preparer(JETON, 8L, "1000", null);

		assertEquals(VirementService.LIBELLE_PAR_DEFAUT, recap.libelle());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<UnaryOperator<String>> texte = ArgumentCaptor.forClass(UnaryOperator.class);
		verify(codes).envoyer(any(), texte.capture());
		assertThat(texte.getValue().apply("654321")).contains("654321").contains("1000,00 €");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "0", "0,00", "-5", "12,505", "abc", "", "12.5.1" })
	void critere3_unMontantNulNegatifOuAvecTroisDecimalesEstInvalide(String montant) {
		refus(HttpStatus.BAD_REQUEST, "Montant invalide", () -> service.preparer(JETON, 8L, montant, "Loyer"));
	}

	@Test
	void lesMontantsSontConvertisEnCentimes() {
		assertEquals(1250L, VirementService.enCentimes("12,5"));
		assertEquals(100_050L, VirementService.enCentimes("1 000.50"));
		assertEquals(1250L, VirementService.enCentimes("12,500"));
		assertNull(VirementService.enCentimes("99999999999999"));
		assertEquals("12,05 €", VirementService.euros(1205));
	}

	@Test
	void critere4_unMontantSuperieurAuSoldeEstRefuseSansVirement() {
		refus(HttpStatus.UNPROCESSABLE_CONTENT, "Solde insuffisant", () -> service.preparer(JETON, 8L, "1000,01", "Loyer"));
		verify(virements, never()).save(any());
		assertEquals(100_000L, compte.getSoldeCentimes());
	}

	@Test
	void critere5_unMontantSuperieurAuPlafondEstRefuseEtSansPlafondIlPasse() {
		refus(HttpStatus.UNPROCESSABLE_CONTENT, "Montant supérieur au plafond de virement",
				() -> service(true, 50_000).preparer(JETON, 8L, "500,01", "Loyer"));
		assertEquals(50_001L, service.preparer(JETON, 8L, "500,01", "Loyer").montantCentimes());
	}

	@Test
	void critere6_unPremierVirementVersUnBeneficiaireRecentEstLimiteA500() {
		when(beneficiaire.getAjouteLe()).thenReturn(MAINTENANT.minusHours(23));

		refus(HttpStatus.UNPROCESSABLE_CONTENT, "Premier virement vers ce bénéficiaire : montant limité à 500 €",
				() -> service.preparer(JETON, 8L, "500,01", "Loyer"));
		assertEquals(50_000L, service.preparer(JETON, 8L, "500", "Loyer").montantCentimes());
	}

	@Test
	void critere6_apresUnPremierVirementExecuteLaLimiteNeSAppliquePlus() {
		when(beneficiaire.getAjouteLe()).thenReturn(MAINTENANT.minusHours(1));
		when(virements.existsByBeneficiaireIdAndStatut(8L, Virement.EXECUTE)).thenReturn(true);

		assertEquals(60_000L, service.preparer(JETON, 8L, "600", "Loyer").montantCentimes());
	}

	@Test
	void critere7_unLibelleDePlusDe140CaracteresEstRefuse() {
		refus(HttpStatus.BAD_REQUEST, "Libellé trop long", () -> service.preparer(JETON, 8L, "10", "x".repeat(141)));
	}

	@Test
	void critere8_unCompteClotureEstRefuse() {
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);

		refus(HttpStatus.CONFLICT, "Compte clôturé", () -> service.preparer(JETON, 8L, "10", "Loyer"));
	}

	@Test
	void critere10_leBonCodeExecuteLeVirementDebiteEtEnregistreUnMouvementDuJour() {
		Virement virement = virementEnAttente(3000, MAINTENANT.minusMinutes(4), 0);

		Execution execution = service.signer(JETON, 21L, CODE);

		assertEquals(new Execution(VirementService.VIREMENT_EXECUTE, 97_000L), execution);
		assertEquals(Virement.EXECUTE, virement.getStatut());
		assertEquals(MAINTENANT, virement.getExecuteLe());
		ArgumentCaptor<Mouvement> mouvement = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements).save(mouvement.capture());
		assertThat(mouvement.getValue().isSortant()).isTrue();
		assertThat(mouvement.getValue().isSimule()).isFalse();
		assertEquals(3000L, mouvement.getValue().getMontantCentimes());
		assertEquals(MAINTENANT, mouvement.getValue().getEffectueLe());
		assertEquals(21L, mouvement.getValue().getVirementId());
		assertEquals("Test Cliente-B", mouvement.getValue().getBeneficiaire());
	}

	@Test
	void critere11_unCodeIncorrectNeDebiteRienEtLeTroisiemeAnnuleLeVirement() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 1);

		refus(HttpStatus.BAD_REQUEST, "Code incorrect", () -> service.signer(JETON, 21L, "000000"));
		DemandeRefusee troisieme = assertThrows(DemandeRefusee.class, () -> service.signer(JETON, 21L, "000000"));

		assertEquals(VirementService.VIREMENT_ANNULE, troisieme.getMessage());
		assertThat(troisieme.isTermine()).isTrue();
		assertEquals(Virement.ANNULE, virement.getStatut());
		assertEquals(100_000L, compte.getSoldeCentimes());
		verify(mouvements, never()).save(any());
	}

	@Test
	void critere12_unCodeExpireOuDejaUtiliseNeDebiteRien() {
		Virement virement = virementEnAttente(3000, MAINTENANT.minusMinutes(6), 0);

		refus(HttpStatus.GONE, "Code expiré", () -> service.signer(JETON, 21L, CODE));
		assertEquals(Virement.ANNULE, virement.getStatut());
		refus(HttpStatus.GONE, "Code expiré", () -> service.signer(JETON, 21L, CODE));
		assertEquals(100_000L, compte.getSoldeCentimes());
	}

	@Test
	void critere13_unSoldeDevenuInsuffisantALaSignatureAnnuleLeVirement() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 0);
		ReflectionTestUtils.setField(compte, "soldeCentimes", 2000L);

		refus(HttpStatus.CONFLICT, "Solde insuffisant", () -> service.signer(JETON, 21L, CODE));

		assertEquals(Virement.ANNULE, virement.getStatut());
		assertEquals(2000L, compte.getSoldeCentimes());
	}

	@Test
	void unCompteClotureALaSignatureAnnuleLeVirement() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 0);
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);

		refus(HttpStatus.CONFLICT, "Compte clôturé", () -> service.signer(JETON, 21L, CODE));
		assertEquals(Virement.ANNULE, virement.getStatut());
	}

	// CU-250 : virement interne, le bénéficiaire a un compte dans la banque.

	private Compte compteInterne(Long id, String statut) {
		Compte interne = new Compte(4L, UUID.randomUUID(), Iban.de("99000000002"), 9L, MAINTENANT.minusDays(30));
		ReflectionTestUtils.setField(interne, "id", id);
		ReflectionTestUtils.setField(interne, "soldeCentimes", 1_000L);
		ReflectionTestUtils.setField(interne, "statut", statut);
		when(comptes.idParIban(IBAN_BENEFICIAIRE)).thenReturn(Optional.of(id));
		when(comptes.verrouiller(id)).thenReturn(Optional.of(interne));
		return interne;
	}

	private void emettrice(String prenoms, String nom) {
		Dossier dossier = mock(Dossier.class);
		when(dossier.getPrenoms()).thenReturn(prenoms);
		when(dossier.getNomNaissance()).thenReturn(nom);
		when(dossiers.findById(3L)).thenReturn(Optional.of(dossier));
	}

	private Mouvement[] mouvementsEnregistres(int nombre) {
		ArgumentCaptor<Mouvement> enregistres = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements, times(nombre)).save(enregistres.capture());
		return enregistres.getAllValues().toArray(Mouvement[]::new);
	}

	@Test
	void cu250_unVirementInterneDebiteLEmettriceEtCrediteLeBeneficiaireAvecUnVirementRecu() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 0);
		Compte interne = compteInterne(6L, Compte.ACTIF);
		emettrice("Test", "Cliente-A");

		Execution execution = service.signer(JETON, 21L, CODE);

		assertEquals(new Execution(VirementService.VIREMENT_EXECUTE, 97_000L), execution);
		assertEquals(Virement.EXECUTE, virement.getStatut());
		assertEquals(4_000L, interne.getSoldeCentimes());
		Mouvement[] enregistres = mouvementsEnregistres(2);
		assertThat(enregistres[0].isSortant()).isTrue();
		assertEquals(5L, enregistres[0].getCompteId());
		Mouvement recu = enregistres[1];
		assertEquals(Mouvement.VIREMENT_ENTRANT, recu.getType());
		assertEquals(6L, recu.getCompteId());
		assertEquals(3000L, recu.getMontantCentimes());
		assertEquals("Loyer", recu.getLibelle());
		assertEquals("Test Cliente-A", recu.getEmetteur());
		assertThat(recu.isSimule()).isFalse();
		assertEquals(MAINTENANT, recu.getEffectueLe());
	}

	@Test
	void cu250_unBeneficiaireClotureRefuseLeVirementAC04EtRecrediteLEmettrice() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 0);
		Compte interne = compteInterne(6L, Compte.CLOTURE);

		Execution execution = service.signer(JETON, 21L, CODE);

		assertEquals(new Execution(VirementService.REFUSE_BENEFICIAIRE_CLOTURE, 100_000L), execution);
		assertEquals(Virement.REFUSE, virement.getStatut());
		assertEquals(VirementService.BENEFICIAIRE_CLOTURE, virement.getMotifRefus());
		assertEquals(1_000L, interne.getSoldeCentimes());
		Mouvement[] enregistres = mouvementsEnregistres(2);
		assertThat(enregistres[1].isRecredit()).isTrue();
		assertEquals(5L, enregistres[1].getCompteId());
	}

	@Test
	void cu250_lesComptesSontVerrouillesDansLOrdreDeLeursIdentifiants() {
		virementEnAttente(3000, MAINTENANT, 0);
		compteInterne(2L, Compte.ACTIF);
		emettrice("Test", "Cliente-A");

		service.signer(JETON, 21L, CODE);

		InOrder ordre = inOrder(comptes);
		ordre.verify(comptes).verrouiller(2L);
		ordre.verify(comptes).verrouiller(5L);
	}

	@Test
	void cu250_sansDossierLEmetteurEstGeneriqueEtUnNomTropLongEstCoupe() {
		virementEnAttente(1000, MAINTENANT, 0);
		compteInterne(6L, Compte.ACTIF);
		service.signer(JETON, 21L, CODE);

		emettrice("A".repeat(60), "B".repeat(20));
		virementEnAttente(1000, MAINTENANT, 0);
		service.signer(JETON, 21L, CODE);

		Mouvement[] enregistres = mouvementsEnregistres(4);
		assertEquals("Cliente de la banque", enregistres[1].getEmetteur());
		assertEquals(VirementEntrant.EMETTEUR_MAX, enregistres[3].getEmetteur().length());
	}

	@Test
	void cu250_unVirementVersSonPropreCompteDebiteEtRecrediteLeMemeCompte() {
		virementEnAttente(3000, MAINTENANT, 0);
		when(comptes.idParIban(IBAN_BENEFICIAIRE)).thenReturn(Optional.of(5L));
		emettrice("Test", "Cliente-A");

		Execution execution = service.signer(JETON, 21L, CODE);

		assertEquals(new Execution(VirementService.VIREMENT_EXECUTE, 100_000L), execution);
		assertEquals(100_000L, compte.getSoldeCentimes());
		verify(comptes, times(1)).verrouiller(5L);
		assertEquals(Mouvement.VIREMENT_ENTRANT, mouvementsEnregistres(2)[1].getType());
	}

	@Test
	void critere14_unRefusDeLaBanqueRecrediteEtEnregistreLeMotif() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 0);
		virement.executer(MAINTENANT);
		ReflectionTestUtils.setField(compte, "soldeCentimes", 97_000L);
		when(virements.verrouiller(21L)).thenReturn(Optional.of(virement));

		Execution execution = service.refuserParBanque(21L, " Compte du bénéficiaire clôturé ");

		assertEquals(100_000L, execution.soldeCentimes());
		assertEquals(Virement.REFUSE, virement.getStatut());
		assertEquals("Compte du bénéficiaire clôturé", virement.getMotifRefus());
		ArgumentCaptor<Mouvement> mouvement = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements).save(mouvement.capture());
		assertThat(mouvement.getValue().isRecredit()).isTrue();
		assertEquals(3000L, mouvement.getValue().getMontantCentimes());
		assertEquals(21L, mouvement.getValue().getVirementId());
	}

	@Test
	void critere14_sansMotifLeMotifParDefautEstUtiliseEtUnVirementNonExecuteNePeutEtreRefuse() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 0);
		when(virements.verrouiller(21L)).thenReturn(Optional.of(virement));

		refus(HttpStatus.CONFLICT, VirementService.NON_EXECUTE, () -> service.refuserParBanque(21L, null));

		virement.executer(MAINTENANT);
		service.refuserParBanque(21L, null);
		assertEquals(VirementService.MOTIF_PAR_DEFAUT, virement.getMotifRefus());
	}

	@Test
	void unRefusSurUnCompteClotureEstRefuse() {
		Virement virement = virementEnAttente(3000, MAINTENANT, 0);
		virement.executer(MAINTENANT);
		when(virements.verrouiller(21L)).thenReturn(Optional.of(virement));
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);

		refus(HttpStatus.CONFLICT, "Compte clôturé", () -> service.refuserParBanque(21L, "x"));
	}

	@Test
	void critere16_unBeneficiaireSupprimeNePeutPlusRecevoirDeVirement() {
		when(beneficiaires.actif(CLIENTE, 9L))
				.thenThrow(new DemandeRefusee(HttpStatus.NOT_FOUND, "Bénéficiaire introuvable"));

		refus(HttpStatus.NOT_FOUND, "Bénéficiaire introuvable", () -> service.preparer(JETON, 9L, "10", "Loyer"));
	}

	@Test
	void critere17_enProductionUnIbanDeTestEstRefuse() {
		refus(HttpStatus.UNPROCESSABLE_CONTENT, "Ce compte n’a pas pu être trouvé. Vérifiez l’IBAN.",
				() -> service(false, 0).preparer(JETON, 8L, "10", "Loyer"));
		verify(codes, never()).envoyer(any(), any());
	}

}
