package com.example.compteunique.activation;

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

// CU-14 : page « Activer mon espace » (lien reçu par SMS). Le jeton circule dans le corps des
// requêtes, jamais dans l'adresse : il n'apparaît pas dans les journaux.
// POST /activation/etat {jeton}                 : VALIDE, EXPIRE ou DEJA_ACTIVE (le lien n'est pas consommé)
// POST /activation {jeton, motDePasse, confirmation} : définit le mot de passe
// POST /activation/nouveau-lien {jeton}         : nouveau lien par SMS
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/activation/...
@RestController
@RequestMapping("/activation")
public class ActivationController {

	static final String MESSAGE_LIEN_EXPIRE = "Lien expiré";
	static final String MESSAGE_NOUVEAU_LIEN = "Si votre espace n'est pas encore activé, un nouveau lien vous a été envoyé par SMS.";

	private final ActivationService activation;

	public ActivationController(ActivationService activation) {
		this.activation = activation;
	}

	public record Lien(String jeton) {

		@Override
		public String toString() {
			return "Lien[jeton=***]";
		}

	}

	public record DemandeActivation(String jeton, String motDePasse, String confirmation) {

		// Ni le jeton ni le mot de passe ne doivent apparaître dans un log.
		@Override
		public String toString() {
			return "DemandeActivation[***]";
		}

	}

	public record Etat(EtatActivation etat) {
	}

	public record Refus(String message, List<String> regles) {
	}

	@PostMapping("/etat")
	public Etat etat(@RequestBody Lien lien) {
		return new Etat(activation.etat(lien.jeton()));
	}

	@PostMapping
	public Etat activer(@RequestBody DemandeActivation demande) {
		return new Etat(activation.activer(demande.jeton(), demande.motDePasse(), demande.confirmation()));
	}

	@PostMapping("/nouveau-lien")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Refus nouveauLien(@RequestBody Lien lien) {
		activation.nouveauLien(lien.jeton());
		return new Refus(MESSAGE_NOUVEAU_LIEN, List.of());
	}

	/** Le premier message est affiché ; tous sont renvoyés. */
	@ExceptionHandler(MotDePasseNonConformeException.class)
	public ResponseEntity<Refus> motDePasseRefuse(MotDePasseNonConformeException refus) {
		return ResponseEntity.badRequest().body(new Refus(refus.getRegles().get(0), refus.getRegles()));
	}

	@ExceptionHandler(JetonInvalideException.class)
	public ResponseEntity<Refus> lienExpire() {
		return ResponseEntity.status(HttpStatus.GONE).body(new Refus(MESSAGE_LIEN_EXPIRE, List.of()));
	}

	@ExceptionHandler(EspaceDejaActiveException.class)
	public ResponseEntity<Refus> dejaActive() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new Refus(ActivationService.MESSAGE_DEJA_ACTIVE, List.of()));
	}

}
