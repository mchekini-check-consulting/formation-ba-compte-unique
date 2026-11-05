package com.example.compteunique.reinitialisation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.connexion.JetonInvalideException;
import com.example.compteunique.connexion.MotDePasseNonConformeException;

// CU-46 : mot de passe oublié. Le jeton circule dans le corps des requêtes, jamais dans
// l'adresse : il n'apparaît pas dans les journaux.
// POST /reinitialisation/demande {identifiant}                   : lien par SMS (même réponse dans tous les cas)
// POST /reinitialisation/etat {jeton}                            : VALIDE ou EXPIRE (le lien n'est pas consommé)
// POST /reinitialisation {jeton, motDePasse, confirmation}       : définit le nouveau mot de passe
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/reinitialisation/...
@RestController
@RequestMapping("/reinitialisation")
public class ReinitialisationController {

	static final String MESSAGE_DEMANDE = "Si un compte correspond, un lien vient d’être envoyé par SMS";
	static final String MESSAGE_LIEN_EXPIRE = "Lien expiré";

	private final ReinitialisationService reinitialisation;

	public ReinitialisationController(ReinitialisationService reinitialisation) {
		this.reinitialisation = reinitialisation;
	}

	public record DemandeLien(String identifiant) {

		@Override
		public String toString() {
			return "DemandeLien[***]";
		}

	}

	public record Lien(String jeton) {

		@Override
		public String toString() {
			return "Lien[jeton=***]";
		}

	}

	public record NouveauMotDePasse(String jeton, String motDePasse, String confirmation) {

		// Ni le jeton ni les mots de passe ne doivent apparaître dans un log.
		@Override
		public String toString() {
			return "NouveauMotDePasse[***]";
		}

	}

	public record Etat(EtatReinitialisation etat) {
	}

	public record Reponse(String message, List<String> regles) {
	}

	@PostMapping("/demande")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Reponse demander(@RequestBody DemandeLien demande) {
		reinitialisation.demander(demande.identifiant());
		return new Reponse(MESSAGE_DEMANDE, List.of());
	}

	@PostMapping("/etat")
	public Etat etat(@RequestBody Lien lien) {
		return new Etat(reinitialisation.etat(lien.jeton()));
	}

	@PostMapping
	public Etat reinitialiser(@RequestBody NouveauMotDePasse demande) {
		return new Etat(reinitialisation.reinitialiser(demande.jeton(), demande.motDePasse(), demande.confirmation()));
	}

	/** Le premier message est affiché ; tous sont renvoyés. */
	@ExceptionHandler(MotDePasseNonConformeException.class)
	public ResponseEntity<Reponse> motDePasseRefuse(MotDePasseNonConformeException refus) {
		return ResponseEntity.badRequest().body(new Reponse(refus.getRegles().get(0), refus.getRegles()));
	}

	@ExceptionHandler(JetonInvalideException.class)
	public ResponseEntity<Reponse> lienExpire() {
		return ResponseEntity.status(HttpStatus.GONE).body(new Reponse(MESSAGE_LIEN_EXPIRE, List.of()));
	}

}
