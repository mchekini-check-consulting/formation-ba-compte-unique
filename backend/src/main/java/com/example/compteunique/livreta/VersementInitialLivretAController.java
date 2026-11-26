package com.example.compteunique.livreta;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.livreta.LivretAController.Refus;

// CU-33 (US-24) : versement initial du Livret A (session obligatoire), une fois l'ouverture validée.
// POST /livret-a/demandes/{reference}/versement-initial : {"montant": "150,50"} -> demande (montant enregistré).
// Refus : {"message", "termine"} avec le message affiché à la cliente (mêmes règles que LivretAController).
@RestController
@RequestMapping("/livret-a/demandes")
public class VersementInitialLivretAController {

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final VersementInitialLivretAService versements;
	private final LivretAService demandes;

	public VersementInitialLivretAController(VersementInitialLivretAService versements, LivretAService demandes) {
		this.versements = versements;
		this.demandes = demandes;
	}

	/** Montant saisi par la cliente, en euros (« 150 » ou « 150,50 »). */
	public record VersementInitial(String montant) {
	}

	@PostMapping("/{reference}/versement-initial")
	public DemandeLivretAVue choisir(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) VersementInitial versement) {
		return DemandeLivretAVue.de(demandes.suivi(
				versements.choisir(jetonSession, reference, versement == null ? null : versement.montant())));
	}

	/** Sans session : 401 sans corps ; sans compte courant : 404 ; refus : son code et son message. */
	@ExceptionHandler({ DemandeRefusee.class, ClienteNonConnecteeException.class, CompteInaccessibleException.class })
	public ResponseEntity<Refus> refus(RuntimeException erreur) {
		return LivretAController.reponseDe(erreur);
	}

}
