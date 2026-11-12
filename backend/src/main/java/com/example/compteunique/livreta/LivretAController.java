package com.example.compteunique.livreta;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;

// CU-31 (US-22) : ouverture d'un Livret A par la cliente connectée (session obligatoire), contrôle de
// multidétention.
// GET  /livret-a/demande                                 : {"demande"} dernière demande, null s'il n'y en a pas.
// POST /livret-a/demandes                                : {"attestation", "partageDetailAutorise"} -> demande (201).
// POST /livret-a/demandes/{reference}/reponse-fiscale    : {"interrogationId", "resultat"} -> demande.
// Refus : {"message", "termine"} avec le message affiché à la cliente.
@RestController
@RequestMapping("/livret-a")
public class LivretAController {

	static final String SANS_COMPTE = "Aucun compte courant";
	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final LivretAService service;

	public LivretAController(LivretAService service) {
		this.service = service;
	}

	public record Etat(DemandeLivretAVue demande) {
	}

	public record Eligibilite(Boolean attestation, Boolean partageDetailAutorise) {
	}

	public record ResultatInterrogation(String interrogationId, ReponseFiscale resultat) {
	}

	public record Refus(String message, boolean termine) {
	}

	@GetMapping("/demande")
	public Etat etat(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return new Etat(service.etat(jetonSession).map(DemandeLivretAVue::de).orElse(null));
	}

	@PostMapping("/demandes")
	public ResponseEntity<DemandeLivretAVue> demander(
			@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Eligibilite eligibilite) {
		Eligibilite lue = eligibilite == null ? new Eligibilite(null, null) : eligibilite;
		return ResponseEntity.status(HttpStatus.CREATED).body(DemandeLivretAVue
				.de(service.demander(jetonSession, lue.attestation(), lue.partageDetailAutorise())));
	}

	@PostMapping("/demandes/{reference}/reponse-fiscale")
	public DemandeLivretAVue reponseFiscale(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) ResultatInterrogation resultat) {
		ResultatInterrogation lu = resultat == null ? new ResultatInterrogation(null, null) : resultat;
		return DemandeLivretAVue
				.de(service.enregistrerReponse(jetonSession, reference, lu.interrogationId(), lu.resultat()));
	}

	@ExceptionHandler(DemandeRefusee.class)
	public ResponseEntity<Refus> refus(DemandeRefusee refus) {
		return ResponseEntity.status(refus.getStatut()).body(new Refus(refus.getMessage(), refus.isTermine()));
	}

	@ExceptionHandler(ClienteNonConnecteeException.class)
	public ResponseEntity<Void> nonConnectee() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(CompteInaccessibleException.class)
	public ResponseEntity<Refus> sansCompte() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(SANS_COMPTE, true));
	}

}
