package com.example.compteunique.beneficiaire;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.VerificationBeneficiaire.CompteExterne;
import com.example.compteunique.virement.IbanBeneficiaire;

// CU-145 (TECH-06) : page de test du bouchon « Vérification du Bénéficiaire (VoP) », en environnement de test
// seulement (app.test.virements-simules, comme les bouchons de virement CU-143 et CU-144). Hors test, ce contrôleur
// n'est pas créé : aucun point d'accès, toute demande reçoit 404.
// GET  /test/verification-beneficiaire              : sélecteur et journal de test (50 derniers appels).
// PUT  /test/verification-beneficiaire/indisponible : {"indisponible": true|false}, sélecteur « Service indisponible ».
// POST /test/verification-beneficiaire              : {"iban", "nom"} -> résultat de la vérification.
@RestController
@RequestMapping("/test/verification-beneficiaire")
@ConditionalOnProperty(name = "app.test.virements-simules", havingValue = "true")
public class VerificationTestController {

	static final String IBAN_INVALIDE = "IBAN invalide";

	private final VerificationBeneficiaire verification;

	public VerificationTestController(VerificationBeneficiaire verification) {
		this.verification = verification;
	}

	public record Demande(String iban, String nom) {
	}

	public record Selecteur(boolean indisponible) {
	}

	public record Appel(OffsetDateTime appeleLe, String iban, ResultatVerification resultat) {

		static Appel de(AppelVerification appel) {
			return new Appel(appel.getAppeleLe(), appel.getIban(), appel.getResultat());
		}

	}

	/** comptesExternes : comptes d'autres banques connus du bouchon, à utiliser pour ajouter un bénéficiaire. */
	public record Etat(boolean indisponible, List<Appel> appels, List<CompteExterne> comptesExternes) {
	}

	public record Refus(String message) {
	}

	@GetMapping
	public Etat etat() {
		return new Etat(verification.estIndisponible(), verification.journal().stream().map(Appel::de).toList(),
				VerificationBeneficiaire.COMPTES_EXTERNES);
	}

	@PutMapping("/indisponible")
	public Etat selecteur(@RequestBody(required = false) Selecteur selecteur) {
		verification.rendreIndisponible(selecteur != null && selecteur.indisponible());
		return etat();
	}

	@PostMapping
	public ResponseEntity<Object> verifier(@RequestBody(required = false) Demande demande) {
		String iban = IbanBeneficiaire.normaliser(demande == null ? null : demande.iban());
		if (!IbanBeneficiaire.estValide(iban)) {
			return ResponseEntity.badRequest().body(new Refus(IBAN_INVALIDE));
		}
		return ResponseEntity.ok(verification.verifier(iban, demande.nom()));
	}

}
