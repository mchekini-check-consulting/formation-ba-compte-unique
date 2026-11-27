package com.example.compteunique.catalogue;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.catalogue.ParametresPublics.Vue;

// CU-24 (US-00) et US-13 : GET /catalogue/parametres-publics, lecture seule et sans connexion (page publique et
// espace) : {fraisUcPct: {PER, AV}, fondsEurosAv: {tauxBrutVisePct, dateMiseAJour, fraisGestionPct}}. Aucune donnée
// de cliente ; cache court (5 minutes).
@RestController
public class ParametresPublicsController {

	static final Duration CACHE = Duration.ofMinutes(5);

	private final ParametresPublics parametres;

	public ParametresPublicsController(ParametresPublics parametres) {
		this.parametres = parametres;
	}

	@GetMapping("/catalogue/parametres-publics")
	public ResponseEntity<Vue> lire() {
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE).cachePublic()).body(parametres.lire());
	}

}
