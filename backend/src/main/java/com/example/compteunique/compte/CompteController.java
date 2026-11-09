package com.example.compteunique.compte;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.connexion.ConnexionController;

// CU-20 : GET /comptes (mes comptes) ; GET /comptes/{id} (un compte dont je suis titulaire).
// CU-11 : GET /comptes/{id}/versements (rubrique Versements : IBAN complet et derniers mouvements).
@RestController
@RequestMapping("/comptes")
public class CompteController {

	// Texte exact du ticket CU-20.
	static final String MESSAGE_INACCESSIBLE = "Ce compte n’est pas accessible";

	private final CompteService comptes;

	public CompteController(CompteService comptes) {
		this.comptes = comptes;
	}

	// Jamais d'IBAN complet ni de solde négatif : soldeCentimes est vide si le solde est refusé (CU-19).
	public record CompteVue(Long id, String ibanMasque, String statut, Long soldeCentimes,
			OffsetDateTime soldeMisAJourLe, boolean premierVersementAttendu) {

		static CompteVue de(Compte compte) {
			Long solde = compte.getSoldeAffichable().orElse(null);
			OffsetDateTime miseAJour = solde == null || compte.isCloture() ? null : compte.getSoldeMisAJourLe();
			return new CompteVue(compte.getId(), compte.getIbanMasque(), compte.getStatut(), solde, miseAJour,
					compte.isSansVersement());
		}

	}

	public record Refus(String message) {
	}

	/** CU-11 : un mouvement tel qu'affiché dans la rubrique Versements. */
	public record MouvementVue(OffsetDateTime date, String libelle, String emetteur, long montantCentimes) {

		static MouvementVue de(Mouvement mouvement) {
			return new MouvementVue(mouvement.getEffectueLe(), mouvement.getLibelle(), mouvement.getEmetteur(),
					mouvement.getMontantCentimes());
		}

	}

	/**
	 * CU-11 : rubrique Versements. iban : IBAN complet à communiquer à l'émetteur, seulement pour un compte actif
	 * (seul endroit où l'IBAN complet est envoyé à l'écran) ; vide sinon.
	 */
	public record RubriqueVersements(String iban, List<MouvementVue> mouvements) {
	}

	@GetMapping("/{id}/versements")
	public RubriqueVersements versements(
			@CookieValue(name = ConnexionController.COOKIE_SESSION, required = false) String jetonSession,
			@PathVariable String id) {
		Compte compte = comptes.consulter(jetonSession, id);
		return new RubriqueVersements(compte.isActif() ? compte.getIbanParQuatre() : null,
				comptes.mouvements(compte).stream().map(MouvementVue::de).toList());
	}

	@GetMapping
	public List<CompteVue> mesComptes(
			@CookieValue(name = ConnexionController.COOKIE_SESSION, required = false) String jetonSession) {
		return comptes.mesComptes(jetonSession).stream().map(CompteVue::de).toList();
	}

	@GetMapping("/{id}")
	public CompteVue consulter(@CookieValue(name = ConnexionController.COOKIE_SESSION, required = false) String jetonSession,
			@PathVariable String id) {
		return CompteVue.de(comptes.consulter(jetonSession, id));
	}

	@ExceptionHandler(ClienteNonConnecteeException.class)
	public ResponseEntity<Void> nonConnectee() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(CompteInaccessibleException.class)
	public ResponseEntity<Refus> inaccessible() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(MESSAGE_INACCESSIBLE));
	}

}
