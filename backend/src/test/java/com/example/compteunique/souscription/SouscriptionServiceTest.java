package com.example.compteunique.souscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.beneficiaire.CodesSms;
import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.decouverte.DocumentsProduits;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.gestion.ModeDeGestion;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilClientService.Statut;
import com.example.compteunique.profil.ProfilKyc;
import com.example.compteunique.profil.Reponses;
import com.example.compteunique.souscription.SouscriptionService.Beneficiaire;
import com.example.compteunique.souscription.SouscriptionService.DemandeVersement;
import com.example.compteunique.souscription.SouscriptionService.Etape;
import com.example.compteunique.souscription.SouscriptionService.Etat;

// CU-312 (PER-04) et CU-330 (AV-01) : critères d'acceptation de la souscription et du premier versement.
class SouscriptionServiceTest {

	private static final ZonedDateTime DEBUT = ZonedDateTime.of(2026, 11, 27, 10, 0, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final String JETON = "jeton";
	private static final String BON_CODE = "123456";
	private static final String MAUVAIS_CODE = "654321";
	private static final BigDecimal CINQ_CENTS = new BigDecimal("500");
	private static final Produit PER = Produit.PER;
	private static final Produit AV = Produit.AV;

	private final CompteService comptes = mock(CompteService.class);
	private final CompteRepository comptesVerrouilles = mock(CompteRepository.class);
	private final MouvementRepository mouvements = mock(MouvementRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final ProfilClientService profils = mock(ProfilClientService.class);
	private final ModeDeGestion gestion = mock(ModeDeGestion.class);
	private final CodesSms codes = mock(CodesSms.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final SouscriptionRepository souscriptions = mock(SouscriptionRepository.class);
	private final BeneficiaireClauseRepository clauses = mock(BeneficiaireClauseRepository.class);
	private final DocumentsProduits documents = mock(DocumentsProduits.class);
	private final Compte compte = mock(Compte.class);
	private final AtomicReference<ZonedDateTime> maintenant = new AtomicReference<>(DEBUT);
	private final List<Souscription> base = new ArrayList<>();
	private final List<BeneficiaireClause> baseClauses = new ArrayList<>();

	private final SouscriptionService service = new SouscriptionService(comptes, comptesVerrouilles, mouvements,
			dossiers, profils, gestion, codes, dateMetier, souscriptions, clauses, documents);

	@BeforeEach
	void cliente() {
		when(compte.getCustomerId()).thenReturn(CLIENTE);
		when(compte.getId()).thenReturn(7L);
		when(compte.getDossierId()).thenReturn(3L);
		when(compte.isActif()).thenReturn(true);
		when(compte.getSoldeCentimes()).thenReturn(100_000L);
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(comptesVerrouilles.verrouiller(7L)).thenReturn(Optional.of(compte));
		Dossier dossier = mock(Dossier.class);
		when(dossier.getDateNaissance()).thenReturn(LocalDate.of(1980, 11, 28));
		when(dossiers.findById(3L)).thenReturn(Optional.of(dossier));
		when(dateMetier.maintenant()).thenAnswer(appel -> maintenant.get());
		when(gestion.numeroContrat(any(), any())).thenReturn(Optional.empty());
		when(codes.envoyer(eq(compte), any())).thenAnswer(appel -> CodesSms.hacher(BON_CODE));
		// Comme la base : refuse une souscription sans montant ni origine (CU-521), attribue l'identifiant.
		when(souscriptions.saveAndFlush(any())).thenAnswer(appel -> {
			Souscription s = appel.getArgument(0);
			assertThat(s.getMontant()).as("montant obligatoire en base").isNotNull();
			assertThat(s.getOrigineFonds()).as("origine obligatoire en base").isNotNull();
			if (s.getId() == null) {
				ReflectionTestUtils.setField(s, "id", (long) base.size() + 1);
				base.add(s);
			}
			return s;
		});
		when(souscriptions.findByCustomerIdAndProduit(eq(CLIENTE), any()))
				.thenAnswer(appel -> souscription(appel.getArgument(1)));
		when(souscriptions.findWithLockByCustomerIdAndProduit(eq(CLIENTE), any()))
				.thenAnswer(appel -> souscription(appel.getArgument(1)));
		when(clauses.save(any())).thenAnswer(appel -> {
			baseClauses.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		doAnswer(appel -> baseClauses.removeIf(b -> ReflectionTestUtils.getField(b, "souscriptionId")
				.equals(appel.getArgument(0)))).when(clauses).supprimer(anyLong());
		when(clauses.findBySouscriptionIdOrderByRang(anyLong())).thenAnswer(appel -> baseClauses.stream()
				.filter(b -> ReflectionTestUtils.getField(b, "souscriptionId").equals(appel.getArgument(0))).toList());
		profilValide(true);
		when(documents.peutContinuer(eq(CLIENTE), any())).thenReturn(true);
	}

	private Optional<Souscription> souscription(Produit produit) {
		return base.stream().filter(s -> s.getProduit() == produit).findFirst();
	}

	private void profilValide(boolean valide) {
		ProfilKyc kyc = mock(ProfilKyc.class);
		Reponses reponses = mock(Reponses.class);
		when(reponses.anneeDepartRetraite()).thenReturn(2041);
		when(kyc.getReponses()).thenReturn(reponses);
		when(kyc.getProfilInvestisseur()).thenReturn("EQUILIBRE");
		when(profils.profilValide(CLIENTE)).thenReturn(valide ? Optional.of(kyc) : Optional.empty());
		when(profils.statut(CLIENTE)).thenReturn(valide ? Statut.VALIDE : Statut.A_REVALIDER);
	}

	private void avancer(int minutes) {
		maintenant.set(maintenant.get().plusMinutes(minutes));
	}

	private static DemandeVersement versement(String montant, String origine, String precision,
			Beneficiaire... beneficiaires) {
		return new DemandeVersement(montant == null ? null : new BigDecimal(montant), origine, precision,
				List.of(beneficiaires));
	}

	private static Beneficiaire beneficiaire(String nom, String part) {
		return new Beneficiaire(nom, "Léa", LocalDate.of(2010, 5, 1), new BigDecimal(part));
	}

	private Etat verser(Produit produit, DemandeVersement demande) {
		return service.choisirVersement(JETON, produit, demande);
	}

	private Etat souscrire(Produit produit, DemandeVersement demande) {
		verser(produit, demande);
		service.envoyerCode(JETON, produit);
		return service.signer(JETON, produit, BON_CODE);
	}

	private static DemandeRefusee refus(Runnable action) {
		try {
			action.run();
		} catch (DemandeRefusee refus) {
			return refus;
		}
		throw new AssertionError("refus attendu");
	}

	@Test
	@DisplayName("PER-02 / AV-02 : documents pas lus (adresse /souscrire tapée) : l'état le signale, le versement et la "
			+ "signature sont refusés, rien n'est débité ni créé")
	void documentsNonLus() {
		Etat saisi = verser(AV, versement("500", "REVENUS", null));
		assertThat(saisi.documentsLus()).isTrue();
		service.envoyerCode(JETON, AV);
		when(documents.peutContinuer(CLIENTE, DocumentsProduits.Produit.AV)).thenReturn(false);

		assertThat(service.etat(JETON, AV).documentsLus()).isFalse();
		assertThat(refus(() -> verser(AV, versement("500", "REVENUS", null))).getMessage())
				.isEqualTo(SouscriptionService.DOCUMENTS_REQUIS);
		assertThat(refus(() -> service.signer(JETON, AV, BON_CODE)).getMessage())
				.isEqualTo(SouscriptionService.DOCUMENTS_REQUIS);
		verify(compte, never()).debiter(anyLong(), any());
		verify(gestion, never()).initialiser(any(), any(), any(), anyInt(), any());
	}

	@Test
	@DisplayName("PER critère 1 (et CU-521) : profil validé, versement de 500 € enregistré, signature par code SMS : "
			+ "contrat créé, compte courant débité, versement placé sur le compartiment 1")
	void souscriptionPer() {
		Etat saisi = verser(PER, versement("500", "REVENUS", null));
		assertThat(saisi.etape()).isEqualTo(Etape.SIGNATURE);
		assertThat(saisi.montant()).isEqualByComparingTo("500");
		Etat envoye = service.envoyerCode(JETON, PER);
		assertThat(envoye.codeEnvoyeLe()).isNotNull();

		Etat signe = service.signer(JETON, PER, BON_CODE);

		assertThat(signe.etape()).isEqualTo(Etape.SIGNEE);
		assertThat(signe.numeroContrat()).startsWith("PER-");
		verify(compte).debiter(eq(50_000L), any());
		ArgumentCaptor<Mouvement> mouvement = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements).save(mouvement.capture());
		assertThat(mouvement.getValue().isSortant()).isTrue();
		assertThat(mouvement.getValue().getBeneficiaire()).isEqualTo("PER Hi-Tech Bank " + signe.numeroContrat());
		verify(gestion).initialiser(signe.numeroContrat(), CLIENTE, PER, 2041, "EQUILIBRE");
		verify(gestion).verser(eq(JETON), eq(PER), any(), eq(new BigDecimal("500.00")));
		Souscription enBase = base.get(0);
		assertThat(enBase.getCompartiment()).isEqualTo((short) 1);
		assertThat(enBase.getAgeTitulaire()).isNull();
		assertThat(enBase.getOrigineFonds()).isEqualTo("REVENUS");
		assertThat(refus(() -> verser(PER, versement("500", "REVENUS", null))).getMessage())
				.isEqualTo("Vous avez déjà un PER.");
	}

	@Test
	@DisplayName("AV critère 1 : signature par code SMS et versement de 500 € : contrat créé, versement enregistré avec "
			+ "l'âge de la titulaire, sans compartiment")
	void souscriptionAssuranceVie() {
		Etat signe = souscrire(AV, versement("500", "EPARGNE", null, beneficiaire("Martin", "60"),
				beneficiaire("Durand", "40")));

		assertThat(signe.numeroContrat()).startsWith("AV-");
		Souscription enBase = base.get(0);
		// Née le 28/11/1980, signature le 27/11/2026 : 45 ans (anniversaire le lendemain).
		assertThat(enBase.getAgeTitulaire()).isEqualTo((short) 45);
		assertThat(enBase.getCompartiment()).isNull();
		verify(gestion).initialiser(signe.numeroContrat(), CLIENTE, AV, 2041, "EQUILIBRE");
		verify(gestion).verser(eq(JETON), eq(AV), any(), eq(new BigDecimal("500.00")));
		ArgumentCaptor<Mouvement> mouvement = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements).save(mouvement.capture());
		assertThat(mouvement.getValue().getBeneficiaire()).startsWith("Assurance vie Hi-Tech Bank AV-");
		assertThat(signe.beneficiaires()).extracting(Beneficiaire::nom).containsExactly("Martin", "Durand");
		assertThat(baseClauses).allSatisfy(b -> assertThat(b.getVersion()).isEqualTo(1));
	}

	@Test
	@DisplayName("AV : quotes-parts qui ne totalisent pas 100 % : « Les quotes-parts doivent totaliser 100 % » ; ligne "
			+ "incomplète refusée")
	void quotesParts() {
		assertThat(refus(() -> verser(AV, versement("500", "REVENUS", null, beneficiaire("Martin", "60"),
				beneficiaire("Durand", "30")))).getMessage()).isEqualTo("Les quotes-parts doivent totaliser 100 %");
		assertThat(refus(() -> verser(AV, versement("500", "REVENUS", null, beneficiaire(" ", "100")))).getMessage())
				.isEqualTo(SouscriptionService.BENEFICIAIRE_INCOMPLET);
		assertThat(refus(() -> verser(AV, versement("500", "REVENUS", null,
				new Beneficiaire("Martin", "Léa", null, new BigDecimal("100"))))).getMessage())
				.isEqualTo(SouscriptionService.BENEFICIAIRE_INCOMPLET);
		assertThat(refus(() -> verser(AV, versement("500", "REVENUS", null, beneficiaire("Martin", "0")))).getMessage())
				.isEqualTo(SouscriptionService.BENEFICIAIRE_INCOMPLET);
		Beneficiaire[] onze = new Beneficiaire[11];
		java.util.Arrays.fill(onze, beneficiaire("Martin", "1"));
		assertThat(refus(() -> verser(AV, versement("500", "REVENUS", null, onze))).getMessage())
				.isEqualTo(SouscriptionService.TROP_DE_BENEFICIAIRES);
		assertThat(base).isEmpty();
	}

	@Test
	@DisplayName("AV : clause laissée vide : contrat créé sans bénéficiaire (le capital entrera dans la succession) ; "
			+ "une nouvelle saisie remplace la clause")
	void clauseVide() {
		verser(AV, versement("500", "REVENUS", null, beneficiaire("Martin", "100")));
		assertThat(baseClauses).hasSize(1);

		Etat etat = verser(AV, versement("500", "REVENUS", null));

		assertThat(etat.beneficiaires()).isEmpty();
		service.envoyerCode(JETON, AV);
		assertThat(service.signer(JETON, AV, BON_CODE).etape()).isEqualTo(Etape.SIGNEE);
		assertThat(baseClauses).isEmpty();
	}

	@Test
	@DisplayName("PER : les bénéficiaires envoyés sont ignorés (clause propre à l'assurance vie)")
	void perSansClause() {
		verser(PER, versement("500", "REVENUS", null, beneficiaire("Martin", "50")));
		assertThat(baseClauses).isEmpty();
	}

	@Test
	@DisplayName("Critère profil (PER 2, AV 9 et 10) : profil à compléter ou à revalider : souscription refusée, rien "
			+ "d'enregistré ; profil expiré avant la signature : aucun versement, souscription en cours conservée")
	void profilNonValide() {
		verser(AV, versement("500", "REVENUS", null));
		service.envoyerCode(JETON, AV);
		profilValide(false);

		assertThat(refus(() -> service.signer(JETON, AV, BON_CODE)).getMessage())
				.isEqualTo(SouscriptionService.PROFIL_REQUIS);
		DemandeRefusee refus = refus(() -> verser(PER, versement("500", "REVENUS", null)));
		assertThat(refus.getStatut()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(refus.getMessage()).isEqualTo(SouscriptionService.PROFIL_REQUIS);
		verify(compte, never()).debiter(anyLong(), any());
		verify(gestion, never()).initialiser(any(), any(), any(), any(), any());
		assertThat(service.etat(JETON, AV).etape()).isEqualTo(Etape.SIGNATURE);
		assertThat(service.etat(JETON, AV).profil()).isEqualTo(Statut.A_REVALIDER);
	}

	@Test
	@DisplayName("Origine des fonds absente, ou « autre » sans précision : la cliente ne peut pas continuer")
	void origineDesFonds() {
		assertThat(refus(() -> verser(PER, versement("500", null, null))).getMessage())
				.isEqualTo(SouscriptionService.ORIGINE_REQUISE);
		assertThat(refus(() -> verser(PER, versement("500", "LOTO", null))).getMessage())
				.isEqualTo(SouscriptionService.ORIGINE_REQUISE);
		assertThat(refus(() -> verser(AV, versement("500", "AUTRE", "  "))).getMessage())
				.isEqualTo(SouscriptionService.PRECISION_REQUISE);

		Etat etat = verser(PER, versement("500", "AUTRE", " Prime de départ "));

		assertThat(etat.originePrecision()).isEqualTo("Prime de départ");
	}

	@Test
	@DisplayName("Versement sous 100 € : message propre à chaque produit ; montant invalide ou supérieur au solde refusé")
	void montant() {
		assertThat(refus(() -> verser(PER, versement("99.99", "REVENUS", null))).getMessage())
				.isEqualTo("Le premier versement minimum est de 100 €");
		assertThat(refus(() -> verser(AV, versement("99.99", "REVENUS", null))).getMessage())
				.isEqualTo("Le versement initial minimum est de 100 €");
		assertThat(refus(() -> verser(PER, versement("100.001", "REVENUS", null))).getMessage())
				.isEqualTo(SouscriptionService.MONTANT_INVALIDE);
		assertThat(refus(() -> verser(PER, versement(null, "REVENUS", null))).getMessage())
				.isEqualTo(SouscriptionService.MONTANT_INVALIDE);
		assertThat(refus(() -> verser(PER, versement("1000.01", "REVENUS", null))).getMessage())
				.isEqualTo(SouscriptionService.SOLDE_INSUFFISANT);
		assertThat(verser(PER, versement("100", "REVENUS", null)).montant()).isEqualByComparingTo("100");
	}

	@Test
	@DisplayName("Code erroné : signature refusée, un nouveau code peut être demandé puis signé")
	void codeErrone() {
		verser(PER, versement("500", "REVENUS", null));
		service.envoyerCode(JETON, PER);

		assertThat(refus(() -> service.signer(JETON, PER, MAUVAIS_CODE)).getMessage())
				.isEqualTo(SouscriptionService.CODE_INCORRECT);

		assertThat(base.get(0).getCodesFaux()).isEqualTo(1);
		service.envoyerCode(JETON, PER);
		assertThat(service.signer(JETON, PER, BON_CODE).etape()).isEqualTo(Etape.SIGNEE);
	}

	@Test
	@DisplayName("Code saisi après 5 minutes : signature refusée, un nouveau code peut être demandé")
	void codeExpire() {
		verser(AV, versement("500", "REVENUS", null));
		service.envoyerCode(JETON, AV);
		avancer(6);

		DemandeRefusee refus = refus(() -> service.signer(JETON, AV, BON_CODE));

		assertThat(refus.getStatut()).isEqualTo(HttpStatus.GONE);
		assertThat(refus(() -> service.signer(JETON, AV, BON_CODE)).getMessage())
				.isEqualTo(SouscriptionService.CODE_REQUIS);
		service.envoyerCode(JETON, AV);
		assertThat(service.signer(JETON, AV, BON_CODE).etape()).isEqualTo(Etape.SIGNEE);
	}

	@Test
	@DisplayName("Trois codes erronés : signature bloquée pendant 30 minutes, puis de nouveau possible")
	void blocage() {
		verser(PER, versement("500", "REVENUS", null));
		service.envoyerCode(JETON, PER);
		refus(() -> service.signer(JETON, PER, MAUVAIS_CODE));
		refus(() -> service.signer(JETON, PER, MAUVAIS_CODE));

		DemandeRefusee troisieme = refus(() -> service.signer(JETON, PER, MAUVAIS_CODE));

		assertThat(troisieme.getStatut()).isEqualTo(HttpStatus.LOCKED);
		assertThat(troisieme.getMessage()).isEqualTo("Trois codes erronés : la signature est bloquée jusqu'à 10 h 30.");
		avancer(29);
		assertThat(refus(() -> service.envoyerCode(JETON, PER)).getStatut()).isEqualTo(HttpStatus.LOCKED);
		assertThat(refus(() -> service.signer(JETON, PER, BON_CODE)).getStatut()).isEqualTo(HttpStatus.LOCKED);
		avancer(1);
		service.envoyerCode(JETON, PER);
		assertThat(base.get(0).getCodesFaux()).isZero();
		assertThat(service.signer(JETON, PER, BON_CODE).etape()).isEqualTo(Etape.SIGNEE);
	}

	@Test
	@DisplayName("Un nouveau montant annule le code déjà envoyé ; sans versement, pas de code")
	void nouveauMontant() {
		assertThat(refus(() -> service.envoyerCode(JETON, PER)).getMessage())
				.isEqualTo(SouscriptionService.VERSEMENT_REQUIS);
		verser(PER, versement("500", "REVENUS", null));
		service.envoyerCode(JETON, PER);

		verser(PER, versement("300", "REVENUS", null));

		assertThat(refus(() -> service.signer(JETON, PER, BON_CODE)).getMessage())
				.isEqualTo(SouscriptionService.CODE_REQUIS);
		assertThat(base).hasSize(1);
	}

	@Test
	@DisplayName("Solde devenu insuffisant à la signature : refus, rien n'est débité")
	void soldeInsuffisantALaSignature() {
		verser(PER, versement("500", "REVENUS", null));
		service.envoyerCode(JETON, PER);
		when(compte.getSoldeCentimes()).thenReturn(10_000L);

		assertThat(refus(() -> service.signer(JETON, PER, BON_CODE)).getMessage())
				.isEqualTo(SouscriptionService.SOLDE_INSUFFISANT);
		verify(compte, never()).debiter(anyLong(), any());
	}

	@Test
	@DisplayName("Cliente qui a déjà un contrat (contrat de test) : état « signé » avec son numéro, souscription refusée")
	void dejaUnContrat() {
		when(gestion.numeroContrat(CLIENTE, AV)).thenReturn(Optional.of("AV-TEST-1"));

		Etat etat = service.etat(JETON, AV);

		assertThat(etat.etape()).isEqualTo(Etape.SIGNEE);
		assertThat(etat.numeroContrat()).isEqualTo("AV-TEST-1");
		assertThat(refus(() -> verser(AV, versement("500", "REVENUS", null))).getMessage())
				.isEqualTo("Vous avez déjà une assurance vie.");
		verify(codes, never()).envoyer(any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("Le SMS nomme le contrat à signer et la validité de 5 minutes")
	void texteDuSms() {
		verser(AV, versement("500", "REVENUS", null));
		ArgumentCaptor<UnaryOperator<String>> texte = ArgumentCaptor.forClass(UnaryOperator.class);
		service.envoyerCode(JETON, AV);
		verify(codes).envoyer(eq(compte), texte.capture());
		assertThat(texte.getValue().apply("111111")).contains("assurance vie", "111111", "5 minutes");
	}

}
