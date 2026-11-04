package com.example.compteunique.ouverture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.messagerie.Canal;

// CU-13 : ouverture du compte courant.
// GET  /dossiers/{reference}/ouverture          : écran de confirmation
// POST /dossiers/{reference}/ouverture/relance  : relance manuelle d'un dossier « Création à relancer »
// POST /dossiers/{reference}/envois/{canal}     : renvoi de l'identifiant (EMAIL) ou du lien (SMS)
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/dossiers/...
@RestController
@RequestMapping("/dossiers/{reference}")
public class OuvertureController {

	private final OuvertureService ouverture;

	public OuvertureController(OuvertureService ouverture) {
		this.ouverture = ouverture;
	}

	@GetMapping("/ouverture")
	public OuvertureReponse etat(@PathVariable String reference) {
		return ouverture.etat(reference);
	}

	@PostMapping("/ouverture/relance")
	public OuvertureReponse relancer(@PathVariable String reference) {
		return ouverture.relancer(reference);
	}

	@PostMapping("/envois/{canal}")
	public OuvertureReponse renvoyer(@PathVariable String reference, @PathVariable Canal canal) {
		return ouverture.renvoyer(reference, canal);
	}

}
