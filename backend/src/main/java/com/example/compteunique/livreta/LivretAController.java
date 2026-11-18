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
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.livreta.IdentiteLivretAService.Identite;

// CU-31 (US-22) : ouverture d'un Livret A par la cliente connectée (session obligatoire), contrôle de
// multidétention.
// GET  /livret-a/demande                                 : {"demande"} dernière demande, null s'il n'y en a pas.
// POST /livret-a/demandes                                : {"attestation", "partageDetailAutorise"} -> demande (201).
// POST /livret-a/demandes/{reference}/reponse-fiscale    : {"interrogationId", "resultat"} -> demande.
// CU-28 (US-19) : étape « Mon identité », avant l'éligibilité.
// GET  /livret-a/identite                                : {"identite", "controle"} pré-remplie depuis le dossier.
// POST /livret-a/identite                                : {"coordonnees", "residenceFiscaleConfirmee"} -> contrôle (201).
// POST /livret-a/identite/{reference}/resultat           : {"resultat", "tentatives"} -> contrôle.
// Refus : {"message", "termine"} avec le message affiché à la cliente.
@RestController
@RequestMapping("/livret-a")
public class LivretAController {

	static final String SANS_COMPTE = "Aucun compte courant";
	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final LivretAService service;
	private final IdentiteLivretAService identites;

	public LivretAController(LivretAService service, IdentiteLivretAService identites) {
		this.service = service;
		this.identites = identites;
	}

	/** CU-28 : identité pré-remplie et dernier contrôle (null s'il n'y en a pas). */
	public record EtapeIdentite(Identite identite, ControleIdentiteVue controle) {
	}

	/** CU-28 : coordonnées (éventuellement corrigées) et confirmation de la résidence fiscale. */
	public record Confirmation(Coordonnees coordonnees, Boolean residenceFiscaleConfirmee) {
	}

	/** CU-28 : résultat du contrôle sanctions/PPE et nombre d'essais (1 à 4). */
	public record ResultatControle(ResultatSanctions resultat, Integer tentatives) {
	}

	@GetMapping("/identite")
	public EtapeIdentite identite(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return new EtapeIdentite(identites.identite(jetonSession),
				identites.dernierControle(jetonSession).map(ControleIdentiteVue::de).orElse(null));
	}

	@PostMapping("/identite")
	public ResponseEntity<ControleIdentiteVue> confirmerIdentite(
			@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Confirmation confirmation) {
		Confirmation lue = confirmation == null ? new Confirmation(null, null) : confirmation;
		return ResponseEntity.status(HttpStatus.CREATED).body(ControleIdentiteVue
				.de(identites.confirmer(jetonSession, lue.coordonnees(), lue.residenceFiscaleConfirmee())));
	}

	@PostMapping("/identite/{reference}/resultat")
	public ControleIdentiteVue resultatControle(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) ResultatControle resultat) {
		ResultatControle lu = resultat == null ? new ResultatControle(null, null) : resultat;
		return ControleIdentiteVue
				.de(identites.enregistrerResultat(jetonSession, reference, lu.resultat(), lu.tentatives()));
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

	/** Sans session : 401 sans corps ; sans compte courant : 404 ; demande refusée : son code et son message. */
	@ExceptionHandler({ DemandeRefusee.class, ClienteNonConnecteeException.class, CompteInaccessibleException.class })
	public ResponseEntity<Refus> refus(RuntimeException erreur) {
		return reponseDe(erreur);
	}

	static ResponseEntity<Refus> reponseDe(RuntimeException erreur) {
		if (erreur instanceof DemandeRefusee demande) {
			return ResponseEntity.status(demande.getStatut()).body(new Refus(demande.getMessage(), demande.isTermine()));
		}
		if (erreur instanceof CompteInaccessibleException) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(SANS_COMPTE, true));
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

}
