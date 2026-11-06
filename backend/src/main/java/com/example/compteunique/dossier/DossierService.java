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

import com.example.compteunique.dossier.Categories.MotifAnnulation;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.logging.Loggable;

// CUB-8 (US-01) : enregistre l'identité saisie puis le résultat des contrôles sanctions et PEP.
// Les règles de saisie sont aussi contrôlées par l'écran ; elles sont revérifiées ici car le
// backend ne doit jamais enregistrer un dossier incomplet.
// CU-9 : une personne déjà connue ne crée pas de nouveau dossier (ControleDoublon).
@Service
@Loggable
public class DossierService {

	static final int AGE_MINIMUM = 18;
	private static final Set<String> CIVILITES = Set.of("M.", "Mme");
	private static final String CHAMP_REFERENCE = "reference";
	/** CU-9 : annulations que l'écran peut signaler (étapes pièce, selfie, questions). */
	private static final Set<MotifAnnulation> ANNULATIONS_ECRAN = Set.of(MotifAnnulation.CONTROLE_AUTOMATIQUE,
			MotifAnnulation.DEMANDE_CLIENTE);

	private final DossierRepository dossiers;
	private final ControleDoublon doublons;
	private final Clock horloge;

	@Autowired
	public DossierService(DossierRepository dossiers, ControleDoublon doublons) {
		this(dossiers, doublons, Clock.systemDefaultZone());
	}

	DossierService(DossierRepository dossiers, ControleDoublon doublons, Clock horloge) {
		this.dossiers = dossiers;
		this.doublons = doublons;
		this.horloge = horloge;
	}

	/**
	 * CU-9 : la personne est contrôlée contre les profils et dossiers existants avant tout
	 * enregistrement. En cas de refus, aucun dossier n'est créé ni modifié ; seul l'événement est
	 * ajouté au journal (pas d'annulation de la transaction pour le garder).
	 */
	@Transactional(noRollbackFor = OuvertureRefuseeException.class)
	public DossierReponse renseignerIdentite(String reference, IdentiteRequete requete) {
		String ref = obligatoire(reference, CHAMP_REFERENCE);
		IdentiteRequete identite = controler(requete);
		Dossier dossier = dossiers.findByReference(ref).orElseGet(() -> new Dossier(ref));
		if (dossier.getStatut() == StatutDossier.ANNULE) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "dossier annulé");
		}
		if (dossier.getStatut() != StatutDossier.EN_COURS_DE_VERIFICATION) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "convention déjà signée");
		}
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		doublons.verifier(ref, identite, maintenant);
		dossier.renseignerIdentite(identite, maintenant);
		return DossierReponse.de(dossiers.save(dossier));
	}

	/**
	 * CU-9 : annulation signalée par l'écran, pour un dossier encore « En cours de vérification » :
	 * contrôle automatique (pièce expirée ou non authentique, écart de concordance, selfie, PEP
	 * déclaré) ou demande de la cliente.
	 */
	@Transactional
	public DossierReponse annuler(String reference, MotifAnnulation motif) {
		if (motif == null || !ANNULATIONS_ECRAN.contains(motif)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motif : CONTROLE_AUTOMATIQUE ou DEMANDE_CLIENTE");
		}
		Dossier dossier = dossierExistant(reference);
		if (dossier.getStatut() != StatutDossier.EN_COURS_DE_VERIFICATION) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "dossier non annulable");
		}
		dossier.annuler(motif, OffsetDateTime.now(horloge));
		return DossierReponse.de(dossiers.save(dossier));
	}

	@Transactional
	public DossierReponse enregistrerControles(String reference, ControlesRequete controles) {
		if (controles == null || controles.sanctions() == null || controles.pep() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sanctions et pep obligatoires");
		}
		Dossier dossier = dossierExistant(reference);
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

	private Dossier dossierExistant(String reference) {
		return dossiers.findByReference(obligatoire(reference, CHAMP_REFERENCE))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dossier inconnu"));
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
