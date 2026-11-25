package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.livreta.IdentiteLivretAService.Identite;
import com.example.compteunique.livreta.IdentiteLivretAService.IdentiteAControler;
import com.example.compteunique.livreta.LivretAController.Confirmation;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.ResultatControle;
import com.example.compteunique.livreta.LivretAController.Refus;
import com.example.compteunique.livreta.LivretAController.ResultatInterrogation;
import com.example.compteunique.livreta.LivretAService.Suivi;
import com.example.compteunique.livreta.LivretAService.Transmission;
import com.example.compteunique.livreta.PieceLivretAService.EtatPiece;
import com.example.compteunique.livreta.PieceLivretAService.PieceAVerifier;
import com.example.compteunique.livreta.ResultatsPiece.Document;
import com.example.compteunique.livreta.ResultatsPiece.Selfie;

// CU-31 : routes /livret-a et vue de la demande (messages affichés à la cliente).
class LivretAControllerTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final Transmission TRANSMISSION = new Transmission("N", "P", "F", LocalDate.of(1990, 1, 1), "Paris");

	private final LivretAService service = mock(LivretAService.class);
	private final IdentiteLivretAService identites = mock(IdentiteLivretAService.class);
	private final PieceLivretAService pieces = mock(PieceLivretAService.class);
	private final ClotureLivretAService clotures = mock(ClotureLivretAService.class);
	private final LivretAController controleur = new LivretAController(service, identites, pieces, clotures);

	private static DemandeLivretA demande() {
		return new DemandeLivretA(7L, UUID.randomUUID(), true, MAINTENANT);
	}

	@Test
	void etatSansDemandePuisAvecDemande() {
		when(service.etat("j")).thenReturn(Optional.empty());
		assertNull(controleur.etat("j").demande());
		DemandeLivretA demande = demande();
		when(service.etat("j")).thenReturn(Optional.of(new Suivi(demande, TRANSMISSION)));
		DemandeLivretAVue vue = controleur.etat("j").demande();
		assertEquals(demande.getReference(), vue.reference());
		assertEquals(DemandeLivretAVue.EN_COURS, vue.message());
		assertEquals("2 jours ouvrés", vue.delai());
		assertEquals(TRANSMISSION, vue.transmission());
		assertTrue(vue.partageDetailAutorise());
		assertEquals(MAINTENANT, vue.attestationLe());
		assertEquals(MAINTENANT, vue.choixPartageLe());
	}

	@Test
	void demandeCreeeEn201() {
		when(service.demander("j", true, false)).thenReturn(new Suivi(demande(), TRANSMISSION));
		ResponseEntity<DemandeLivretAVue> reponse = controleur.demander("j", new Eligibilite(true, false));
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		when(service.demander("j", null, null)).thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "x"));
		ResponseEntity<Refus> refus = controleur.refus(assertThrows(DemandeRefusee.class, () -> controleur.demander("j", null)));
		assertEquals(HttpStatus.BAD_REQUEST, refus.getStatusCode());
		assertEquals("x", refus.getBody().message());
		assertFalse(refus.getBody().termine());
	}

	@Test
	void reponseFiscaleTransmise() {
		DemandeLivretA demande = demande();
		demande.recevoirReponse("i", ReponseFiscale.AUCUN_LIVRET_A, MAINTENANT);
		when(service.enregistrerReponse("j", "ref", "i", ReponseFiscale.AUCUN_LIVRET_A, null))
				.thenReturn(new Suivi(demande, null));
		DemandeLivretAVue vue = controleur.reponseFiscale("j", "ref",
				new ResultatInterrogation("i", ReponseFiscale.AUCUN_LIVRET_A));
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, vue.statut());
		assertEquals(DemandeLivretAVue.VALIDEE, vue.message());
		assertNull(vue.transmission());
		when(service.enregistrerReponse("j", "ref", null, null, null))
				.thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "y"));
		assertThrows(DemandeRefusee.class,
				() -> controleur.reponseFiscale("j", "ref", null));
	}

	@Test
	void messagesSelonLaReponse() {
		DemandeLivretA existant = demande();
		existant.recevoirReponse("i", ReponseFiscale.LIVRET_A_EXISTANT, MAINTENANT);
		assertEquals(DemandeLivretAVue.EXISTANT, DemandeLivretAVue.de(new Suivi(existant, null)).message());

		DemandeLivretA indisponible = demande();
		indisponible.recevoirReponse("i", ReponseFiscale.INDISPONIBLE, MAINTENANT);
		DemandeLivretAVue vue = DemandeLivretAVue.de(new Suivi(indisponible, TRANSMISSION));
		assertEquals(DemandeLivretAVue.INDISPONIBLE, vue.message());
		assertFalse(vue.retard());

		indisponible.alerterConseiller(MAINTENANT);
		DemandeLivretAVue enRetard = DemandeLivretAVue.de(new Suivi(indisponible, TRANSMISSION));
		assertTrue(enRetard.retard());
		assertEquals(DemandeLivretAVue.MESSAGE_RETARD, enRetard.message());
		assertEquals(ReponseFiscale.INDISPONIBLE, enRetard.reponseFiscale());
		assertEquals(MAINTENANT, enRetard.interrogationLe());
	}

	// CU-296 : choix de la suite, dépôt du certificat (multipart) et boutons de test du conseiller.
	@Test
	void choixDeLaSuiteEtDepotDuCertificat() throws Exception {
		DemandeLivretA demande = demande();
		demande.recevoirReponse("i", ReponseFiscale.LIVRET_A_EXISTANT, MAINTENANT);
		demande.choisirCloture(MAINTENANT);
		when(clotures.choisirSuite("j", "ref", SuiteLivretAExistant.CLOTURE_PAR_CLIENTE)).thenReturn(demande);
		when(service.suivi(demande)).thenReturn(new Suivi(demande, null));
		DemandeLivretAVue vue = controleur.choisirSuite("j", "ref",
				new LivretAController.ChoixSuite(SuiteLivretAExistant.CLOTURE_PAR_CLIENTE));
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, vue.statut());
		assertEquals(LocalDate.of(2027, 2, 12), vue.dateLimiteCertificat());
		when(clotures.choisirSuite("j", "ref", null)).thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "c"));
		assertThrows(DemandeRefusee.class, () -> controleur.choisirSuite("j", "ref", null));

		MockMultipartFile fichier = new MockMultipartFile("fichier", "certificat.pdf", "application/pdf",
				new byte[] { 1 });
		when(clotures.deposerCertificat("j", "ref", "certificat.pdf", "application/pdf", new byte[] { 1 }))
				.thenReturn(demande);
		assertEquals(HttpStatus.CREATED, controleur.deposerCertificat("j", "ref", fichier).getStatusCode());
		when(clotures.deposerCertificat("j", "ref", null, null, null))
				.thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "f"));
		assertThrows(DemandeRefusee.class, () -> controleur.deposerCertificat("j", "ref", null));
	}

	@Test
	void controleurDeTestValideOuRefuseLeCertificat() {
		DemandeLivretA demande = demande();
		when(clotures.simulerValidation("ref")).thenReturn(demande);
		when(clotures.simulerRefus("ref", "illisible")).thenReturn(demande);
		when(clotures.simulerRefus("ref", null)).thenReturn(demande);
		when(service.suivi(demande)).thenReturn(new Suivi(demande, null));
		LivretATestController test = new LivretATestController(service, identites, clotures);
		assertEquals(demande.getReference(), test.validerCertificat("ref").reference());
		assertEquals(demande.getReference(),
				test.refuserCertificat("ref", new LivretATestController.RefusCertificat("illisible")).reference());
		assertEquals(demande.getReference(), test.refuserCertificat("ref", null).reference());
	}

	@Test
	void sansSessionOuSansCompte() {
		ResponseEntity<Refus> nonConnectee = controleur.refus(new ClienteNonConnecteeException());
		assertEquals(HttpStatus.UNAUTHORIZED, nonConnectee.getStatusCode());
		assertNull(nonConnectee.getBody());
		ResponseEntity<Refus> sansCompte = controleur.refus(new CompteInaccessibleException());
		assertEquals(HttpStatus.NOT_FOUND, sansCompte.getStatusCode());
		assertEquals(LivretAController.SANS_COMPTE, sansCompte.getBody().message());
	}

	@Test
	void controleurDeTestSimuleLExpiration() {
		DemandeLivretA demande = demande();
		demande.recevoirReponse("i", ReponseFiscale.SANS_REPONSE, MAINTENANT);
		demande.alerterConseiller(MAINTENANT);
		when(service.simulerExpirationDelai("ref")).thenReturn(demande);
		LivretATestController test = new LivretATestController(service, identites, clotures);
		assertTrue(test.expirerDelai("ref").retard());
		ResponseEntity<Refus> refus = test.refus(new DemandeRefusee(HttpStatus.CONFLICT, "z"));
		assertEquals(HttpStatus.CONFLICT, refus.getStatusCode());
	}

	// CU-28 : routes de l'étape « Mon identité ».
	private static final Coordonnees COORDONNEES = new Coordonnees("1 rue", null, "75011", "Paris", "France",
			"a@b.fr", "0612345678");
	private static final IdentiteAControler A_CONTROLER = new IdentiteAControler("N", "P", LocalDate.of(1990, 1, 1),
			"FRANCE");

	private static ControleIdentiteLivretA controle() {
		return new ControleIdentiteLivretA(7L, UUID.randomUUID(), false, MAINTENANT);
	}

	@Test
	void identitePreRemplieEtDernierControle() {
		Identite identite = new Identite("Mme", "N", "P", LocalDate.of(1990, 1, 1), COORDONNEES, "France");
		when(identites.identite("j")).thenReturn(identite);
		when(identites.dernierControle("j")).thenReturn(Optional.empty());
		assertEquals(identite, controleur.identite("j").identite());
		assertNull(controleur.identite("j").controle());
		ControleIdentiteLivretA controle = controle();
		when(identites.dernierControle("j")).thenReturn(Optional.of(new IdentiteLivretAService.Suivi(controle, A_CONTROLER)));
		ControleIdentiteVue vue = controleur.identite("j").controle();
		assertEquals(controle.getReference(), vue.reference());
		assertEquals(ControleIdentiteVue.EN_COURS, vue.message());
		assertEquals(A_CONTROLER, vue.aControler());
	}

	@Test
	void identiteConfirmeeEn201() {
		ControleIdentiteLivretA controle = controle();
		when(identites.confirmer("j", COORDONNEES, true))
				.thenReturn(new IdentiteLivretAService.Suivi(controle, A_CONTROLER));
		ResponseEntity<ControleIdentiteVue> reponse = controleur.confirmerIdentite("j", new Confirmation(COORDONNEES, true));
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(StatutControleIdentite.EN_COURS, reponse.getBody().statut());
		when(identites.confirmer("j", null, null)).thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "x"));
		assertThrows(DemandeRefusee.class, () -> controleur.confirmerIdentite("j", null));
	}

	@Test
	void resultatDuControleEtMessages() {
		ControleIdentiteLivretA controle = controle();
		controle.recevoirResultat(ResultatSanctions.CORRESPONDANCE_SANCTIONS, 1, MAINTENANT);
		when(identites.enregistrerResultat("j", "ref", ResultatSanctions.CORRESPONDANCE_SANCTIONS, 1))
				.thenReturn(new IdentiteLivretAService.Suivi(controle, null));
		ControleIdentiteVue vue = controleur.resultatControle("j", "ref",
				new ResultatControle(ResultatSanctions.CORRESPONDANCE_SANCTIONS, 1));
		assertEquals(ControleIdentiteVue.BLOQUE, vue.message());
		assertNull(vue.aControler());
		when(identites.enregistrerResultat("j", "ref", null, null)).thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "x"));
		assertThrows(DemandeRefusee.class, () -> controleur.resultatControle("j", "ref", null));
		assertEquals(ControleIdentiteVue.VALIDE, ControleIdentiteVue.message(StatutControleIdentite.VALIDE));
		assertEquals(ControleIdentiteVue.ATTENTE_RESPONSABLE,
				ControleIdentiteVue.message(StatutControleIdentite.ATTENTE_RESPONSABLE));
		assertEquals(ControleIdentiteVue.EN_ATTENTE, ControleIdentiteVue.message(StatutControleIdentite.EN_ATTENTE));
	}

	@Test
	void controleurDeTestValideUnePPE() {
		ControleIdentiteLivretA controle = controle();
		controle.recevoirResultat(ResultatSanctions.CORRESPONDANCE_PEP, 1, MAINTENANT);
		controle.validerParResponsable(MAINTENANT);
		when(identites.validerParResponsable("ref")).thenReturn(controle);
		LivretATestController test = new LivretATestController(service, identites, clotures);
		ControleIdentiteVue vue = test.validerParResponsable("ref");
		assertEquals(StatutControleIdentite.VALIDE, vue.statut());
		assertTrue(vue.utilisable());
	}


	// CU-29 : étape « Ma pièce d'identité ».
	private static final LocalDate EXPIRATION = LocalDate.of(2036, 3, 1);
	private static final PieceAVerifier A_VERIFIER = new PieceAVerifier("N", "P", LocalDate.of(1990, 1, 1),
			EXPIRATION);

	private static PieceIdentiteLivretA piece() {
		return new PieceIdentiteLivretA(7L, UUID.randomUUID(), TypePiece.PASSEPORT, "18AB12345", EXPIRATION,
				MAINTENANT);
	}

	@Test
	void etapePieceRequiseEtDerniereVerification() {
		when(pieces.etat("j")).thenReturn(new EtatPiece(true, LocalDate.of(2024, 1, 1), null));
		LivretAController.EtapePiece etape = controleur.piece("j");
		assertTrue(etape.requise());
		assertEquals(LocalDate.of(2024, 1, 1), etape.dateExpirationActuelle());
		assertNull(etape.verification());
		PieceIdentiteLivretA piece = piece();
		when(pieces.etat("j")).thenReturn(
				new EtatPiece(true, LocalDate.of(2024, 1, 1), new PieceLivretAService.Suivi(piece, A_VERIFIER)));
		PieceLivretAVue vue = controleur.piece("j").verification();
		assertEquals(piece.getReference(), vue.reference());
		assertEquals(PieceLivretAVue.PIECE_A_VERIFIER, vue.message());
		assertEquals(TypePiece.PASSEPORT, vue.typePiece());
		assertEquals(EXPIRATION, vue.dateExpiration());
		assertEquals(A_VERIFIER, vue.aVerifier());
	}

	@Test
	void nouvellePieceEn201() {
		when(pieces.soumettre("j", TypePiece.PASSEPORT, "18AB12345", EXPIRATION))
				.thenReturn(new PieceLivretAService.Suivi(piece(), A_VERIFIER));
		ResponseEntity<PieceLivretAVue> reponse = controleur.soumettrePiece("j",
				new LivretAController.NouvellePiece(TypePiece.PASSEPORT, "18AB12345", EXPIRATION));
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(StatutPieceLivretA.PIECE_A_VERIFIER, reponse.getBody().statut());
		when(pieces.soumettre("j", null, null, null)).thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "x"));
		assertThrows(DemandeRefusee.class, () -> controleur.soumettrePiece("j", null));
	}

	@Test
	void resultatsDeLAnalyseEtDuSelfie() {
		PieceIdentiteLivretA piece = piece();
		piece.recevoirDocument(Document.VERIFIE, 1, MAINTENANT);
		when(pieces.enregistrerDocument("j", "ref", Document.VERIFIE, 1))
				.thenReturn(new PieceLivretAService.Suivi(piece, null));
		PieceLivretAVue verifiee = controleur.resultatDocument("j", "ref",
				new LivretAController.ResultatDocument(Document.VERIFIE, 1));
		assertEquals(PieceLivretAVue.SELFIE_A_FAIRE, verifiee.message());
		assertNull(verifiee.aVerifier());
		when(pieces.enregistrerDocument("j", "ref", null, null)).thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "x"));
		assertThrows(DemandeRefusee.class, () -> controleur.resultatDocument("j", "ref", null));

		piece.recevoirSelfie(Selfie.CORRESPONDANCE, 96, 1, MAINTENANT);
		when(pieces.enregistrerSelfie("j", "ref", Selfie.CORRESPONDANCE, 96, 1))
				.thenReturn(new PieceLivretAService.Suivi(piece, null));
		assertEquals(PieceLivretAVue.VALIDE, controleur.resultatSelfie("j", "ref",
				new LivretAController.ResultatSelfie(Selfie.CORRESPONDANCE, 96, 1)).message());
		when(pieces.enregistrerSelfie("j", "ref", null, null, null))
				.thenThrow(new DemandeRefusee(HttpStatus.BAD_REQUEST, "x"));
		assertThrows(DemandeRefusee.class, () -> controleur.resultatSelfie("j", "ref", null));

		assertEquals(PieceLivretAVue.BLOQUE, PieceLivretAVue.message(StatutPieceLivretA.BLOQUE));
		assertTrue(PieceLivretAVue.message(StatutPieceLivretA.EN_ATTENTE).endsWith("48 heures ouvrées."));
	}

}
