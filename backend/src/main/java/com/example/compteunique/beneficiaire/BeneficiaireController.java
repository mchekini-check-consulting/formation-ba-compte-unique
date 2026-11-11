package com.example.compteunique.beneficiaire;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.BeneficiaireService.AjoutEnAttente;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.virement.IbanBeneficiaire;

// CU-146 (US-22) : rubrique « Mes bénéficiaires » de la cliente connectée (session obligatoire).
// GET    /beneficiaires                           : bénéficiaires actifs (IBAN masqué).
// POST   /beneficiaires                           : {"iban", "nom", "libelle"} -> vérification, code SMS envoyé (202).
// POST   /beneficiaires/ajouts/{id}/confirmation  : {"code"} -> bénéficiaire enregistré (201).
// PATCH  /beneficiaires/{id}                      : {"libelle"} -> libellé modifié.
// DELETE /beneficiaires/{id}                      : statut « supprimé » (204).
// Refus : {"message", "termine"} avec le message exact du ticket.
@RestController
@RequestMapping("/beneficiaires")
public class BeneficiaireController {

	static final String SANS_COMPTE = "Aucun compte courant";

	private final BeneficiaireService service;

	public BeneficiaireController(BeneficiaireService service) {
		this.service = service;
	}

	/** Critère 11 : libellé, nom vérifié, IBAN masqué, date d'ajout et statut « vérifié ». Jamais l'IBAN complet. */
	public record BeneficiaireVue(Long id, String libelle, String nomVerifie, String ibanMasque,
			OffsetDateTime ajouteLe, String verification) {

		static final String VERIFIE = "vérifié";

		static BeneficiaireVue de(Beneficiaire beneficiaire) {
			return new BeneficiaireVue(beneficiaire.getId(), beneficiaire.getLibelle(), beneficiaire.getNomVerifie(),
					IbanBeneficiaire.masquer(beneficiaire.getIban()), beneficiaire.getAjouteLe(), VERIFIE);
		}

	}

	public record DemandeAjout(String iban, String nom, String libelle) {
	}

	public record Code(String code) {
	}

	public record Libelle(String libelle) {
	}

	public record Refus(String message, boolean termine) {
	}

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	@GetMapping
	public List<BeneficiaireVue> lister(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return service.lister(jetonSession).stream().map(BeneficiaireVue::de).toList();
	}

	@PostMapping
	public ResponseEntity<AjoutEnAttente> ajouter(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) DemandeAjout demande) {
		DemandeAjout lue = demande == null ? new DemandeAjout(null, null, null) : demande;
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(service.demanderAjout(jetonSession, lue.iban(), lue.nom(), lue.libelle()));
	}

	@PostMapping("/ajouts/{ajoutId}/confirmation")
	public ResponseEntity<BeneficiaireVue> confirmer(
			@CookieValue(name = COOKIE, required = false) String jetonSession, @PathVariable Long ajoutId,
			@RequestBody(required = false) Code code) {
		return ResponseEntity.status(HttpStatus.CREATED).body(BeneficiaireVue
				.de(service.confirmerAjout(jetonSession, ajoutId, code == null ? null : code.code())));
	}

	@PatchMapping("/{id}")
	public BeneficiaireVue renommer(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable Long id, @RequestBody(required = false) Libelle libelle) {
		return BeneficiaireVue.de(service.renommer(jetonSession, id, libelle == null ? null : libelle.libelle()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> supprimer(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable Long id) {
		service.supprimer(jetonSession, id);
		return ResponseEntity.noContent().build();
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
