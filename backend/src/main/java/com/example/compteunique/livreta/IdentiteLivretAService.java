package com.example.compteunique.livreta;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;

// CU-28 (US-19) : étape « Mon identité » de l'ouverture d'un Livret A par une cliente connectée. Identité lue
// dans son dossier (nom, prénom, date de naissance non modifiables) ; seules les coordonnées peuvent être
// corrigées, et le dossier est mis à jour tout de suite (décision métier). Résidence fiscale à confirmer. Un
// contrôle sanctions/PPE (API Gels des avoirs) par ouverture : l'écran l'interroge (5 s, 3 relances) et
// transmet ici le résultat, qui décide de la suite. Aucune ouverture sans contrôle validé (LivretAService).
@Service
@Loggable
public class IdentiteLivretAService {

	static final String RESIDENCE_OBLIGATOIRE = "Vous devez confirmer votre résidence fiscale pour continuer.";
	static final String CHAMP_OBLIGATOIRE = "Ce champ est obligatoire : ";
	static final String EMAIL_INVALIDE = "Adresse e-mail invalide.";
	static final String CODE_POSTAL_INVALIDE = "Code postal à 5 chiffres.";
	static final String MOBILE_INVALIDE = "Numéro de téléphone mobile invalide.";
	static final String CONTROLE_INTROUVABLE = "Contrôle introuvable";
	static final String CONTROLE_TERMINE = "Ce contrôle a déjà reçu son résultat.";
	static final String RESULTAT_OBLIGATOIRE = "Résultat du contrôle obligatoire";
	static final String TENTATIVES_INVALIDES = "Nombre d'essais invalide";
	static final String PAS_EN_ATTENTE_RESPONSABLE = "Ce contrôle n'attend pas la validation d'un responsable.";
	static final String DEMARCHE_SUSPENDUE = "Votre démarche est en cours de traitement : elle ne peut pas être reprise en ligne pour le moment.";
	static final String SANS_IDENTITE_CONFIRMEE = "Confirmez d'abord votre identité.";

	/** 1 envoi + 3 relances automatiques au plus. */
	static final int ESSAIS_MAX = 4;

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Pattern CODE_POSTAL = Pattern.compile("^\\d{5}$");
	private static final Pattern MOBILE = Pattern.compile("^(0|\\+33\\s?)[67]([\\s.-]?\\d{2}){4}$");

	/** Identité de la cliente telle qu'affichée à l'étape (résidence fiscale : « France » ou « Hors de France »). */
	public record Identite(String civilite, String nom, String prenoms, LocalDate dateNaissance,
			Coordonnees coordonnees, String residenceFiscale) {
	}

	/** Identité envoyée au contrôle sanctions/PPE (nationalité : FRANCE, UE_HORS_FRANCE, HORS_UE). */
	public record IdentiteAControler(String nomNaissance, String prenoms, LocalDate dateNaissance,
			String nationalite) {
	}

	/** Contrôle et, tant qu'il attend son résultat, l'identité à envoyer au contrôle. */
	public record Suivi(ControleIdentiteLivretA controle, IdentiteAControler aControler) {
	}

	private final CompteService comptes;
	private final DossierRepository dossiers;
	private final ControleIdentiteLivretARepository controles;
	private final Clock horloge;

	@Autowired
	public IdentiteLivretAService(CompteService comptes, DossierRepository dossiers,
			ControleIdentiteLivretARepository controles) {
		this(comptes, dossiers, controles, Clock.systemDefaultZone());
	}

	IdentiteLivretAService(CompteService comptes, DossierRepository dossiers,
			ControleIdentiteLivretARepository controles, Clock horloge) {
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.controles = controles;
		this.horloge = horloge;
	}

	/** Critère 1 : identité pré-remplie depuis le dossier client. */
	@Transactional(readOnly = true)
	public Identite identite(String jetonSession) {
		Dossier dossier = dossier(comptes.compteCourant(jetonSession).getDossierId());
		return new Identite(dossier.getCivilite(), dossier.getNomNaissance(), dossier.getPrenoms(),
				dossier.getDateNaissance(),
				new Coordonnees(dossier.getVoie(), dossier.getComplement(), dossier.getCodePostal(), dossier.getVille(),
						dossier.getPaysAdresse(), dossier.getEmail(), dossier.getMobile()),
				dossier.isResidenceFrancaise() ? "France" : "Hors de France");
	}

	/** Dernier contrôle de la cliente, vide si elle n'en a pas. */
	@Transactional(readOnly = true)
	public Optional<Suivi> dernierControle(String jetonSession) {
		Compte compte = comptes.compteCourant(jetonSession);
		return controles.findFirstByCustomerIdOrderByCreeLeDesc(compte.getCustomerId()).map(this::suivi);
	}

	/**
	 * Critères 2 à 6 : coordonnées contrôlées (champ vide, e-mail sans « @ »), résidence fiscale confirmée, dossier
	 * mis à jour, puis contrôle sanctions/PPE créé, à envoyer par l'écran.
	 */
	@Transactional
	public Suivi confirmer(String jetonSession, Coordonnees saisies, Boolean residenceFiscaleConfirmee) {
		Compte compte = comptes.compteCourant(jetonSession);
		controles.findFirstByCustomerIdOrderByCreeLeDesc(compte.getCustomerId())
				.filter(c -> c.getStatut() == StatutControleIdentite.BLOQUE
						|| c.getStatut() == StatutControleIdentite.ATTENTE_RESPONSABLE
						|| c.getStatut() == StatutControleIdentite.EN_ATTENTE)
				.ifPresent(c -> {
					throw new DemandeRefusee(HttpStatus.CONFLICT, DEMARCHE_SUSPENDUE, true);
				});
		Coordonnees coordonnees = verifier(saisies);
		if (!Boolean.TRUE.equals(residenceFiscaleConfirmee)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, RESIDENCE_OBLIGATOIRE);
		}
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		Dossier dossier = dossier(compte.getDossierId());
		boolean modifiees = dossier.corrigerCoordonnees(coordonnees, maintenant);
		ControleIdentiteLivretA controle = controles
				.save(new ControleIdentiteLivretA(dossier.getId(), compte.getCustomerId(), modifiees, maintenant));
		return new Suivi(controle, aControler(dossier));
	}

	/** Critères 6 à 9 : résultat du contrôle (ou absence de réponse après 3 relances) transmis par l'écran. */
	@Transactional
	public Suivi enregistrerResultat(String jetonSession, String reference, ResultatSanctions resultat,
			Integer tentatives) {
		Compte compte = comptes.compteCourant(jetonSession);
		if (resultat == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, RESULTAT_OBLIGATOIRE);
		}
		if (tentatives == null || tentatives < 1 || tentatives > ESSAIS_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, TENTATIVES_INVALIDES);
		}
		ControleIdentiteLivretA controle = controles.findByReferenceAndCustomerId(reference, compte.getCustomerId())
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, CONTROLE_INTROUVABLE));
		if (controle.getStatut() != StatutControleIdentite.EN_COURS) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, CONTROLE_TERMINE, true);
		}
		controle.recevoirResultat(resultat, tentatives, OffsetDateTime.now(horloge));
		return suivi(controle);
	}

	/** Critère 9 (test) : un responsable valide la démarche d'une personne politiquement exposée. */
	@Transactional
	public ControleIdentiteLivretA validerParResponsable(String reference) {
		ControleIdentiteLivretA controle = controles.findByReference(reference)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, CONTROLE_INTROUVABLE));
		if (controle.getStatut() != StatutControleIdentite.ATTENTE_RESPONSABLE) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, PAS_EN_ATTENTE_RESPONSABLE);
		}
		controle.validerParResponsable(OffsetDateTime.now(horloge));
		return controle;
	}

	/**
	 * Avant l'éligibilité (CU-31) : dernier contrôle de la cliente, validé et pas encore utilisé. La demande créée
	 * s'y rattache (un contrôle par ouverture) : aucune ouverture sans résultat.
	 */
	@Transactional
	public ControleIdentiteLivretA controleValide(Compte compte) {
		return controles.findFirstByCustomerIdOrderByCreeLeDesc(compte.getCustomerId())
				.filter(ControleIdentiteLivretA::permetUneDemande)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.CONFLICT, SANS_IDENTITE_CONFIRMEE));
	}

	private Suivi suivi(ControleIdentiteLivretA controle) {
		return new Suivi(controle,
				controle.getStatut() == StatutControleIdentite.EN_COURS ? aControler(dossier(controle.getDossierId()))
						: null);
	}

	private Dossier dossier(Long dossierId) {
		return dossiers.findById(dossierId)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, CONTROLE_INTROUVABLE));
	}

	static IdentiteAControler aControler(Dossier dossier) {
		return new IdentiteAControler(dossier.getNomNaissance(), dossier.getPrenoms(), dossier.getDateNaissance(),
				dossier.getNationalite().name());
	}

	/** Champs obligatoires (complément facultatif), e-mail avec « @ », code postal français et mobile. */
	static Coordonnees verifier(Coordonnees saisies) {
		Coordonnees c = saisies == null ? new Coordonnees(null, null, null, null, null, null, null) : saisies;
		String voie = obligatoire(c.voie(), "adresse");
		String codePostal = obligatoire(c.codePostal(), "code postal");
		String ville = obligatoire(c.ville(), "ville");
		String pays = obligatoire(c.paysAdresse(), "pays");
		String email = obligatoire(c.email(), "e-mail");
		String mobile = obligatoire(c.mobile(), "téléphone");
		if (!EMAIL.matcher(email).matches()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, EMAIL_INVALIDE);
		}
		if ("France".equals(pays) && !CODE_POSTAL.matcher(codePostal).matches()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, CODE_POSTAL_INVALIDE);
		}
		if (!MOBILE.matcher(mobile).matches()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MOBILE_INVALIDE);
		}
		String complement = c.complement() == null || c.complement().isBlank() ? null : c.complement().trim();
		return new Coordonnees(voie, complement, codePostal, ville, pays, email, mobile);
	}

	private static String obligatoire(String valeur, String champ) {
		if (valeur == null || valeur.isBlank()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, CHAMP_OBLIGATOIRE + champ + ".");
		}
		return valeur.trim();
	}

}
