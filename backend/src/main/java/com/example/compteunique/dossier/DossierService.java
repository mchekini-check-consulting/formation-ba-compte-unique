package com.example.compteunique.dossier;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.logging.Loggable;

// CUB-8 (US-01) : enregistre l'identité saisie puis le résultat des contrôles sanctions et PEP.
// Les règles de saisie sont aussi contrôlées par l'écran ; elles sont revérifiées ici car le
// backend ne doit jamais enregistrer un dossier incomplet.
@Service
@Loggable
public class DossierService {

	static final int AGE_MINIMUM = 18;
	private static final Set<String> CIVILITES = Set.of("M.", "Mme");

	private final DossierRepository dossiers;
	private final Clock horloge;

	@Autowired
	public DossierService(DossierRepository dossiers) {
		this(dossiers, Clock.systemDefaultZone());
	}

	DossierService(DossierRepository dossiers, Clock horloge) {
		this.dossiers = dossiers;
		this.horloge = horloge;
	}

	@Transactional
	public DossierReponse renseignerIdentite(String reference, IdentiteRequete requete) {
		String ref = obligatoire(reference, "reference");
		IdentiteRequete identite = controler(requete);
		Dossier dossier = dossiers.findByReference(ref).orElseGet(() -> new Dossier(ref));
		if (dossier.getStatut() == StatutDossier.ANNULE) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "dossier annulé");
		}
		if (dossier.getStatut() != StatutDossier.EN_COURS_DE_VERIFICATION) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "convention déjà signée");
		}
		dossier.renseignerIdentite(identite, OffsetDateTime.now(horloge));
		return DossierReponse.de(dossiers.save(dossier));
	}

	@Transactional
	public DossierReponse enregistrerControles(String reference, ControlesRequete controles) {
		if (controles == null || controles.sanctions() == null || controles.pep() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sanctions et pep obligatoires");
		}
		Dossier dossier = dossiers.findByReference(obligatoire(reference, "reference"))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dossier inconnu"));
		dossier.enregistrerControles(controles.sanctions(), controles.pep(), OffsetDateTime.now(horloge));
		return DossierReponse.de(dossiers.save(dossier));
	}

	/** Champs obligatoires et règles de CUB-8 ; renvoie l'identité nettoyée (espaces, champs facultatifs vides). */
	private IdentiteRequete controler(IdentiteRequete i) {
		if (i == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "identité obligatoire");
		}
		String civilite = obligatoire(i.civilite(), "civilite");
		if (!CIVILITES.contains(civilite)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "civilite : M. ou Mme");
		}
		LocalDate aujourdhui = LocalDate.now(horloge);
		LocalDate dateNaissance = present(i.dateNaissance(), "dateNaissance");
		if (dateNaissance.isAfter(aujourdhui.minusYears(AGE_MINIMUM))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous devez avoir au moins 18 ans");
		}
		LocalDate dateExpiration = present(i.dateExpiration(), "dateExpiration");
		if (!dateExpiration.isAfter(aujourdhui)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date d'expiration dépassée");
		}
		return new IdentiteRequete(
				civilite,
				obligatoire(i.nomNaissance(), "nomNaissance"),
				facultatif(i.nomUsage()),
				obligatoire(i.prenoms(), "prenoms"),
				dateNaissance,
				obligatoire(i.communeNaissance(), "communeNaissance"),
				present(i.paysNaissance(), "paysNaissance"),
				present(i.nationalite(), "nationalite"),
				obligatoire(i.voie(), "voie"),
				facultatif(i.complement()),
				obligatoire(i.codePostal(), "codePostal"),
				obligatoire(i.ville(), "ville"),
				obligatoire(i.paysAdresse(), "paysAdresse"),
				obligatoire(i.email(), "email"),
				obligatoire(i.mobile(), "mobile"),
				facultatif(i.fixe()),
				present(i.typePiece(), "typePiece"),
				obligatoire(i.numeroPiece(), "numeroPiece"),
				dateExpiration);
	}

	private static String obligatoire(String valeur, String champ) {
		if (valeur == null || valeur.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, champ + " obligatoire");
		}
		return valeur.trim();
	}

	private static <T> T present(T valeur, String champ) {
		if (valeur == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, champ + " obligatoire");
		}
		return valeur;
	}

	private static String facultatif(String valeur) {
		return valeur == null || valeur.isBlank() ? null : valeur.trim();
	}

}
