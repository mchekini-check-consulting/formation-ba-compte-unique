package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.PieceLivretAService.EtatPiece;
import com.example.compteunique.livreta.PieceLivretAService.Suivi;
import com.example.compteunique.livreta.ResultatsPiece.Document;
import com.example.compteunique.livreta.ResultatsPiece.Selfie;

// CU-29 (US-20, avec ex-CU-30) : un ou plusieurs cas de test par critère d'acceptation.
class PieceLivretAServiceTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final LocalDate AUJOURDHUI = MAINTENANT.toLocalDate();
	private static final LocalDate NOUVELLE_EXPIRATION = LocalDate.of(2036, 3, 1);
	private static final String JETON = "jeton";
	private static final UUID CLIENTE = UUID.randomUUID();

	private final CompteService comptes = mock(CompteService.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final PieceIdentiteLivretARepository verifications = mock(PieceIdentiteLivretARepository.class);
	private final IdentiteLivretAService identites = mock(IdentiteLivretAService.class);
	private final Dossier dossier = mock(Dossier.class);
	private final Compte compte = new Compte(7L, CLIENTE, Iban.de("99000000005"), 3L, MAINTENANT.minusDays(30));
	private final PieceLivretAService service = new PieceLivretAService(comptes, dossiers, verifications, identites,
			Clock.fixed(MAINTENANT.toInstant(), ZoneOffset.UTC));

	@BeforeEach
	void clienteAvecPieceExpiree() {
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(dossier.getId()).thenReturn(7L);
		when(dossier.getNomNaissance()).thenReturn("Cliente-Piece");
		when(dossier.getPrenoms()).thenReturn("Test");
		when(dossier.getDateNaissance()).thenReturn(LocalDate.of(1990, 1, 1));
		when(dossier.getDateExpiration()).thenReturn(LocalDate.of(2024, 1, 1));
		when(dossier.pieceExpireeLe(AUJOURDHUI)).thenReturn(true);
		when(dossiers.findById(7L)).thenReturn(Optional.of(dossier));
		when(verifications.findFirstByCustomerIdOrderByCreeLeDesc(CLIENTE)).thenReturn(Optional.empty());
		when(verifications.save(any(PieceIdentiteLivretA.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static void assertRefus(HttpStatus statut, String message, Runnable action) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, action::run);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	private static PieceIdentiteLivretA verification() {
		return new PieceIdentiteLivretA(7L, CLIENTE, TypePiece.PASSEPORT, "18AB12345", NOUVELLE_EXPIRATION,
				MAINTENANT);
	}

	private PieceIdentiteLivretA enregistree(PieceIdentiteLivretA verification) {
		when(verifications.findByReferenceAndCustomerId(verification.getReference(), CLIENTE))
				.thenReturn(Optional.of(verification));
		return verification;
	}

	private PieceIdentiteLivretA selfieAFaire() {
		PieceIdentiteLivretA verification = enregistree(verification());
		verification.recevoirDocument(Document.VERIFIE, 1, MAINTENANT);
		return verification;
	}

	@Test
	void etapeDemandeeSeulementSiLaPieceDuDossierAExpire() {
		EtatPiece etat = service.etat(JETON);
		assertTrue(etat.requise());
		assertEquals(LocalDate.of(2024, 1, 1), etat.dateExpirationActuelle());
		assertNull(etat.suivi());

		when(dossier.pieceExpireeLe(AUJOURDHUI)).thenReturn(false);
		assertFalse(service.etat(JETON).requise());
		assertRefus(HttpStatus.CONFLICT, PieceLivretAService.PIECE_ENCORE_VALIDE,
				() -> service.soumettre(JETON, TypePiece.PASSEPORT, "18AB12345", NOUVELLE_EXPIRATION));
	}

	@Test
	void etatAvecLaDerniereVerification() {
		PieceIdentiteLivretA derniere = verification();
		when(verifications.findFirstByCustomerIdOrderByCreeLeDesc(CLIENTE)).thenReturn(Optional.of(derniere));
		Suivi suivi = service.etat(JETON).suivi();
		assertEquals(derniere, suivi.verification());
		assertEquals("Cliente-Piece", suivi.aVerifier().nomNaissance());
		assertEquals(NOUVELLE_EXPIRATION, suivi.aVerifier().dateExpiration());
	}

	@ParameterizedTest
	@EnumSource(TypePiece.class)
	void nouvellePieceEnregistreeCniPasseportOuTitreDeSejour(TypePiece type) {
		Suivi suivi = service.soumettre(JETON, type, " 18ab-123 45 ", NOUVELLE_EXPIRATION);
		PieceIdentiteLivretA verification = suivi.verification();
		assertEquals(StatutPieceLivretA.PIECE_A_VERIFIER, verification.getStatut());
		assertEquals(type, verification.getTypePiece());
		assertEquals("18AB12345", verification.getNumeroPiece());
		assertEquals(NOUVELLE_EXPIRATION, verification.getDateExpiration());
		assertEquals(MAINTENANT, verification.getCreeLe());
		assertEquals(LocalDate.of(1990, 1, 1), suivi.aVerifier().dateNaissance());
		verify(identites).controleValide(compte);
	}

	@Test
	void identiteNonConfirmeeRefusee() {
		when(identites.controleValide(compte)).thenThrow(new DemandeRefusee(HttpStatus.CONFLICT,
				IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE));
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE,
				() -> service.soumettre(JETON, TypePiece.PASSEPORT, "18AB12345", NOUVELLE_EXPIRATION));
		verify(verifications, never()).save(any());
	}

	@Test
	void saisieIncompleteOuInvalideRefusee() {
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.TYPE_OBLIGATOIRE,
				() -> service.soumettre(JETON, null, "18AB12345", NOUVELLE_EXPIRATION));
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.EXPIRATION_OBLIGATOIRE,
				() -> service.soumettre(JETON, TypePiece.PASSEPORT, "18AB12345", null));
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.EXPIRATION_DEPASSEE,
				() -> service.soumettre(JETON, TypePiece.PASSEPORT, "18AB12345", AUJOURDHUI));
		verify(verifications, never()).save(any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "1234", "18AB/12345", "1234567890123456789012345678901" })
	void numeroInvalideRefuse(String numero) {
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.NUMERO_INVALIDE,
				() -> service.soumettre(JETON, TypePiece.PASSEPORT, numero, NOUVELLE_EXPIRATION));
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.NUMERO_INVALIDE, () -> PieceLivretAService.numero(null));
	}

	@Test
	void pieceVerifieeLeSelfieEstAttendu() {
		PieceIdentiteLivretA verification = enregistree(verification());
		Suivi suivi = service.enregistrerDocument(JETON, verification.getReference(), Document.VERIFIE, 1);
		assertEquals(StatutPieceLivretA.SELFIE_A_FAIRE, suivi.verification().getStatut());
		assertNull(suivi.aVerifier());
		assertEquals(Document.VERIFIE, verification.getResultatDocument());
		assertEquals(MAINTENANT, verification.getDocumentLe());
	}

	@ParameterizedTest
	@EnumSource(value = Document.class, names = { "A_REPRENDRE", "ERREUR_TECHNIQUE" })
	void pieceAReprendreOuErreurTechniqueLaClienteRecommence(Document resultat) {
		PieceIdentiteLivretA verification = enregistree(verification());
		Suivi suivi = service.enregistrerDocument(JETON, verification.getReference(), resultat, 1);
		assertEquals(StatutPieceLivretA.PIECE_A_VERIFIER, suivi.verification().getStatut());
		assertNotNull(suivi.aVerifier());
		assertEquals(StatutPieceLivretA.SELFIE_A_FAIRE,
				service.enregistrerDocument(JETON, verification.getReference(), Document.VERIFIE, 1).verification()
						.getStatut());
	}

	@Test
	void pieceRejeteeLaDemandeEstArretee() {
		PieceIdentiteLivretA verification = enregistree(verification());
		assertEquals(StatutPieceLivretA.BLOQUE,
				service.enregistrerDocument(JETON, verification.getReference(), Document.REJETE, 1).verification()
						.getStatut());
		assertNull(verification.getConseillerAlerteLe());
	}

	@Test
	void analyseSansReponseApresTroisRelancesEnAttenteEtConseillerAlerte() {
		PieceIdentiteLivretA verification = enregistree(verification());
		service.enregistrerDocument(JETON, verification.getReference(), Document.SANS_REPONSE, 4);
		assertEquals(StatutPieceLivretA.EN_ATTENTE, verification.getStatut());
		assertEquals(4, verification.getTentativesDocument());
		assertEquals(MAINTENANT, verification.getConseillerAlerteLe());
	}

	@Test
	void resultatDeLAnalyseIncompletOuHorsEtape() {
		PieceIdentiteLivretA verification = enregistree(verification());
		String reference = verification.getReference();
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.RESULTAT_OBLIGATOIRE,
				() -> service.enregistrerDocument(JETON, reference, null, 1));
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.TENTATIVES_INVALIDES,
				() -> service.enregistrerDocument(JETON, reference, Document.VERIFIE, 0));
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.TENTATIVES_INVALIDES,
				() -> service.enregistrerDocument(JETON, reference, Document.VERIFIE, 5));
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.TENTATIVES_INVALIDES,
				() -> service.enregistrerDocument(JETON, reference, Document.VERIFIE, null));
		assertRefus(HttpStatus.NOT_FOUND, PieceLivretAService.VERIFICATION_INTROUVABLE,
				() -> service.enregistrerDocument(JETON, "inconnue", Document.VERIFIE, 1));
		service.enregistrerDocument(JETON, reference, Document.VERIFIE, 1);
		assertRefus(HttpStatus.CONFLICT, PieceLivretAService.PAS_DE_PIECE_A_VERIFIER,
				() -> service.enregistrerDocument(JETON, reference, Document.VERIFIE, 1));
	}

	@ParameterizedTest
	@ValueSource(ints = { 96, 90, 100 })
	void selfieAuMoins90PourCentLaNouvellePieceRemplaceCelleDuDossier(int score) {
		PieceIdentiteLivretA verification = selfieAFaire();
		Suivi suivi = service.enregistrerSelfie(JETON, verification.getReference(), Selfie.CORRESPONDANCE, score, 1);
		assertEquals(StatutPieceLivretA.VALIDE, suivi.verification().getStatut());
		assertEquals(score, verification.getScoreSelfie());
		assertEquals(MAINTENANT, verification.getValideLe());
		verify(dossier).remplacerPiece(TypePiece.PASSEPORT, "18AB12345", NOUVELLE_EXPIRATION, MAINTENANT);
	}

	@Test
	void selfieInferieurA90PourCentLaDemandeEstBloqueeSansToucherAuDossier() {
		PieceIdentiteLivretA verification = selfieAFaire();
		service.enregistrerSelfie(JETON, verification.getReference(), Selfie.NON_CORRESPONDANCE, 89, 1);
		assertEquals(StatutPieceLivretA.BLOQUE, verification.getStatut());
		verify(dossier, never()).remplacerPiece(any(), any(), any(), any());
	}

	@Test
	void photoDEcranLaVerificationDePresenceEchoue() {
		PieceIdentiteLivretA verification = selfieAFaire();
		service.enregistrerSelfie(JETON, verification.getReference(), Selfie.ECHEC_VERIFICATION_PRESENCE, null, 1);
		assertEquals(StatutPieceLivretA.BLOQUE, verification.getStatut());
		assertNull(verification.getValideLe());
	}

	@Test
	void selfieSansReponseEnAttenteEtConseillerAlerte() {
		PieceIdentiteLivretA verification = selfieAFaire();
		service.enregistrerSelfie(JETON, verification.getReference(), Selfie.SANS_REPONSE, null, 4);
		assertEquals(StatutPieceLivretA.EN_ATTENTE, verification.getStatut());
		assertEquals(4, verification.getTentativesSelfie());
		assertEquals(MAINTENANT, verification.getSelfieLe());
		assertEquals(MAINTENANT, verification.getConseillerAlerteLe());
	}

	@ParameterizedTest
	@CsvSource(value = { "CORRESPONDANCE,89", "CORRESPONDANCE,101", "CORRESPONDANCE,", "NON_CORRESPONDANCE,90",
			"NON_CORRESPONDANCE,-1", "NON_CORRESPONDANCE,", "ECHEC_VERIFICATION_PRESENCE,50",
			"SANS_REPONSE,50" })
	void scoreIncoherentRefuse(Selfie resultat, Integer score) {
		PieceIdentiteLivretA verification = selfieAFaire();
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.SCORE_INVALIDE,
				() -> service.enregistrerSelfie(JETON, verification.getReference(), resultat, score, 1));
	}

	@Test
	void selfieIncompletOuHorsEtape() {
		PieceIdentiteLivretA verification = enregistree(verification());
		String reference = verification.getReference();
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.RESULTAT_OBLIGATOIRE,
				() -> service.enregistrerSelfie(JETON, reference, null, null, 1));
		assertRefus(HttpStatus.BAD_REQUEST, PieceLivretAService.TENTATIVES_INVALIDES,
				() -> service.enregistrerSelfie(JETON, reference, Selfie.CORRESPONDANCE, 96, 0));
		assertRefus(HttpStatus.CONFLICT, PieceLivretAService.PAS_DE_SELFIE_A_FAIRE,
				() -> service.enregistrerSelfie(JETON, reference, Selfie.CORRESPONDANCE, 96, 1));
	}

	@ParameterizedTest
	@EnumSource(value = StatutPieceLivretA.class, names = { "BLOQUE", "EN_ATTENTE" })
	void demarcheArreteeOuEnAttenteNePeutPasEtreRepriseEnLigne(StatutPieceLivretA statut) {
		PieceIdentiteLivretA derniere = verification();
		derniere.recevoirDocument(statut == StatutPieceLivretA.BLOQUE ? Document.REJETE : Document.SANS_REPONSE, 1,
				MAINTENANT);
		when(verifications.findFirstByCustomerIdOrderByCreeLeDesc(CLIENTE)).thenReturn(Optional.of(derniere));
		DemandeRefusee refus = assertThrows(DemandeRefusee.class,
				() -> service.soumettre(JETON, TypePiece.PASSEPORT, "18AB12345", NOUVELLE_EXPIRATION));
		assertEquals(PieceLivretAService.DEMARCHE_SUSPENDUE, refus.getMessage());
		assertTrue(refus.isTermine());
	}

	@Test
	void nouvellePieceApresUneVerificationEnCoursEstAcceptee() {
		when(verifications.findFirstByCustomerIdOrderByCreeLeDesc(CLIENTE)).thenReturn(Optional.of(verification()));
		assertEquals(StatutPieceLivretA.PIECE_A_VERIFIER,
				service.soumettre(JETON, TypePiece.TITRE_SEJOUR, "AB12345", NOUVELLE_EXPIRATION).verification()
						.getStatut());
	}

	@Test
	void dossierIntrouvable() {
		when(dossiers.findById(7L)).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, PieceLivretAService.VERIFICATION_INTROUVABLE, () -> service.etat(JETON));
	}

	@Test
	void constructeurSpringUtiliseLHorlogeSysteme() {
		assertNotNull(new PieceLivretAService(comptes, dossiers, verifications, identites));
	}

}
