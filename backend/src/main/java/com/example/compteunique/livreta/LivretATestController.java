package com.example.compteunique.livreta;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.livreta.LivretAController.Refus;
import com.example.compteunique.livreta.LivretAService.Suivi;

// CU-295 critère 5 : « Simuler l'expiration du délai », en environnement de test seulement
// (app.test.livret-a-simule ; false en preprod et prod, helm.yaml : 404).
// POST /test/livret-a/demandes/{reference}/expiration-delai : demande transmise et sans réponse -> conseiller
// alerté, comme au bout de 2 jours ouvrés.
// POST /test/livret-a/identite/{reference}/validation-responsable : CU-28, PPE validée par un responsable.
// CU-296 (pas encore de back-office conseiller) :
// POST /test/livret-a/demandes/{reference}/certificat/validation : certificat validé, l'ouverture reprend.
// POST /test/livret-a/demandes/{reference}/certificat/refus      : {"motif"} certificat refusé, nouveau dépôt possible.
// L'expiration de la date limite se teste en avançant la date métier (CU-380, /test/date-metier).
@RestController
@RequestMapping("/test/livret-a")
@ConditionalOnProperty(name = "app.test.livret-a-simule", havingValue = "true")
public class LivretATestController {

	private final LivretAService service;
	private final IdentiteLivretAService identites;
	private final ClotureLivretAService clotures;

	public LivretATestController(LivretAService service, IdentiteLivretAService identites,
			ClotureLivretAService clotures) {
		this.service = service;
		this.identites = identites;
		this.clotures = clotures;
	}

	/** CU-296 : motif saisi par la testeuse pour un refus simulé. */
	public record RefusCertificat(String motif) {
	}

	/** CU-296 : « Simuler la validation du certificat par un conseiller » ; l'ouverture reprend. */
	@PostMapping("/demandes/{reference}/certificat/validation")
	public DemandeLivretAVue validerCertificat(@PathVariable String reference) {
		return DemandeLivretAVue.de(service.suivi(clotures.simulerValidation(reference)));
	}

	/** CU-296 : « Simuler le refus du certificat par un conseiller », avec un motif. */
	@PostMapping("/demandes/{reference}/certificat/refus")
	public DemandeLivretAVue refuserCertificat(@PathVariable String reference,
			@RequestBody(required = false) RefusCertificat refus) {
		return DemandeLivretAVue
				.de(service.suivi(clotures.simulerRefus(reference, refus == null ? null : refus.motif())));
	}

	@PostMapping("/demandes/{reference}/expiration-delai")
	public DemandeLivretAVue expirerDelai(@PathVariable String reference) {
		return DemandeLivretAVue.de(new Suivi(service.simulerExpirationDelai(reference), null));
	}

	/** CU-28 critère 9 (test) : « Simuler la validation du responsable » pour une personne politiquement exposée. */
	@PostMapping("/identite/{reference}/validation-responsable")
	public ControleIdentiteVue validerParResponsable(@PathVariable String reference) {
		return ControleIdentiteVue
				.de(new IdentiteLivretAService.Suivi(identites.validerParResponsable(reference), null));
	}

	@ExceptionHandler(DemandeRefusee.class)
	public ResponseEntity<Refus> refus(DemandeRefusee refus) {
		return LivretAController.reponseDe(refus);
	}

}
