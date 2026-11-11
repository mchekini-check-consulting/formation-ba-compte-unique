package com.example.compteunique.virement;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.virement.VirementService.Execution;
import com.example.compteunique.virement.VirementService.Recapitulatif;

// CU-147 (US-21) : virement vers un de mes bénéficiaires (session obligatoire).
// POST /virements                  : {"beneficiaireId", "montant" (euros, « 12,50 »), "libelle"} -> récapitulatif,
//                                     code SMS envoyé (202). Rien n'est débité.
// POST /virements/{id}/signature   : {"code"} -> virement exécuté, nouveau solde.
// Refus : {"message", "termine"} avec le message exact du ticket.
@RestController
@RequestMapping("/virements")
public class VirementController {

	static final String SANS_COMPTE = "Aucun compte courant";

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final VirementService service;

	public VirementController(VirementService service) {
		this.service = service;
	}

	public record Demande(Long beneficiaireId, String montant, String libelle) {
	}

	public record Code(String code) {
	}

	public record Refus(String message, boolean termine) {
	}

	@PostMapping
	public ResponseEntity<Recapitulatif> preparer(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Demande demande) {
		Demande lue = demande == null ? new Demande(null, null, null) : demande;
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(service.preparer(jetonSession, lue.beneficiaireId(), lue.montant(), lue.libelle()));
	}

	@PostMapping("/{virementId}/signature")
	public Execution signer(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable Long virementId, @RequestBody(required = false) Code code) {
		return service.signer(jetonSession, virementId, code == null ? null : code.code());
	}

	@ExceptionHandler(DemandeRefusee.class)
	public ResponseEntity<Refus> refus(DemandeRefusee refus) {
		return ResponseEntity.status(refus.getStatut()).body(new Refus(refus.getMessage(), refus.isTermine()));
	}

	@ExceptionHandler(ClienteNonConnecteeException.class)
	public ResponseEntity<Void> nonConnectee() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(CompteInaccessibleException.class)
	public ResponseEntity<Refus> sansCompte() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(SANS_COMPTE, true));
	}

}
