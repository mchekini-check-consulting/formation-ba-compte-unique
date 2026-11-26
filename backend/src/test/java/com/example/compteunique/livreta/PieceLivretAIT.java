package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.LivretAController.Confirmation;
import com.example.compteunique.livreta.LivretAController.Eligibilite;
import com.example.compteunique.livreta.LivretAController.NouvellePiece;
import com.example.compteunique.livreta.LivretAController.ResultatControle;
import com.example.compteunique.livreta.LivretAController.ResultatDocument;
import com.example.compteunique.livreta.LivretAController.ResultatSelfie;
import com.example.compteunique.livreta.ResultatsPiece.Document;
import com.example.compteunique.livreta.ResultatsPiece.Selfie;

// CU-29 contre un vrai PostgreSQL (migration V24, cliente de test TEST-CLIENTE-PIECE dont la pièce a expiré) :
// éligibilité refusée tant que la pièce est expirée, puis pièce et selfie vérifiés, pièce remplacée dans le
// dossier, éligibilité ouverte.
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration,classpath:db/demo" })
@Testcontainers
class PieceLivretAIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final LocalDate NOUVELLE_EXPIRATION = LocalDate.of(2036, 3, 1);

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private LivretAController livretA;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private QuestionsLivretAController questions;

	private String identiteConfirmee(String identifiant) {
		String jeton = connexion.connecter(identifiant, MOT_DE_PASSE, null).jetonSession();
		Coordonnees coordonnees = livretA.identite(jeton).identite().coordonnees();
		ControleIdentiteVue controle = livretA.confirmerIdentite(jeton, new Confirmation(coordonnees, true)).getBody();
		livretA.resultatControle(jeton, controle.reference(),
				new ResultatControle(ResultatSanctions.AUCUNE_CORRESPONDANCE, 1));
		return jeton;
	}

	@Test
	void pieceExpireeRemplaceeApresPieceEtSelfieVerifies() {
		String jeton = identiteConfirmee("TEST-CLIENTE-PIECE");
		LivretAController.EtapePiece etape = livretA.piece(jeton);
		assertTrue(etape.requise());
		assertEquals(LocalDate.of(2024, 1, 1), etape.dateExpirationActuelle());
		assertNull(etape.verification());
		DemandeRefusee expiree = assertThrows(DemandeRefusee.class,
				() -> livretA.demander(jeton, new Eligibilite(true, true)));
		assertEquals(LivretAService.PIECE_EXPIREE, expiree.getMessage());
		// CU-32 : l'étape « Questions et données » vient après la nouvelle pièce.
		DemandeRefusee questionsAvantPiece = assertThrows(DemandeRefusee.class,
				() -> QuestionnaireExemple.rempli(questions, jeton));
		assertEquals(LivretAService.PIECE_EXPIREE, questionsAvantPiece.getMessage());

		PieceLivretAVue piece = livretA
				.soumettrePiece(jeton, new NouvellePiece(TypePiece.PASSEPORT, "18AB12345", NOUVELLE_EXPIRATION))
				.getBody();
		assertEquals(StatutPieceLivretA.PIECE_A_VERIFIER, piece.statut());
		assertEquals("Cliente-Piece", piece.aVerifier().nomNaissance());
		PieceLivretAVue verifiee = livretA.resultatDocument(jeton, piece.reference(),
				new ResultatDocument(Document.VERIFIE, 1));
		assertEquals(StatutPieceLivretA.SELFIE_A_FAIRE, verifiee.statut());
		PieceLivretAVue validee = livretA.resultatSelfie(jeton, piece.reference(),
				new ResultatSelfie(Selfie.CORRESPONDANCE, 96, 1));
		assertEquals(PieceLivretAVue.VALIDE, validee.message());

		assertThat(dossiers.findByReference("demo-cliente-piece")).hasValueSatisfying(dossier -> {
			assertEquals(TypePiece.PASSEPORT, dossier.getTypePiece());
			assertEquals("18AB12345", dossier.getNumeroPiece());
			assertEquals(NOUVELLE_EXPIRATION, dossier.getDateExpiration());
		});
		assertFalse(livretA.piece(jeton).requise());
		QuestionnaireExemple.rempli(questions, jeton);
		assertEquals(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION,
				livretA.demander(jeton, new Eligibilite(true, false)).getBody().statut());
	}

	@Test
	void pieceEncoreValideAucuneNouvellePieceDemandee() {
		String jeton = identiteConfirmee("TEST-CLIENTE-B");
		assertFalse(livretA.piece(jeton).requise());
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, () -> livretA.soumettrePiece(jeton,
				new NouvellePiece(TypePiece.CARTE_NATIONALE_IDENTITE, "AB12345", NOUVELLE_EXPIRATION)));
		assertEquals(PieceLivretAService.PIECE_ENCORE_VALIDE, refus.getMessage());
	}

}
