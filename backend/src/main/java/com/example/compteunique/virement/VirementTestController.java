package com.example.compteunique.virement;

import java.math.BigDecimal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.compte.MontantInvalideException;
import com.example.compteunique.virement.ReceptionVirement.Resultat;

// CU-143 (TECH-04) : bouchon « Simuler un virement entrant », en environnement de test seulement
// (app.test.virements-simules, VIREMENTS_SIMULES dans helm.yaml : false en preprod et prod). Hors test, ce
// contrôleur n'est pas créé : aucun point d'accès, toute demande reçoit 404.
// GET  /test/virements-entrants : 204 si le bouchon est disponible.
// POST /test/virements-entrants {"iban", "montantCentimes", "emetteur", "libelle"} : traité comme un virement
// reçu, par le même chemin que CU-11 (ReceptionVirement), marqué « simulé ». Aucun virement réel.
@RestController
@RequestMapping("/test/virements-entrants")
@ConditionalOnProperty(name = "app.test.virements-simules", havingValue = "true")
public class VirementTestController {

	static final String MONTANT_INVALIDE = "Montant invalide";
	static final String COMPTE_INTROUVABLE = "Compte introuvable";
	static final String INFORMATIONS_INCOMPLETES = "Le nom de l’émetteur (" + VirementEntrant.EMETTEUR_MAX
			+ " caractères au plus) et le libellé (" + VirementEntrant.LIBELLE_MAX
			+ " caractères au plus) sont obligatoires";
	static final String VIREMENT_RECU = "Virement reçu : le solde du compte a augmenté";
	static final String VIREMENT_RENVOYE = "Virement renvoyé à l’émetteur : compte clôturé (AC04)";

	private final ReceptionVirement reception;

	public VirementTestController(ReceptionVirement reception) {
		this.reception = reception;
	}

	/** montantCentimes : nombre entier de centimes (1250 = 12,50 €). */
	public record Demande(String iban, BigDecimal montantCentimes, String emetteur, String libelle) {

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

	public record Reponse(Resultat resultat, String message) {
	}

	public record Refus(String message) {
	}

	@GetMapping
	public ResponseEntity<Void> disponible() {
		return ResponseEntity.noContent().build();
	}

	@PostMapping
	public ResponseEntity<Reponse> simuler(@RequestBody(required = false) Demande demande) {
		Demande lue = demande == null ? new Demande(null, null, null, null) : demande;
		Resultat resultat = reception.recevoir(
				new VirementEntrant(lue.iban(), lue.enCentimes(), lue.emetteur(), lue.libelle(), true));
		return resultat == Resultat.CREDITE
				? ResponseEntity.status(HttpStatus.CREATED).body(new Reponse(resultat, VIREMENT_RECU))
				: ResponseEntity.ok(new Reponse(resultat, VIREMENT_RENVOYE));
	}

	@ExceptionHandler(MontantInvalideException.class)
	public ResponseEntity<Refus> montantInvalide() {
		return ResponseEntity.badRequest().body(new Refus(MONTANT_INVALIDE));
	}

	@ExceptionHandler(InformationsVirementIncompletesException.class)
	public ResponseEntity<Refus> informationsIncompletes() {
		return ResponseEntity.badRequest().body(new Refus(INFORMATIONS_INCOMPLETES));
	}

	@ExceptionHandler(CompteIntrouvableException.class)
	public ResponseEntity<Refus> compteIntrouvable() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(COMPTE_INTROUVABLE));
	}

}
