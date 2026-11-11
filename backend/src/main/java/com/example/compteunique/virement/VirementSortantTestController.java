package com.example.compteunique.virement;

import java.math.BigDecimal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.compte.CompteNonActifException;
import com.example.compteunique.compte.MontantInvalideException;
import com.example.compteunique.compte.SoldeInsuffisantException;

// CU-144 (TECH-05) : bouchon « Simuler un virement sortant », en environnement de test seulement, avec le même
// interrupteur que le virement entrant (app.test.virements-simules, VIREMENTS_SIMULES dans helm.yaml : false en
// preprod et prod). Hors test, ce contrôleur n'est pas créé : aucun point d'accès, toute demande reçoit 404.
// POST /test/virements-sortants {"iban", "montantCentimes", "beneficiaire", "ibanBeneficiaire", "libelle"} :
// débite le compte d'IBAN iban, mouvement marqué « simulé ». Aucun virement réel.
@RestController
@RequestMapping("/test/virements-sortants")
@ConditionalOnProperty(name = "app.test.virements-simules", havingValue = "true")
public class VirementSortantTestController {

	static final String VIREMENT_EMIS = "Virement émis : le solde du compte a diminué";
	static final String MONTANT_INVALIDE = "Montant invalide";
	static final String IBAN_INVALIDE = "IBAN invalide";
	static final String SOLDE_INSUFFISANT = "Solde insuffisant";
	static final String COMPTE_CLOTURE = "Compte clôturé";
	static final String COMPTE_INTROUVABLE = "Compte introuvable";
	static final String INFORMATIONS_INCOMPLETES = "Le nom du bénéficiaire (" + VirementSortant.BENEFICIAIRE_MAX
			+ " caractères au plus) et le libellé (" + VirementSortant.LIBELLE_MAX
			+ " caractères au plus) sont obligatoires";

	private final EmissionVirement emission;

	public VirementSortantTestController(EmissionVirement emission) {
		this.emission = emission;
	}

	/** montantCentimes : nombre entier de centimes (1250 = 12,50 €). iban : compte débité. */
	public record Demande(String iban, BigDecimal montantCentimes, String beneficiaire, String ibanBeneficiaire,
			String libelle) {

		long enCentimes() {
			if (montantCentimes == null) {
				return 0;
			}
			try {
				return montantCentimes.longValueExact();
			} catch (ArithmeticException nonEntierOuTropGrand) {
				return 0;
			}
		}

	}

	/** soldeCentimes : nouveau solde du compte débité. */
	public record Reponse(String message, long soldeCentimes) {
	}

	public record Refus(String message) {
	}

	@PostMapping
	public ResponseEntity<Reponse> simuler(@RequestBody(required = false) Demande demande) {
		Demande lue = demande == null ? new Demande(null, null, null, null, null) : demande;
		long solde = emission.emettre(new VirementSortant(lue.iban(), lue.enCentimes(), lue.beneficiaire(),
				lue.ibanBeneficiaire(), lue.libelle(), true));
		return ResponseEntity.status(HttpStatus.CREATED).body(new Reponse(VIREMENT_EMIS, solde));
	}

	@ExceptionHandler(MontantInvalideException.class)
	public ResponseEntity<Refus> montantInvalide() {
		return ResponseEntity.badRequest().body(new Refus(MONTANT_INVALIDE));
	}

	@ExceptionHandler(IbanInvalideException.class)
	public ResponseEntity<Refus> ibanInvalide() {
		return ResponseEntity.badRequest().body(new Refus(IBAN_INVALIDE));
	}

	@ExceptionHandler(InformationsVirementIncompletesException.class)
	public ResponseEntity<Refus> informationsIncompletes() {
		return ResponseEntity.badRequest().body(new Refus(INFORMATIONS_INCOMPLETES));
	}

	@ExceptionHandler(CompteIntrouvableException.class)
	public ResponseEntity<Refus> compteIntrouvable() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(COMPTE_INTROUVABLE));
	}

	@ExceptionHandler(CompteNonActifException.class)
	public ResponseEntity<Refus> compteCloture() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new Refus(COMPTE_CLOTURE));
	}

	@ExceptionHandler(SoldeInsuffisantException.class)
	public ResponseEntity<Refus> soldeInsuffisant() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new Refus(SOLDE_INSUFFISANT));
	}

}
