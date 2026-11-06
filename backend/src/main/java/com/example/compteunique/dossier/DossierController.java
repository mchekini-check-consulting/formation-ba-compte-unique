package com.example.compteunique.dossier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.dossier.Categories.MotifAnnulation;
import com.example.compteunique.dossier.OuvertureRefuseeException.EtapeReprise;
import com.example.compteunique.dossier.OuvertureRefuseeException.Refus;

// Dossier d'ouverture de compte (CUB-8). La référence est générée par l'écran au début du parcours.
// PUT /dossiers/{reference}/identite   : enregistre l'identité saisie (statut « En cours de vérification »)
// PUT /dossiers/{reference}/controles  : enregistre le résultat des contrôles sanctions et PEP
// PUT /dossiers/{reference}/annulation : annulation signalée par l'écran {motif} (CU-9)
// CU-9 : 409 {code, message, reprise, conseiller} si la personne est déjà connue (profil ou dossier).
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/dossiers/...
@RestController
@RequestMapping("/dossiers/{reference}")
public class DossierController {

	private final DossierService dossiers;
	private final String coordonneesConseiller;

	/** CU-9 : dossier en cours à reprendre (seul cas où une référence est renvoyée). */
	public record Reprise(String reference, EtapeReprise etape) {
	}

	/** CU-9 : refus d'une nouvelle ouverture ; jamais de statut, nom, numéro de compte ni motif. */
	public record RefusOuverture(Refus code, String message, Reprise reprise, String conseiller) {
	}

	public record DemandeAnnulation(MotifAnnulation motif) {
	}

	public DossierController(DossierService dossiers,
			@Value("${app.conseiller.coordonnees}") String coordonneesConseiller) {
		this.dossiers = dossiers;
		this.coordonneesConseiller = coordonneesConseiller;
	}

	@PutMapping("/identite")
	public DossierReponse renseignerIdentite(@PathVariable String reference, @RequestBody IdentiteRequete identite) {
		return dossiers.renseignerIdentite(reference, identite);
	}

	@PutMapping("/controles")
	public DossierReponse enregistrerControles(@PathVariable String reference,
			@RequestBody ControlesRequete controles) {
		return dossiers.enregistrerControles(reference, controles);
	}

	@PutMapping("/annulation")
	public DossierReponse annuler(@PathVariable String reference, @RequestBody DemandeAnnulation demande) {
		return dossiers.annuler(reference, demande == null ? null : demande.motif());
	}

	@ExceptionHandler(OuvertureRefuseeException.class)
	public ResponseEntity<RefusOuverture> refus(OuvertureRefuseeException refus) {
		Reprise reprise = refus.getReferenceReprise() == null ? null
				: new Reprise(refus.getReferenceReprise(), refus.getEtapeReprise());
		String conseiller = refus.getRefus() == Refus.OUVERTURE_ANNULEE ? coordonneesConseiller : null;
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new RefusOuverture(refus.getRefus(), refus.getRefus().message(), reprise, conseiller));
	}

}
