package com.example.compteunique.souscription;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.catalogue.Adequation.SupportInadapte;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.souscription.SouscriptionPer.Etat;

// CU-312 (PER-04) : parcours de souscription du PER de la cliente connectée (session obligatoire).
// GET /souscriptions/per : état ; POST /versement {montant, origineFonds, originePrecision} ; POST /code : envoi du
// code SMS ; POST /signature {code} : contrat créé et premier versement placé. Refus : {message}.
@RestController
@RequestMapping("/souscriptions/per")
public class SouscriptionController {

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final SouscriptionPer service;

	public SouscriptionController(SouscriptionPer service) {
		this.service = service;
	}

	public record DemandeVersement(BigDecimal montant, String origineFonds, String originePrecision) {
	}

	public record DemandeSignature(String code) {
	}

	public record Refus(String message) {
	}

	@GetMapping
	public Etat etat(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return service.etat(jetonSession);
	}

	@PostMapping("/versement")
	public Etat versement(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) DemandeVersement demande) {
		DemandeVersement d = demande != null ? demande : new DemandeVersement(null, null, null);
		return service.choisirVersement(jetonSession, d.montant(), d.origineFonds(), d.originePrecision());
	}

	@PostMapping("/code")
	public Etat code(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return service.envoyerCode(jetonSession);
	}

	@PostMapping("/signature")
	public Etat signature(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) DemandeSignature demande) {
		return service.signer(jetonSession, demande == null ? null : demande.code());
	}

	@ExceptionHandler(DemandeRefusee.class)
	public ResponseEntity<Refus> refus(DemandeRefusee refus) {
		return ResponseEntity.status(refus.getStatut()).body(new Refus(refus.getMessage()));
	}

	/** CU-349 : un support de l'allocation ne correspond plus au profil ; rien n'est débité ni créé. */
	@ExceptionHandler(SupportInadapte.class)
	public ResponseEntity<Refus> inadapte(SupportInadapte refus) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler(ClienteNonConnecteeException.class)
	public ResponseEntity<Void> nonConnectee() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(CompteInaccessibleException.class)
	public ResponseEntity<Refus> sansCompte() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus("Aucun compte courant"));
	}

}
