package com.example.compteunique.souscription;

import java.util.Map;

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
import com.example.compteunique.catalogue.Adequation.SupportInadapte;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.souscription.SouscriptionService.DemandeVersement;
import com.example.compteunique.souscription.SouscriptionService.Etat;

// CU-312 (PER-04) et CU-330 (AV-01) : parcours de souscription de la cliente connectée (session obligatoire), produit
// « per » ou « av ». GET /souscriptions/{produit} : état ; POST /versement {montant, origineFonds, originePrecision,
// beneficiaires} ; POST /code : envoi du code SMS ; POST /signature {code} : contrat créé et premier versement placé.
// Refus : {message}.
@RestController
@RequestMapping("/souscriptions/{produit}")
public class SouscriptionController {

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;
	private static final Map<String, Produit> PRODUITS = Map.of("per", Produit.PER, "av", Produit.AV);

	private final SouscriptionService service;

	public SouscriptionController(SouscriptionService service) {
		this.service = service;
	}

	public record DemandeSignature(String code) {
	}

	public record Refus(String message) {
	}

	@GetMapping
	public Etat etat(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable("produit") String produit) {
		return service.etat(jetonSession, produit(produit));
	}

	@PostMapping("/versement")
	public Etat versement(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable("produit") String produit, @RequestBody(required = false) DemandeVersement demande) {
		return service.choisirVersement(jetonSession, produit(produit),
				demande != null ? demande : new DemandeVersement(null, null, null, null));
	}

	@PostMapping("/code")
	public Etat code(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable("produit") String produit) {
		return service.envoyerCode(jetonSession, produit(produit));
	}

	@PostMapping("/signature")
	public Etat signature(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable("produit") String produit, @RequestBody(required = false) DemandeSignature demande) {
		return service.signer(jetonSession, produit(produit), demande == null ? null : demande.code());
	}

	private static Produit produit(String produit) {
		Produit connu = PRODUITS.get(produit);
		if (connu == null) {
			throw new DemandeRefusee(HttpStatus.NOT_FOUND, "Produit inconnu : per ou av attendu.");
		}
		return connu;
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
