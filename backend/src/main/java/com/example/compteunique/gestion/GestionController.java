package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.util.UUID;

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

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.gestion.Allocation.AllocationImpossible;
import com.example.compteunique.gestion.ModeDeGestion.ChoixRefuse;
import com.example.compteunique.gestion.ModeDeGestion.ContratIntrouvable;
import com.example.compteunique.gestion.ModeDeGestion.ContratsDeTestIndisponibles;
import com.example.compteunique.gestion.ModeDeGestion.DemandeInvalide;
import com.example.compteunique.gestion.ModeDeGestion.Etat;
import com.example.compteunique.gestion.ModeDeGestion.ProfilRequis;
import com.example.compteunique.gestion.ModeDeGestion.Versement;
import com.example.compteunique.gestion.ModeDeGestion.VersementImpossible;
import com.example.compteunique.ordres.OrdresService;

// CU-314 (PER-07) et CU-338 (AV-07) : choisir son mode de gestion (session obligatoire).
// GET  /gestion?produit=PER|AV : {produit, profilInvestisseur, contratsDeTest, mandat: {tauxToutComprisPct,
//                                montantMinimum}, contrat: null | {numeroContrat, mode, profil, changeLe, encours,
//                                contraintes, allocation, allocationIndisponible, historique, reequilibrages}}
// POST /gestion/contrats       : {produit} -> contrat de test créé en gestion pilotée équilibrée (dev et qualif ; 404
//                                ailleurs ; 409 sans profil validé)
// POST /gestion/choix          : {produit, mode, profil} -> nouvel état (gratuit) ; 422 refus avec message
// POST /gestion/versements     : {produit, id, montant} -> versement réparti selon l'allocation pilotée (201)
@RestController
@RequestMapping("/gestion")
public class GestionController {

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final ModeDeGestion service;

	public GestionController(ModeDeGestion service) {
		this.service = service;
	}

	public record DemandeContrat(Produit produit) {
	}

	public record Choix(Produit produit, ModeGestion mode, ProfilGestion profil) {
	}

	public record DemandeVersement(Produit produit, UUID id, BigDecimal montant) {
	}

	public record Refus(String message) {
	}

	@GetMapping
	public Etat etat(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestParam("produit") Produit produit) {
		return service.etat(jetonSession, produit);
	}

	@PostMapping("/contrats")
	public ResponseEntity<Etat> creerContrat(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) DemandeContrat demande) {
		if (demande == null || demande.produit() == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(service.creerContratDeTest(jetonSession,
				demande.produit()));
	}

	@PostMapping("/choix")
	public ResponseEntity<Etat> choisir(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Choix choix) {
		if (choix == null || choix.produit() == null || choix.mode() == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(service.choisir(jetonSession, choix.produit(), choix.mode(), choix.profil()));
	}

	@PostMapping("/versements")
	public ResponseEntity<Versement> verser(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) DemandeVersement demande) {
		if (demande == null || demande.produit() == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(service.verser(jetonSession, demande.produit(), demande.id(), demande.montant()));
	}

	@ExceptionHandler({ ChoixRefuse.class, AllocationImpossible.class })
	public ResponseEntity<Refus> refuse(RuntimeException refus) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler({ ProfilRequis.class, VersementImpossible.class })
	public ResponseEntity<Refus> conflit(RuntimeException refus) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler({ ContratIntrouvable.class, ContratsDeTestIndisponibles.class })
	public ResponseEntity<Refus> introuvable(RuntimeException refus) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler({ DemandeInvalide.class, OrdresService.DemandeInvalide.class })
	public ResponseEntity<Refus> invalide(RuntimeException refus) {
		return ResponseEntity.badRequest().body(new Refus(refus.getMessage()));
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
