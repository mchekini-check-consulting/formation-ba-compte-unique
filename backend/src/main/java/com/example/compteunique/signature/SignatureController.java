package com.example.compteunique.signature;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// CU-12 : signature de la convention.
// GET  /dossiers/{reference}/signature                          : convention et état de la signature
// POST /dossiers/{reference}/signature/enveloppe {enveloppeId}  : code SMS envoyé (Docusign)
// POST /dossiers/{reference}/signature/resultat {resultat}      : résultat Docusign de la saisie ou de l'annulation
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/dossiers/...
@RestController
@RequestMapping("/dossiers/{reference}/signature")
public class SignatureController {

	private final SignatureService signatures;

	public SignatureController(SignatureService signatures) {
		this.signatures = signatures;
	}

	public record EnveloppeEnvoyee(String enveloppeId) {
	}

	public record ResultatSaisie(ResultatSignature resultat) {
	}

	@GetMapping
	public SignatureReponse etat(@PathVariable String reference) {
		return signatures.etat(reference);
	}

	@PostMapping("/enveloppe")
	public SignatureReponse enveloppe(@PathVariable String reference, @RequestBody EnveloppeEnvoyee enveloppe) {
		return signatures.enveloppeEnvoyee(reference, enveloppe.enveloppeId());
	}

	@PostMapping("/resultat")
	public SignatureReponse resultat(@PathVariable String reference, @RequestBody ResultatSaisie saisie) {
		return signatures.enregistrerResultat(reference, saisie.resultat());
	}

}
