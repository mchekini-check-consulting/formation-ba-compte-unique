package com.example.compteunique.souscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.CodesSms;
import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.gestion.ModeDeGestion;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilClientService.Statut;
import com.example.compteunique.profil.ProfilKyc;
import com.example.compteunique.profil.Reponses;
import com.example.compteunique.souscription.SouscriptionPer.Etape;
import com.example.compteunique.souscription.SouscriptionPer.Etat;

// CU-312 (PER-04) : critères d'acceptation de la souscription du PER et du premier versement.
class SouscriptionPerTest {

	private static final ZonedDateTime DEBUT = ZonedDateTime.of(2026, 11, 27, 10, 0, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final String JETON = "jeton";
	private static final String BON_CODE = "123456";
	private static final String MAUVAIS_CODE = "654321";
	private static final BigDecimal CINQ_CENTS = new BigDecimal("500");

	private final CompteService comptes = mock(CompteService.class);
	private final CompteRepository comptesVerrouilles = mock(CompteRepository.class);
	private final MouvementRepository mouvements = mock(MouvementRepository.class);
	private final ProfilClientService profils = mock(ProfilClientService.class);
	private final ModeDeGestion gestion = mock(ModeDeGestion.class);
	private final CodesSms codes = mock(CodesSms.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final SouscriptionRepository souscriptions = mock(SouscriptionRepository.class);
	private final Compte compte = mock(Compte.class);
	private final AtomicReference<ZonedDateTime> maintenant = new AtomicReference<>(DEBUT);
	private final AtomicReference<Souscription> base = new AtomicReference<>();

	private final SouscriptionPer service = new SouscriptionPer(comptes, comptesVerrouilles, mouvements, profils,
			gestion, codes, dateMetier, souscriptions);

	@BeforeEach
	void cliente() {
		when(compte.getCustomerId()).thenReturn(CLIENTE);
		when(compte.getId()).thenReturn(7L);
		when(compte.isActif()).thenReturn(true);
		when(compte.getSoldeCentimes()).thenReturn(100_000L);
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(comptesVerrouilles.verrouiller(7L)).thenReturn(Optional.of(compte));
		when(dateMetier.maintenant()).thenAnswer(appel -> maintenant.get());
		when(gestion.numeroContrat(CLIENTE, Produit.PER)).thenReturn(Optional.empty());
		when(codes.envoyer(eq(compte), any())).thenAnswer(appel -> CodesSms.hacher(BON_CODE));
		when(souscriptions.save(any())).thenAnswer(appel -> {
			base.set(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(souscriptions.findByCustomerIdAndProduit(CLIENTE, Produit.PER))
				.thenAnswer(appel -> Optional.ofNullable(base.get()));
		when(souscriptions.findWithLockByCustomerIdAndProduit(CLIENTE, Produit.PER))
				.thenAnswer(appel -> Optional.ofNullable(base.get()));
		profilValide(true);
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

	private static DemandeRefusee refus(Runnable action) {
		try {
			action.run();
		} catch (DemandeRefusee refus) {
			return refus;
		}
		throw new AssertionError("refus attendu");
	}

	@Test
	@DisplayName("Critère 1 : profil validé, signature par code SMS et versement de 500 € : contrat PER créé, compte "
			+ "courant débité, versement placé et affecté au compartiment 1")
	void souscriptionComplete() {
		service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null);
		Etat envoye = service.envoyerCode(JETON);
		assertThat(envoye.etape()).isEqualTo(Etape.SIGNATURE);
		assertThat(envoye.codeEnvoyeLe()).isNotNull();

		Etat signe = service.signer(JETON, BON_CODE);

		assertThat(signe.etape()).isEqualTo(Etape.SIGNEE);
		assertThat(signe.numeroContrat()).startsWith("PER-");
		verify(compte).debiter(eq(50_000L), any());
		ArgumentCaptor<Mouvement> mouvement = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements).save(mouvement.capture());
		assertThat(mouvement.getValue().isSortant()).isTrue();
		assertThat(mouvement.getValue().getBeneficiaire()).endsWith(signe.numeroContrat());
		verify(gestion).initialiser(signe.numeroContrat(), CLIENTE, Produit.PER, 2041, "EQUILIBRE");
		verify(gestion).verser(eq(JETON), eq(Produit.PER), any(), eq(new BigDecimal("500.00")));
		assertThat(base.get().getCompartiment()).isEqualTo((short) 1);
		assertThat(base.get().getOrigineFonds()).isEqualTo("REVENUS");
		assertThat(base.get().getSigneeLe()).isNotNull();
		// Une fois signé, le parcours ne peut pas être refait.
		assertThat(refus(() -> service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null)).getMessage())
				.isEqualTo(SouscriptionPer.DEJA_SOUSCRIT);
	}

	@Test
	@DisplayName("Critère 2 : profil « à compléter » ou « à revalider » : souscription refusée (l'écran renvoie vers le "
			+ "questionnaire)")
	void profilNonValide() {
		profilValide(false);

		DemandeRefusee refus = refus(() -> service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null));

		assertThat(refus.getStatut()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(refus.getMessage()).isEqualTo(SouscriptionPer.PROFIL_REQUIS);
		assertThat(service.etat(JETON).profil()).isEqualTo(Statut.A_REVALIDER);
	}

	@Test
	@DisplayName("Profil devenu « à revalider » entre le versement et la signature : rien n'est débité ni créé")
	void profilExpireAvantSignature() {
		service.choisirVersement(JETON, CINQ_CENTS, "EPARGNE", null);
		service.envoyerCode(JETON);
		profilValide(false);

		assertThat(refus(() -> service.signer(JETON, BON_CODE)).getMessage()).isEqualTo(SouscriptionPer.PROFIL_REQUIS);
		verify(compte, never()).debiter(any(Long.class), any());
		verify(gestion, never()).initialiser(any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("Critère 4 : origine des fonds absente, ou « autre » sans précision : la cliente ne peut pas continuer")
	void origineDesFonds() {
		assertThat(refus(() -> service.choisirVersement(JETON, CINQ_CENTS, null, null)).getMessage())
				.isEqualTo(SouscriptionPer.ORIGINE_REQUISE);
		assertThat(refus(() -> service.choisirVersement(JETON, CINQ_CENTS, "LOTO", null)).getMessage())
				.isEqualTo(SouscriptionPer.ORIGINE_REQUISE);
		assertThat(refus(() -> service.choisirVersement(JETON, CINQ_CENTS, "AUTRE", "  ")).getMessage())
				.isEqualTo(SouscriptionPer.PRECISION_REQUISE);

		Etat etat = service.choisirVersement(JETON, CINQ_CENTS, "AUTRE", " Prime de départ ");

		assertThat(etat.origineFonds()).isEqualTo("AUTRE");
		assertThat(etat.originePrecision()).isEqualTo("Prime de départ");
	}

	@Test
	@DisplayName("Critère 5 : premier versement inférieur à 100 € : « Le premier versement minimum est de 100 € » ; "
			+ "montant invalide ou supérieur au solde refusé")
	void montant() {
		assertThat(refus(() -> service.choisirVersement(JETON, new BigDecimal("99.99"), "REVENUS", null))
				.getMessage()).isEqualTo("Le premier versement minimum est de 100 €");
		assertThat(refus(() -> service.choisirVersement(JETON, new BigDecimal("100.001"), "REVENUS", null))
				.getMessage()).isEqualTo(SouscriptionPer.MONTANT_INVALIDE);
		assertThat(refus(() -> service.choisirVersement(JETON, null, "REVENUS", null)).getMessage())
				.isEqualTo(SouscriptionPer.MONTANT_INVALIDE);
		assertThat(refus(() -> service.choisirVersement(JETON, new BigDecimal("1000.01"), "REVENUS", null))
				.getMessage()).isEqualTo(SouscriptionPer.SOLDE_INSUFFISANT);
		assertThat(service.choisirVersement(JETON, new BigDecimal("100"), "REVENUS", null).montant())
				.isEqualByComparingTo("100");
	}

	@Test
	@DisplayName("Critère 6 : code erroné : signature refusée, un nouveau code peut être demandé puis signé")
	void codeErrone() {
		service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null);
		service.envoyerCode(JETON);

		DemandeRefusee refus = refus(() -> service.signer(JETON, MAUVAIS_CODE));

		assertThat(refus.getMessage()).isEqualTo(SouscriptionPer.CODE_INCORRECT);
		assertThat(base.get().getCodesFaux()).isEqualTo(1);
		service.envoyerCode(JETON);
		assertThat(service.signer(JETON, BON_CODE).etape()).isEqualTo(Etape.SIGNEE);
	}

	@Test
	@DisplayName("Critère 7 : code saisi après 5 minutes : signature refusée, un nouveau code peut être demandé")
	void codeExpire() {
		service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null);
		service.envoyerCode(JETON);
		avancer(6);

		DemandeRefusee refus = refus(() -> service.signer(JETON, BON_CODE));

		assertThat(refus.getStatut()).isEqualTo(HttpStatus.GONE);
		assertThat(refus.getMessage()).isEqualTo(SouscriptionPer.CODE_EXPIRE);
		assertThat(refus(() -> service.signer(JETON, BON_CODE)).getMessage()).isEqualTo(SouscriptionPer.CODE_REQUIS);
		service.envoyerCode(JETON);
		assertThat(service.signer(JETON, BON_CODE).etape()).isEqualTo(Etape.SIGNEE);
	}

	@Test
	@DisplayName("Critère 8 : trois codes erronés : signature bloquée pendant 30 minutes, puis de nouveau possible")
	void blocage() {
		service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null);
		service.envoyerCode(JETON);
		refus(() -> service.signer(JETON, MAUVAIS_CODE));
		refus(() -> service.signer(JETON, MAUVAIS_CODE));

		DemandeRefusee troisieme = refus(() -> service.signer(JETON, MAUVAIS_CODE));

		assertThat(troisieme.getStatut()).isEqualTo(HttpStatus.LOCKED);
		assertThat(troisieme.getMessage()).isEqualTo("Trois codes erronés : la signature est bloquée jusqu'à 10 h 30.");
		avancer(29);
		assertThat(refus(() -> service.envoyerCode(JETON)).getStatut()).isEqualTo(HttpStatus.LOCKED);
		assertThat(refus(() -> service.signer(JETON, BON_CODE)).getStatut()).isEqualTo(HttpStatus.LOCKED);
		avancer(1);
		service.envoyerCode(JETON);
		assertThat(base.get().getCodesFaux()).isZero();
		assertThat(service.signer(JETON, BON_CODE).etape()).isEqualTo(Etape.SIGNEE);
	}

	@Test
	@DisplayName("Un nouveau montant annule le code déjà envoyé ; sans versement, pas de code")
	void nouveauMontant() {
		assertThat(refus(() -> service.envoyerCode(JETON)).getMessage()).isEqualTo(SouscriptionPer.VERSEMENT_REQUIS);
		service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null);
		service.envoyerCode(JETON);

		service.choisirVersement(JETON, new BigDecimal("300"), "REVENUS", null);

		assertThat(refus(() -> service.signer(JETON, BON_CODE)).getMessage()).isEqualTo(SouscriptionPer.CODE_REQUIS);
	}

	@Test
	@DisplayName("Solde devenu insuffisant à la signature : refus, rien n'est débité")
	void soldeInsuffisantALaSignature() {
		service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null);
		service.envoyerCode(JETON);
		when(compte.getSoldeCentimes()).thenReturn(10_000L);

		assertThat(refus(() -> service.signer(JETON, BON_CODE)).getMessage())
				.isEqualTo(SouscriptionPer.SOLDE_INSUFFISANT);
		verify(compte, never()).debiter(any(Long.class), any());
	}

	@Test
	@DisplayName("Cliente qui a déjà un PER (contrat de test) : état « signé » avec son numéro, souscription refusée")
	void dejaUnPer() {
		when(gestion.numeroContrat(CLIENTE, Produit.PER)).thenReturn(Optional.of("PER-TEST-1"));

		Etat etat = service.etat(JETON);

		assertThat(etat.etape()).isEqualTo(Etape.SIGNEE);
		assertThat(etat.numeroContrat()).isEqualTo("PER-TEST-1");
		assertThat(refus(() -> service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null)).getMessage())
				.isEqualTo(SouscriptionPer.DEJA_SOUSCRIT);
		verify(codes, never()).envoyer(any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("Le SMS mentionne le PER et la validité de 5 minutes")
	void texteDuSms() {
		service.choisirVersement(JETON, CINQ_CENTS, "REVENUS", null);
		ArgumentCaptor<UnaryOperator<String>> texte = ArgumentCaptor.forClass(UnaryOperator.class);
		service.envoyerCode(JETON);
		verify(codes).envoyer(eq(compte), texte.capture());
		assertThat(texte.getValue().apply("111111")).contains("PER", "111111", "5 minutes");
	}

}
