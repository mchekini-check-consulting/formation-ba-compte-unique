package com.example.compteunique.dossier;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Dossier d'ouverture de compte (CUB-8). La référence est générée par l'écran au début du parcours.
// PUT /dossiers/{reference}/identite  : enregistre l'identité saisie (statut « En cours de vérification »)
// PUT /dossiers/{reference}/controles : enregistre le résultat des contrôles sanctions et PEP
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/dossiers/...
@RestController
@RequestMapping("/dossiers/{reference}")
public class DossierController {

	private final DossierService dossiers;

	public DossierController(DossierService dossiers) {
		this.dossiers = dossiers;
	}

	@PutMapping("/identite")
	public DossierReponse renseignerIdentite(@PathVariable String reference, @RequestBody IdentiteRequete identite) {
		return dossiers.renseignerIdentite(reference, identite);
	}

	@PutMapping("/controles")
	public DossierReponse enregistrerControles(@PathVariable String reference,
			@RequestBody ControlesRequete controles) {
		return dossiers.enregistrerControles(reference, controles);
	}

}
