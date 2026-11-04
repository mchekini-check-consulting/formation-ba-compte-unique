package com.example.bouchonsidentite.docusign;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bouchonsidentite.docusign.DocusignRequetes.DemandeEnveloppe;
import com.example.bouchonsidentite.docusign.DocusignRequetes.ReponseDocusign;
import com.example.bouchonsidentite.docusign.DocusignRequetes.SaisieCode;

// CU-89 : bouchon Docusign.
// POST /docusign/enveloppes {dossierId}               : enveloppe créée, code SMS « envoyé »
// POST /docusign/enveloppes/{enveloppeId}/code {code} : résultat de la saisie du code
// POST /docusign/enveloppes/{enveloppeId}/annulation  : annulation demandée
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/bouchons-identite/docusign/...
@RestController
@RequestMapping("/docusign/enveloppes")
public class DocusignController {

	private final DocusignService service;

	public DocusignController(DocusignService service) {
		this.service = service;
	}

	@PostMapping
	public ReponseDocusign envoyer(@RequestBody DemandeEnveloppe demande) {
		return service.envoyerEnveloppe(demande.dossierId());
	}

	@PostMapping("/{enveloppeId}/code")
	public ReponseDocusign saisirCode(@PathVariable String enveloppeId, @RequestBody SaisieCode saisie) {
		return service.saisirCode(enveloppeId, saisie.code());
	}

	@PostMapping("/{enveloppeId}/annulation")
	public ReponseDocusign annuler(@PathVariable String enveloppeId) {
		return service.annuler(enveloppeId);
	}

}
