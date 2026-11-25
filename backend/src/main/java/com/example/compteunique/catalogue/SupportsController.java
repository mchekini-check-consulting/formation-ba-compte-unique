package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilClientService.Statut;
import com.example.compteunique.profil.ProfilKyc;

// CU-412 (CAT-01B) : supports disponibles pour la cliente connectée (session obligatoire).
// GET /catalogue/supports?produit=PER|AV : {produit, profilValide, profilInvestisseur, fraisContratPct, supports,
// message}. Profil non validé (à compléter, à revalider) : aucune unité de compte et le message du questionnaire.
// Le profil est lu par la fonction publique du module Client et profil (ADR-001, règle 1).
@RestController
public class SupportsController {

	static final String PROFIL_A_VALIDER = "Votre profil doit être validé pour investir en unités de compte";

	private final SupportsEligibles eligibles;
	private final ProfilClientService profils;

	public SupportsController(SupportsEligibles eligibles, ProfilClientService profils) {
		this.eligibles = eligibles;
		this.profils = profils;
	}

	public record SupportsDisponibles(Produit produit, boolean profilValide, String profilInvestisseur,
			BigDecimal fraisContratPct, List<SupportEligible> supports, String message) {
	}

	public record Refus(String message) {
	}

	@GetMapping("/catalogue/supports")
	public SupportsDisponibles supports(
			@CookieValue(name = ConnexionController.COOKIE_SESSION, required = false) String jetonSession,
			@RequestParam("produit") String produit) {
		Produit choisi = produit(produit);
		ProfilClientService.Etat etat = profils.etat(jetonSession);
		String profil = etat.profil().map(ProfilKyc::getProfilInvestisseur).orElse(null);
		BigDecimal frais = eligibles.fraisContrat(choisi);
		if (etat.statut() != Statut.VALIDE) {
			return new SupportsDisponibles(choisi, false, profil, frais, List.of(), PROFIL_A_VALIDER);
		}
		return new SupportsDisponibles(choisi, true, profil, frais, eligibles.lister(choisi, profil), null);
	}

	private static Produit produit(String produit) {
		try {
			return Produit.valueOf(produit);
		} catch (IllegalArgumentException | NullPointerException inconnu) {
			throw new ProduitInconnu();
		}
	}

	@ExceptionHandler(ProduitInconnu.class)
	public ResponseEntity<Refus> produitInconnu() {
		return ResponseEntity.badRequest().body(new Refus("Produit inconnu : PER ou AV attendu."));
	}

	@ExceptionHandler(ClienteNonConnecteeException.class)
	public ResponseEntity<Void> nonConnectee() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(CompteInaccessibleException.class)
	public ResponseEntity<Refus> sansCompte() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus("Aucun compte courant"));
	}

	/** Produit autre que PER ou AV (400). */
	static class ProduitInconnu extends RuntimeException {

		private static final long serialVersionUID = 1L;

	}

}
