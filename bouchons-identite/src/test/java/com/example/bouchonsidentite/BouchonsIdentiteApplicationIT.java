package com.example.bouchonsidentite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.document.SaisieIdentite;
import com.example.bouchonsidentite.document.StatutVerificationDocument;
import com.example.bouchonsidentite.document.VerificationDocumentRepository;
import com.example.bouchonsidentite.document.VerificationDocumentRequete;
import com.example.bouchonsidentite.document.VerificationDocumentService;
import com.example.bouchonsidentite.docusign.AppelDocusignRepository;
import com.example.bouchonsidentite.docusign.DocusignService;
import com.example.bouchonsidentite.docusign.ResultatDocusign;
import com.example.bouchonsidentite.fiscal.AdministrationFiscaleService;
import com.example.bouchonsidentite.fiscal.CasFiscal;
import com.example.bouchonsidentite.fiscal.FiscalRequetes.Interrogation;
import com.example.bouchonsidentite.fiscal.InterrogationFiscaleRepository;
import com.example.bouchonsidentite.fiscal.ResultatFiscal;
import com.example.bouchonsidentite.selfie.ResultatSelfie;
import com.example.bouchonsidentite.selfie.VerificationSelfieRepository;
import com.example.bouchonsidentite.selfie.VerificationSelfieRequete;
import com.example.bouchonsidentite.selfie.VerificationSelfieService;

// Démarrage complet, connecté à un PostgreSQL 16 jetable (migrations Flyway comprises) :
// les entités correspondent au schéma et les vérifications sont bien enregistrées.
// make test-integration ; Docker requis.
@SpringBootTest
@Testcontainers
class BouchonsIdentiteApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private VerificationDocumentService documents;

	@Autowired
	private VerificationDocumentRepository verificationsDocument;

	@Autowired
	private VerificationSelfieService selfies;

	@Autowired
	private VerificationSelfieRepository verificationsSelfie;

	@Autowired
	private DocusignService docusign;

	@Autowired
	private AppelDocusignRepository appelsDocusign;

	@Test
	void contextLoads() {
	}

	// CU-89 : chaque appel au bouchon Docusign est tracé (date, code reçu, résultat).
	@Test
	void appelsDocusignTraces() {
		var enveloppe = docusign.envoyerEnveloppe("dossier-it");
		var reponse = docusign.saisirCode(enveloppe.enveloppeId(), "000000");

		assertThat(reponse.resultat()).isEqualTo(ResultatDocusign.CODE_FAUX);
		assertThat(appelsDocusign.findAll()).anySatisfy(appel -> {
			assertThat(appel.getEnveloppeId()).isEqualTo(enveloppe.enveloppeId());
			assertThat(appel.getCodeRecu()).isEqualTo("000000");
			assertThat(appel.getResultat()).isEqualTo(ResultatDocusign.CODE_FAUX);
			assertThat(appel.getAppeleLe()).isNotNull();
		});
	}

	@Autowired
	private AdministrationFiscaleService administrationFiscale;

	@Autowired
	private InterrogationFiscaleRepository interrogationsFiscales;

	// CU-295 critère 9 : chaque interrogation est tracée (date, référence de la demande, résultat).
	@Test
	void interrogationsFiscalesTracees() {
		var reponse = administrationFiscale.interroger(new Interrogation("demande-it", "Cliente-A", "Test", "F",
				LocalDate.of(1990, 5, 14), "Paris", false, CasFiscal.DETENU_AILLEURS));

		assertThat(reponse.resultat()).isEqualTo(ResultatFiscal.LIVRET_A_EXISTANT);
		assertThat(reponse.detail()).isNull();
		assertThat(interrogationsFiscales.findAll()).anySatisfy(trace -> {
			assertThat(trace.getInterrogationId()).isEqualTo(reponse.interrogationId());
			assertThat(trace.getDemandeId()).isEqualTo("demande-it");
			assertThat(trace.getResultat()).isEqualTo(ResultatFiscal.LIVRET_A_EXISTANT);
			assertThat(trace.isDetailCommunique()).isFalse();
			assertThat(trace.getAppeleLe()).isNotNull();
		});
	}

	@Test
	void verificationDePieceEnregistreeAvecSaConcordance() {
		var saisie = new SaisieIdentite("Dupont", "Marie Claire", LocalDate.of(1990, 5, 14), null);
		var reponse = documents.verifier(new VerificationDocumentRequete("dossier-it", "PIECE_VALIDE", "PIECE_VALIDE", saisie));

		assertThat(verificationsDocument.findById(reponse.id())).hasValueSatisfying(verification -> {
			assertThat(verification.getStatut()).isEqualTo(StatutVerificationDocument.VERIFIE);
			assertThat(verification.getConcordanceNom()).isTrue();
			assertThat(verification.getConcordanceDateExpiration()).isNull();
			assertThat(verification.getVerifieLe()).isNotNull();
		});
	}

	@Test
	void verificationDeSelfieEnregistreeAvecSonScore() {
		var reponse = selfies.verifier(new VerificationSelfieRequete("dossier-it", "SCORE_89"));

		assertThat(verificationsSelfie.findById(reponse.id())).hasValueSatisfying(verification -> {
			assertThat(verification.getResultat()).isEqualTo(ResultatSelfie.NON_CORRESPONDANCE);
			assertThat(verification.getScore()).isEqualTo(89);
			assertThat(verification.getStatutDossier()).isEqualTo(StatutDossier.ANNULE);
		});
	}

}
