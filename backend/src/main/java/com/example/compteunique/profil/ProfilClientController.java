package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.profil.ProfilClientService.ProfilIncomplet;
import com.example.compteunique.profil.ProfilClientService.Statut;

// CU-10 (US-05) : profil client de la cliente connectée (session obligatoire).
// GET  /profil           : {"statut", "profil"} ; profil = dernière version (null sans profil), pour pré-remplir.
// POST /profil           : {"reponses", "consentement"} -> nouvelle version (201), ou 400 {"message", "champs"}.
// GET  /profil/versions  : toutes les versions, de la plus récente à la plus ancienne.
@RestController
@RequestMapping("/profil")
public class ProfilClientController {

	static final String MESSAGE_INCOMPLET = "Votre profil est à compléter : corrigez les réponses signalées. "
			+ "Aucune souscription de PER ni d'assurance vie n'est possible tant qu'il n'est pas validé.";

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final ProfilClientService service;

	public ProfilClientController(ProfilClientService service) {
		this.service = service;
	}

	public record ProfilVue(int version, OffsetDateTime valideLe, OffsetDateTime revaliderLe, Reponses reponses,
			String profilInvestisseur, int scoreInvestisseur) {

		static ProfilVue de(ProfilKyc p) {
			return new ProfilVue(p.getVersion(), p.getValideLe(), p.getRevaliderLe(), p.getReponses(),
					p.getProfilInvestisseur(), p.getScoreInvestisseur());
		}

	}

	public record EtatVue(Statut statut, ProfilVue profil) {
	}

	public record Questionnaire(Reponses reponses, Boolean consentement) {
	}

	public record Refus(String message, Map<String, String> champs) {
	}

	@GetMapping
	public EtatVue etat(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		ProfilClientService.Etat etat = service.etat(jetonSession);
		return new EtatVue(etat.statut(), etat.profil().map(ProfilVue::de).orElse(null));
	}

	@PostMapping
	public ResponseEntity<ProfilVue> valider(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Questionnaire questionnaire) {
		Questionnaire q = questionnaire != null ? questionnaire : new Questionnaire(null, null);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ProfilVue.de(service.valider(jetonSession, q.reponses(), q.consentement())));
	}

	@GetMapping("/versions")
	public List<ProfilVue> versions(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return service.versions(jetonSession).stream().map(ProfilVue::de).toList();
	}

	@ExceptionHandler(ProfilIncomplet.class)
	public ResponseEntity<Refus> incomplet(ProfilIncomplet refus) {
		return ResponseEntity.badRequest().body(new Refus(MESSAGE_INCOMPLET, refus.getChamps()));
	}

	@ExceptionHandler(ClienteNonConnecteeException.class)
	public ResponseEntity<Void> nonConnectee() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(CompteInaccessibleException.class)
	public ResponseEntity<Refus> sansCompte() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus("Aucun compte courant", Map.of()));
	}

}
