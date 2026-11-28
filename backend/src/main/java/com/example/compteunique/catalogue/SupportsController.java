package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	static final String LISTE_INDISPONIBLE = "La liste des supports est momentanément indisponible";

	private final SupportsEligibles eligibles;
	private final ProfilClientService profils;
	private final SimulationSupports simulation;
	private final Adequation adequation;
	private final CatalogueService catalogue;

	public SupportsController(SupportsEligibles eligibles, ProfilClientService profils, SimulationSupports simulation,
			Adequation adequation, CatalogueService catalogue) {
		this.catalogue = catalogue;
		this.eligibles = eligibles;
		this.adequation = adequation;
		this.profils = profils;
		this.simulation = simulation;
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
		if (simulation.estIndisponible()) {
			throw new ListeIndisponible();
		}
		Produit choisi = produit(produit);
		ProfilClientService.Etat etat = profils.etat(jetonSession);
		String profil = etat.profil().map(ProfilKyc::getProfilInvestisseur).orElse(null);
		BigDecimal frais = eligibles.fraisContrat(choisi);
		if (etat.statut() != Statut.VALIDE) {
			return new SupportsDisponibles(choisi, false, profil, frais, List.of(), PROFIL_A_VALIDER);
		}
		List<SupportEligible> proposes = eligibles.lister(choisi, profil);
		// CU-348 : rapport d'adéquation de chaque proposition (le profil est présent quand il est validé).
		adequation.rapporter(etat.profil().orElseThrow().getCustomerId(), choisi, profil, proposes);
		return new SupportsDisponibles(choisi, true, profil, frais, proposes, null);
	}

	/** Une valeur liquidative de la courbe d'un support. */
	public record PointVl(LocalDate dateVl, BigDecimal valeur) {
	}

	/**
	 * Lot 6 : courbe de la valeur liquidative d'un support (fiche du support), lue dans catalogue.historique_vl, en
	 * lecture seule, de la plus ancienne à la plus récente. Session obligatoire, comme la liste des supports.
	 */
	@GetMapping("/catalogue/supports/{codeIsin}/historique")
	public List<PointVl> historique(
			@CookieValue(name = ConnexionController.COOKIE_SESSION, required = false) String jetonSession,
			@PathVariable String codeIsin) {
		profils.etat(jetonSession);
		return catalogue.historiqueVl(codeIsin).reversed().stream().map(v -> new PointVl(v.dateVl(), v.valeur()))
				.toList();
	}

	private static Produit produit(String produit) {
		for (Produit connu : Produit.values()) {
			if (connu.name().equals(produit)) {
				return connu;
			}
		}
		throw new ProduitInconnu();
	}

	/** CU-417 : liste simulée indisponible (test) : 503, aucune liste incomplète. */
	@ExceptionHandler(ListeIndisponible.class)
	public ResponseEntity<Refus> indisponible() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Refus(LISTE_INDISPONIBLE));
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

	/** Liste des supports simulée indisponible (503, test seulement). */
	static class ListeIndisponible extends RuntimeException {

		private static final long serialVersionUID = 1L;

	}

}
