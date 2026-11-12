package com.example.bouchonsidentite.fiscal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bouchonsidentite.fiscal.FiscalRequetes.Interrogation;
import com.example.bouchonsidentite.fiscal.FiscalRequetes.ReponseFiscale;

// CU-295 : bouchon de l'administration fiscale (multidétention du Livret A).
// POST /administration-fiscale/interrogations {demandeId, nom, prenoms, sexe, dateNaissance, lieuNaissance,
//      partageDetailAutorise, casDeTest?} -> {interrogationId, resultat, detail, message, appeleLe}
// Critère 8 : actif seulement si app.bouchon-fiscal.actif=true (local, dev, qualif ; false en preprod et prod,
// helm.yaml). Sinon ce contrôleur n'est pas créé : toute interrogation reçoit 404.
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/bouchons-identite/administration-fiscale/...
@RestController
@RequestMapping("/administration-fiscale")
@ConditionalOnProperty(name = "app.bouchon-fiscal.actif", havingValue = "true")
public class AdministrationFiscaleController {

	private final AdministrationFiscaleService service;

	public AdministrationFiscaleController(AdministrationFiscaleService service) {
		this.service = service;
	}

	@PostMapping("/interrogations")
	public ReponseFiscale interroger(@RequestBody(required = false) Interrogation demande) {
		return service.interroger(demande);
	}

}
