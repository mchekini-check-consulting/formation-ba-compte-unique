package com.example.bouchonsidentite.document;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// POST /stripe-identity/verifications-document {dossierId, recto, verso, saisie}
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/bouchons-identite/stripe-identity/verifications-document
@RestController
@RequestMapping("/stripe-identity/verifications-document")
public class VerificationDocumentController {

	private final VerificationDocumentService service;

	public VerificationDocumentController(VerificationDocumentService service) {
		this.service = service;
	}

	@PostMapping
	public VerificationDocumentReponse verifier(@RequestBody VerificationDocumentRequete requete) {
		return service.verifier(requete);
	}

}
