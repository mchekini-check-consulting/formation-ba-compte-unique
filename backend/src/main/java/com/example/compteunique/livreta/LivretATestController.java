package com.example.compteunique.livreta;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.livreta.LivretAController.Refus;
import com.example.compteunique.livreta.LivretAService.Suivi;

// CU-295 critère 5 : « Simuler l'expiration du délai », en environnement de test seulement
// (app.test.livret-a-simule ; false en preprod et prod, helm.yaml : 404).
// POST /test/livret-a/demandes/{reference}/expiration-delai : demande transmise et sans réponse -> conseiller
// alerté, comme au bout de 2 jours ouvrés.
@RestController
@RequestMapping("/test/livret-a")
@ConditionalOnProperty(name = "app.test.livret-a-simule", havingValue = "true")
public class LivretATestController {

	private final LivretAService service;

	public LivretATestController(LivretAService service) {
		this.service = service;
	}

	@PostMapping("/demandes/{reference}/expiration-delai")
	public DemandeLivretAVue expirerDelai(@PathVariable String reference) {
		return DemandeLivretAVue.de(new Suivi(service.simulerExpirationDelai(reference), null));
	}

	@ExceptionHandler(DemandeRefusee.class)
	public ResponseEntity<Refus> refus(DemandeRefusee refus) {
		return LivretAController.reponseDe(refus);
	}

}
