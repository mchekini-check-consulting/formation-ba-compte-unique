package com.example.compteunique.virement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.virement.VirementService.Execution;

// CU-147 critère 14 : bouchon « Simuler un refus de la banque destinataire », en environnement de test seulement
// (app.test.virements-simules, comme CU-143 et CU-144). Hors test, ce contrôleur n'est pas créé : 404.
// POST /test/virements/{id}/refus {"motif"} : virement exécuté -> recrédité, statut « refusé » avec le motif.
@RestController
@RequestMapping("/test/virements")
@ConditionalOnProperty(name = "app.test.virements-simules", havingValue = "true")
public class RefusBanqueTestController {

	private final VirementService service;

	public RefusBanqueTestController(VirementService service) {
		this.service = service;
	}

	public record Motif(String motif) {
	}

	public record Refus(String message) {
	}

	@PostMapping("/{virementId}/refus")
	public Execution refuser(@PathVariable Long virementId, @RequestBody(required = false) Motif motif) {
		return service.refuserParBanque(virementId, motif == null ? null : motif.motif());
	}

	@ExceptionHandler(DemandeRefusee.class)
	public ResponseEntity<Refus> refus(DemandeRefusee refus) {
		return ResponseEntity.status(refus.getStatut()).body(new Refus(refus.getMessage()));
	}

}
