package com.example.bouchonsidentite.sanctions;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// POST /sanctions-pep/controles {nomNaissance, prenoms, dateNaissance, nationalite}
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/bouchons-identite/sanctions-pep/controles
@RestController
@RequestMapping("/sanctions-pep/controles")
public class ControleSanctionsController {

	private final ControleSanctionsService service;

	public ControleSanctionsController(ControleSanctionsService service) {
		this.service = service;
	}

	@PostMapping
	public ControleReponse controler(@RequestBody ControleRequete requete) {
		return service.controler(requete);
	}

}
