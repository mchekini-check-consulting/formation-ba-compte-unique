package com.example.compteunique.fonctionnalites;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Lot 3 : GET /fonctionnalites, lecture seule et sans connexion : {"messagerie": false, ..., "outils-test": true}.
// Seulement le nom et l'état de chaque interrupteur, aucune donnée de cliente.
@RestController
public class FonctionnalitesController {

	private final Fonctionnalites fonctionnalites;

	public FonctionnalitesController(Fonctionnalites fonctionnalites) {
		this.fonctionnalites = fonctionnalites;
	}

	@GetMapping("/fonctionnalites")
	public Map<String, Boolean> etats() {
		return fonctionnalites.etats();
	}

}
