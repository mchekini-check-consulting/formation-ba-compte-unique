package com.example.compteunique.beneficiaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.beneficiaire.BeneficiaireController.BeneficiaireVue;
import com.example.compteunique.beneficiaire.BeneficiaireController.Code;
import com.example.compteunique.beneficiaire.BeneficiaireController.DemandeAjout;
import com.example.compteunique.beneficiaire.BeneficiaireService.AjoutEnAttente;
import com.example.compteunique.compte.CompteController;
import com.example.compteunique.compte.CompteController.MouvementVue;
import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.messagerie.Canal;
import com.example.compteunique.messagerie.Messagerie;
import com.example.compteunique.virement.RefusBanqueTestController;
import com.example.compteunique.virement.VirementController;
import com.example.compteunique.virement.VirementController.Demande;
import com.example.compteunique.virement.VirementService.Execution;
import com.example.compteunique.virement.VirementService.Recapitulatif;

// CU-145 -> CU-146 -> CU-147 contre un vrai PostgreSQL (migrations V19 à V21, comptes de test de db/demo) :
// vérification du nom, ajout d'un bénéficiaire avec le code SMS capturé (CU-87), virement signé, deux virements
// simultanés, refus de la banque destinataire, suppression du bénéficiaire et isolement entre clientes.
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@Testcontainers
class BeneficiairesEtVirementsIT {

	private static final String MOT_DE_PASSE = "Demo-Connexion1!";
	private static final String IBAN_A = "FR7699999000019900000000105";
	private static final String IBAN_B = "FR7699999000019900000000202";
	private static final Pattern CODE = Pattern.compile("(\\d{6})");

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ConnexionService connexion;

	@Autowired
	private VerificationBeneficiaire verification;

	@Autowired
	private BeneficiaireController beneficiaires;

	@Autowired
	private VirementController virements;

	@Autowired
	private RefusBanqueTestController banque;

	@Autowired
	private CompteController comptes;

	@Autowired
	private Messagerie messagerie;

	@Autowired
	private JdbcTemplate base;

	private String jeton(String identifiant) {
		return connexion.connecter(identifiant, MOT_DE_PASSE, null).jetonSession();
	}

	private String dernierCodeSms() {
		Matcher code = CODE.matcher(messagerie.dernier(Canal.SMS).orElseThrow().getTexte());
		assertThat(code.find()).isTrue();
		return code.group(1);
	}

	private long solde(String iban) {
		return base.queryForObject("select solde_centimes from compte where iban = ?", Long.class, iban);
	}

	private List<MouvementVue> mouvements(String jeton, String iban) {
		Long id = base.queryForObject("select id from compte where iban = ?", Long.class, iban);
		return comptes.versements(jeton, id.toString()).mouvements();
	}

	@Test
	void unBeneficiaireDUneAutreBanqueConnuDuBouchonRecoitUnVirement() {
		String clienteA = jeton("TEST-CLIENTE-A");
		AjoutEnAttente ajout = beneficiaires
				.ajouter(clienteA, new DemandeAjout("DE89 3704 0044 0532 0130 00", "jean martin", "Jean")).getBody();
		BeneficiaireVue jean = beneficiaires.confirmer(clienteA, ajout.ajoutId(), new Code(dernierCodeSms())).getBody();
		assertEquals("Jean Martin", jean.nomVerifie());

		long avant = solde(IBAN_A);
		Recapitulatif recap = virements.preparer(clienteA, new Demande(jean.id(), "10", "Cadeau")).getBody();
		virements.signer(clienteA, recap.virementId(), new VirementController.Code(dernierCodeSms()));

		assertEquals(avant - 1000, solde(IBAN_A));
		assertThat(mouvements(clienteA, IBAN_A)).anyMatch(m -> "Jean Martin".equals(m.beneficiaire())
				&& "•••• 3000".equals(m.ibanMasque()) && "exécuté".equals(m.statut()));
		DemandeRefusee inconnu = assertThrows(DemandeRefusee.class, () -> beneficiaires.ajouter(clienteA,
				new DemandeAjout("GB82WEST12345698765432", "Emma Wilson", "Emma")));
		assertEquals("Ce compte n’a pas pu être trouvé. Vérifiez l’IBAN.", inconnu.getMessage());
	}

	@Test
	void leParcoursCompletDuBeneficiaireAuVirement() throws Exception {
		// CU-145 : le bouchon répond à partir des comptes de test et trace l'appel.
		assertEquals(ResultatVerification.CORRESPOND, verification.verifier(IBAN_B, "test cliente b").resultat());
		assertEquals(ResultatVerification.NE_CORRESPOND_PAS, verification.verifier(IBAN_B, "Autre").resultat());
		assertThat(verification.journal()).hasSizeGreaterThanOrEqualTo(2);

		// CU-146 : ajout avec le code SMS, liste avec IBAN masqué, isolement entre clientes.
		String clienteA = jeton("TEST-CLIENTE-A");
		String clienteB = jeton("TEST-CLIENTE-B");
		AjoutEnAttente ajout = beneficiaires
				.ajouter(clienteA, new DemandeAjout("fr76 9999 9000 0199 0000 0000 202", "Test Cliente-B", "Papa"))
				.getBody();
		assertEquals("Test Cliente-B", ajout.nomVerifie());
		BeneficiaireVue papa = beneficiaires.confirmer(clienteA, ajout.ajoutId(), new Code(dernierCodeSms())).getBody();
		assertEquals("•••• 0202", papa.ibanMasque());
		assertThat(beneficiaires.lister(clienteA)).extracting(BeneficiaireVue::id).contains(papa.id());
		assertThat(beneficiaires.lister(clienteB)).isEmpty();
		DemandeRefusee autreCliente = assertThrows(DemandeRefusee.class,
				() -> beneficiaires.renommer(clienteB, papa.id(), new BeneficiaireController.Libelle("x")));
		assertEquals(HttpStatus.NOT_FOUND, autreCliente.getStatut());
		DemandeRefusee doublon = assertThrows(DemandeRefusee.class, () -> beneficiaires.ajouter(clienteA,
				new DemandeAjout(IBAN_B, "Test Cliente-B", "Encore")));
		assertEquals("Ce bénéficiaire existe déjà", doublon.getMessage());

		// CU-147 : premier virement limité à 500 €, puis virement signé : solde et mouvement « exécuté ».
		DemandeRefusee premier = assertThrows(DemandeRefusee.class,
				() -> virements.preparer(clienteA, new Demande(papa.id(), "500,01", "Loyer")));
		assertEquals("Premier virement vers ce bénéficiaire : montant limité à 500 €", premier.getMessage());
		long avant = solde(IBAN_A);
		Recapitulatif recap = virements.preparer(clienteA, new Demande(papa.id(), "12,50", "Loyer")).getBody();
		assertEquals(avant, solde(IBAN_A));
		Execution execution = virements.signer(clienteA, recap.virementId(),
				new VirementController.Code(dernierCodeSms()));
		assertEquals(avant - 1250, execution.soldeCentimes());
		assertEquals(avant - 1250, solde(IBAN_A));
		MouvementVue debit = mouvements(clienteA, IBAN_A).get(0);
		assertThat(debit.sortant()).isTrue();
		assertEquals("exécuté", debit.statut());
		assertEquals("Test Cliente-B", debit.beneficiaire());
		assertEquals("•••• 0202", debit.ibanMasque());
		assertEquals("Loyer", debit.libelle());

		// Critère 13 : deux virements signés en même temps dont la somme dépasse le solde : un seul passe.
		long moitieEtPlus = solde(IBAN_A) / 2 + 100;
		String montant = moitieEtPlus / 100 + "," + String.format("%02d", moitieEtPlus % 100);
		Recapitulatif premierVirement = virements.preparer(clienteA, new Demande(papa.id(), montant, "Un")).getBody();
		String premierCode = dernierCodeSms();
		Recapitulatif secondVirement = virements.preparer(clienteA, new Demande(papa.id(), montant, "Deux")).getBody();
		String secondCode = dernierCodeSms();
		ExecutorService taches = Executors.newFixedThreadPool(2);
		List<Future<Execution>> resultats = taches.invokeAll(List.<Callable<Execution>>of(
				() -> virements.signer(clienteA, premierVirement.virementId(), new VirementController.Code(premierCode)),
				() -> virements.signer(clienteA, secondVirement.virementId(), new VirementController.Code(secondCode))));
		taches.shutdown();
		int executes = 0;
		for (Future<Execution> resultat : resultats) {
			try {
				resultat.get();
				executes++;
			} catch (ExecutionException refus) {
				assertThat(refus.getCause()).isInstanceOf(DemandeRefusee.class).hasMessage("Solde insuffisant");
			}
		}
		assertEquals(1, executes);
		assertThat(solde(IBAN_A)).isNotNegative();

		// Critère 14 : refus de la banque destinataire -> recrédit, statut « refusé » et motif.
		long avantRefus = solde(IBAN_A);
		banque.refuser(recap.virementId(), new RefusBanqueTestController.Motif("Compte du bénéficiaire clôturé"));
		assertEquals(avantRefus + 1250, solde(IBAN_A));
		MouvementVue recredit = mouvements(clienteA, IBAN_A).get(0);
		assertThat(recredit.recredit()).isTrue();
		assertEquals("refusé", recredit.statut());
		assertEquals("Compte du bénéficiaire clôturé", recredit.motif());

		// CU-146 critères 15 et 16, CU-147 critère 16 : bénéficiaire supprimé, historique conservé.
		beneficiaires.supprimer(clienteA, papa.id());
		assertThat(beneficiaires.lister(clienteA)).extracting(BeneficiaireVue::id).doesNotContain(papa.id());
		DemandeRefusee dejaSupprime = assertThrows(DemandeRefusee.class,
				() -> beneficiaires.supprimer(clienteA, papa.id()));
		assertEquals("Bénéficiaire introuvable", dejaSupprime.getMessage());
		assertThrows(DemandeRefusee.class,
				() -> virements.preparer(clienteA, new Demande(papa.id(), "1", "Après suppression")));
		assertThat(mouvements(clienteA, IBAN_A)).anyMatch(m -> "Test Cliente-B".equals(m.beneficiaire()));
	}

}
