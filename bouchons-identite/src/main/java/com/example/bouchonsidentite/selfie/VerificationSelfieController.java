package com.example.bouchonsidentite.selfie;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// POST /stripe-identity/verifications-selfie {dossierId, selfie}
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/bouchons-identite/stripe-identity/verifications-selfie
@RestController
@RequestMapping("/stripe-identity/verifications-selfie")
public class VerificationSelfieController {

	private final VerificationSelfieService service;

	public VerificationSelfieController(VerificationSelfieService service) {
		this.service = service;
	}

	@PostMapping
	public VerificationSelfieReponse verifier(@RequestBody VerificationSelfieRequete requete) {
		return service.verifier(requete);
	}

}
