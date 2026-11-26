package com.example.compteunique.decouverte;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.decouverte.DocumentsProduits.DocumentNonOuvert;
import com.example.compteunique.decouverte.DocumentsProduits.Document;
import com.example.compteunique.decouverte.DocumentsProduits.Etat;
import com.example.compteunique.decouverte.DocumentsProduits.Produit;

// CU-311 (PER-02) et CU-331 (AV-02) : documents à lire avant de souscrire (session obligatoire).
// GET  /decouverte?produit=PER|AV  : {produit, documents: [{code, titre, version, contenu, ouvert, lu}], peutContinuer}
// POST /decouverte/ouvertures      : {produit, document} -> ouverture enregistrée, nouvel état
// POST /decouverte/lectures        : {produit, document} -> case cochée, nouvel état ; 409 si le document n'est pas ouvert
@RestController
@RequestMapping("/decouverte")
public class DecouverteController {

	static final String OUVRIR_D_ABORD = "Ouvrez le document avant de cocher la case.";

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final DocumentsProduits service;

	public DecouverteController(DocumentsProduits service) {
		this.service = service;
	}

	public record Demande(Produit produit, Document document) {
	}

	public record Refus(String message) {
	}

	@GetMapping
	public Etat etat(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestParam("produit") Produit produit) {
		return service.etat(jetonSession, produit);
	}

	@PostMapping("/ouvertures")
	public ResponseEntity<Etat> ouvrir(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Demande demande) {
		if (incomplete(demande)) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(service.ouvrir(jetonSession, demande.produit(), demande.document()));
	}

	@PostMapping("/lectures")
	public ResponseEntity<Etat> cocher(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Demande demande) {
		if (incomplete(demande)) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(service.cocher(jetonSession, demande.produit(), demande.document()));
	}

	private static boolean incomplete(Demande demande) {
		return demande == null || demande.produit() == null || demande.document() == null;
	}

	@ExceptionHandler(DocumentNonOuvert.class)
	public ResponseEntity<Refus> nonOuvert() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new Refus(OUVRIR_D_ABORD));
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
