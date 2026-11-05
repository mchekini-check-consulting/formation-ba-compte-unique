package com.example.compteunique.ouverture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.activation.ActivationService;
import com.example.compteunique.activation.EtatActivation;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.connexion.ConnexionRefuseeException;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.connexion.MotDePasseNonConformeException;
import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.ControlesRequete;
import com.example.compteunique.dossier.DossierService;
import com.example.compteunique.dossier.ExempleIdentite;
import com.example.compteunique.messagerie.Canal;
import com.example.compteunique.messagerie.Messagerie;
import com.example.compteunique.reinitialisation.EtatReinitialisation;
import com.example.compteunique.reinitialisation.ReinitialisationService;
import com.example.compteunique.signature.ResultatSignature;
import com.example.compteunique.signature.SignatureService;

// CU-12 -> CU-13 -> CU-87 -> CU-14 -> CU-15 contre un vrai PostgreSQL (migrations Flyway comprises) :
// signature, création du compte, e-mail et SMS capturés, activation, connexion, puis CU-46 :
// mot de passe oublié.
@SpringBootTest
@Testcontainers
class OuvertureCompteIT {

	private static final Pattern JETON = Pattern.compile("jeton=([A-Za-z0-9_-]+)");
	private static final Pattern IDENTIFIANT = Pattern.compile("\\b(\\d{8})\\b");
	private static final String MOT_DE_PASSE = "Abcdefghij1!";
	private static final String NOUVEAU = "Nouveau-Secret-2026";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private DossierService dossiers;

	@Autowired
	private SignatureService signatures;

	@Autowired
	private OuvertureService ouverture;

	@Autowired
	private Messagerie messagerie;

	@Autowired
	private ActivationService activation;

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private ReinitialisationService reinitialisation;

	@Autowired
	private JdbcTemplate base;

	@Test
	void parcoursCompletDeLaSignatureALaConnexion() {
		dossiers.renseignerIdentite("it-ouverture", ExempleIdentite.identite());
		dossiers.enregistrerControles("it-ouverture", new ControlesRequete(ResultatControle.OK, ResultatControle.OK));

		signatures.enveloppeEnvoyee("it-ouverture", "env-it");
		signatures.enregistrerResultat("it-ouverture", ResultatSignature.CODE_FAUX);
		assertThat(signatures.enregistrerResultat("it-ouverture", ResultatSignature.SIGNATURE_VALIDE).etat())
				.isEqualTo("SIGNEE");

		OuvertureReponse confirmation = ouverture.etat("it-ouverture");
		assertThat(confirmation.statutDossier()).isEqualTo(StatutDossier.COMPTE_OUVERT);
		assertThat(confirmation.envoiEmail()).isEqualTo(ResultatEnvoi.ENVOYE);
		assertThat(confirmation.envoiSms()).isEqualTo(ResultatEnvoi.ENVOYE);
		String iban = base.queryForObject(
				"select c.iban from compte c join dossier d on d.id = c.dossier_id where d.reference = ?",
				String.class, "it-ouverture");
		assertThat(Iban.estValide(iban)).isTrue();
		assertThat(base.queryForObject("select codes_faux from signature_convention", Integer.class)).isEqualTo(1);

		Matcher identifiant = IDENTIFIANT.matcher(messagerie.dernier(Canal.EMAIL).orElseThrow().getTexte());
		assertThat(identifiant.find()).isTrue();
		Matcher jeton = JETON.matcher(messagerie.dernier(Canal.SMS).orElseThrow().getTexte());
		assertThat(jeton.find()).isTrue();

		assertThat(activation.etat(jeton.group(1))).isEqualTo(EtatActivation.VALIDE);
		assertThrows(MotDePasseNonConformeException.class, () -> activation.activer(jeton.group(1), "Dupont-2026!x", "Dupont-2026!x"));
		assertThat(activation.etat(jeton.group(1))).isEqualTo(EtatActivation.VALIDE);
		assertThat(activation.activer(jeton.group(1), MOT_DE_PASSE, MOT_DE_PASSE)).isEqualTo(EtatActivation.ACTIVE);
		assertThat(activation.etat(jeton.group(1))).isEqualTo(EtatActivation.DEJA_ACTIVE);

		String session = connexion.connecter(identifiant.group(1), MOT_DE_PASSE).jetonSession();
		assertThat(connexion.identifiantDeLaSession(session)).contains(identifiant.group(1));

		// CU-46 : mot de passe oublié, puis faute de frappe dans la confirmation et correction
		// avec le même lien.
		String smsActivation = messagerie.dernier(Canal.SMS).orElseThrow().getTexte();
		reinitialisation.demander(identifiant.group(1));
		String sms = smsRecuApres(smsActivation);
		assertThat(sms).contains("/reinitialisation?jeton=");
		Matcher lien = JETON.matcher(sms);
		assertThat(lien.find()).isTrue();
		String jetonReinitialisation = lien.group(1);

		assertThat(reinitialisation.etat(jetonReinitialisation)).isEqualTo(EtatReinitialisation.VALIDE);
		assertThrows(MotDePasseNonConformeException.class,
				() -> reinitialisation.reinitialiser(jetonReinitialisation, NOUVEAU, NOUVEAU + "x"));
		assertThrows(MotDePasseNonConformeException.class,
				() -> reinitialisation.reinitialiser(jetonReinitialisation, NOUVEAU, ""));
		assertThat(reinitialisation.etat(jetonReinitialisation)).isEqualTo(EtatReinitialisation.VALIDE);
		assertThat(reinitialisation.reinitialiser(jetonReinitialisation, NOUVEAU, NOUVEAU))
				.isEqualTo(EtatReinitialisation.REINITIALISE);
		assertThat(reinitialisation.etat(jetonReinitialisation)).isEqualTo(EtatReinitialisation.EXPIRE);
		assertThat(base.queryForObject("select count(*) from jeton where type = 'REINITIALISATION' and utilise_le is not null",
				Integer.class)).isEqualTo(1);

		assertThat(connexion.identifiantDeLaSession(session)).isEmpty();
		assertThrows(ConnexionRefuseeException.class, () -> connexion.connecter(identifiant.group(1), MOT_DE_PASSE));
		assertThat(connexion.connecter(identifiant.group(1), NOUVEAU).identifiant()).isEqualTo(identifiant.group(1));
	}

	/** La demande est traitée en arrière-plan : on attend le nouveau SMS (5 secondes au plus). */
	private String smsRecuApres(String precedent) {
		for (int essai = 0; essai < 50; essai++) {
			String texte = messagerie.dernier(Canal.SMS).orElseThrow().getTexte();
			if (!texte.equals(precedent)) {
				return texte;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		throw new AssertionError("SMS de réinitialisation non reçu");
	}

}
