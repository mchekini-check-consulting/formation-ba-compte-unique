package com.example.compteunique.livreta;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.ResultatsPiece.Document;
import com.example.compteunique.livreta.ResultatsPiece.Selfie;
import com.example.compteunique.logging.Loggable;

// CU-29 (US-20, avec ex-CU-30 / US-21) : étape « Ma pièce d'identité » de l'ouverture d'un Livret A, seulement
// quand la pièce du dossier client a expiré. La cliente saisit sa nouvelle pièce (CNI, passeport ou titre de
// séjour) ; l'écran demande l'analyse puis la vérification de présence au bouchon Stripe Identity (5 s par essai,
// 3 relances) et transmet ici chaque résultat, qui décide de la suite. Pièce et selfie réussis : la nouvelle pièce
// remplace celle du dossier. Aucune ouverture tant que la pièce du dossier est expirée (LivretAService).
@Service
@Loggable
public class PieceLivretAService {

	static final String PIECE_ENCORE_VALIDE = "Votre pièce d'identité est encore valide : aucune nouvelle pièce n'est demandée.";
	static final String TYPE_OBLIGATOIRE = "Choisissez le type de pièce : carte nationale d'identité, passeport ou titre de séjour.";
	static final String NUMERO_INVALIDE = "Numéro de pièce invalide : 5 à 30 lettres ou chiffres.";
	static final String EXPIRATION_OBLIGATOIRE = "Date d'expiration de la nouvelle pièce obligatoire.";
	static final String EXPIRATION_DEPASSEE = "Cette pièce est expirée : transmettez une pièce en cours de validité.";
	static final String VERIFICATION_INTROUVABLE = "Vérification introuvable";
	static final String RESULTAT_OBLIGATOIRE = "Résultat de la vérification obligatoire";
	static final String TENTATIVES_INVALIDES = "Nombre d'essais invalide";
	static final String SCORE_INVALIDE = "Score de correspondance invalide";
	static final String PAS_DE_PIECE_A_VERIFIER = "Aucune pièce n'attend d'analyse : transmettez d'abord votre pièce.";
	static final String PAS_DE_SELFIE_A_FAIRE = "La vérification de présence n'est pas attendue pour cette pièce.";
	static final String DEMARCHE_SUSPENDUE = "Votre démarche est en cours de traitement : elle ne peut pas être reprise en ligne pour le moment.";

	/** 1 envoi + 3 relances automatiques au plus. */
	static final int ESSAIS_MAX = 4;

	private static final Pattern NUMERO = Pattern.compile("^[A-Z0-9]{5,30}$");

	/** Identité envoyée à l'analyse de la pièce (le bouchon compare la pièce à cette saisie). */
	public record PieceAVerifier(String nomNaissance, String prenoms, LocalDate dateNaissance,
			LocalDate dateExpiration) {
	}

	/** Vérification et, tant que la pièce attend son analyse, l'identité à lui envoyer. */
	public record Suivi(PieceIdentiteLivretA verification, PieceAVerifier aVerifier) {
	}

	/** Pièce à fournir ou non (pièce du dossier expirée), date d'expiration actuelle et dernière vérification. */
	public record EtatPiece(boolean requise, LocalDate dateExpirationActuelle, Suivi suivi) {
	}

	private final CompteService comptes;
	private final DossierRepository dossiers;
	private final PieceIdentiteLivretARepository verifications;
	private final IdentiteLivretAService identites;
	private final Clock horloge;

	@Autowired
	public PieceLivretAService(CompteService comptes, DossierRepository dossiers,
			PieceIdentiteLivretARepository verifications, IdentiteLivretAService identites) {
		this(comptes, dossiers, verifications, identites, Clock.systemDefaultZone());
	}

	PieceLivretAService(CompteService comptes, DossierRepository dossiers,
			PieceIdentiteLivretARepository verifications, IdentiteLivretAService identites, Clock horloge) {
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.verifications = verifications;
		this.identites = identites;
		this.horloge = horloge;
	}

	/** Critères « quand l'étape est demandée » : pièce du dossier expirée à la date du jour. */
	@Transactional(readOnly = true)
	public EtatPiece etat(String jetonSession) {
		Compte compte = comptes.compteCourant(jetonSession);
		Dossier dossier = dossier(compte.getDossierId());
		Optional<Suivi> derniere = verifications.findFirstByCustomerIdOrderByCreeLeDesc(compte.getCustomerId())
				.map(this::suivi);
		return new EtatPiece(dossier.pieceExpireeLe(aujourdhui()), dossier.getDateExpiration(), derniere.orElse(null));
	}

	/**
	 * Nouvelle pièce saisie (type, numéro, date d'expiration) après l'identité confirmée (CU-28). L'analyse est
	 * ensuite demandée par l'écran.
	 */
	@Transactional
	public Suivi soumettre(String jetonSession, TypePiece type, String numero, LocalDate dateExpiration) {
		Compte compte = comptes.compteCourant(jetonSession);
		refuserSiSuspendue(compte);
		Dossier dossier = dossier(compte.getDossierId());
		if (!dossier.pieceExpireeLe(aujourdhui())) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, PIECE_ENCORE_VALIDE);
		}
		identites.controleValide(compte);
		if (type == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, TYPE_OBLIGATOIRE);
		}
		String numeroLu = numero(numero);
		if (dateExpiration == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, EXPIRATION_OBLIGATOIRE);
		}
		if (!dateExpiration.isAfter(aujourdhui())) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, EXPIRATION_DEPASSEE);
		}
		PieceIdentiteLivretA verification = verifications.save(new PieceIdentiteLivretA(dossier.getId(),
				compte.getCustomerId(), type, numeroLu, dateExpiration, OffsetDateTime.now(horloge)));
		return new Suivi(verification, aVerifier(dossier, verification));
	}

	/** Résultat de l'analyse de la pièce (ou absence de réponse après 3 relances), transmis par l'écran. */
	@Transactional
	public Suivi enregistrerDocument(String jetonSession, String reference, Document resultat, Integer tentatives) {
		Compte compte = comptes.compteCourant(jetonSession);
		if (resultat == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, RESULTAT_OBLIGATOIRE);
		}
		int essais = essais(tentatives);
		PieceIdentiteLivretA verification = verification(reference, compte);
		if (verification.getStatut() != StatutPieceLivretA.PIECE_A_VERIFIER) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, PAS_DE_PIECE_A_VERIFIER, true);
		}
		verification.recevoirDocument(resultat, essais, OffsetDateTime.now(horloge));
		return suivi(verification);
	}

	/**
	 * Résultat du selfie (ou absence de réponse après 3 relances), transmis par l'écran. Correspondance : la pièce
	 * remplace celle du dossier client.
	 */
	@Transactional
	public Suivi enregistrerSelfie(String jetonSession, String reference, Selfie resultat, Integer score,
			Integer tentatives) {
		Compte compte = comptes.compteCourant(jetonSession);
		if (resultat == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, RESULTAT_OBLIGATOIRE);
		}
		int essais = essais(tentatives);
		controlerScore(resultat, score);
		PieceIdentiteLivretA verification = verification(reference, compte);
		if (verification.getStatut() != StatutPieceLivretA.SELFIE_A_FAIRE) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, PAS_DE_SELFIE_A_FAIRE, true);
		}
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		verification.recevoirSelfie(resultat, score, essais, maintenant);
		if (verification.getStatut() == StatutPieceLivretA.VALIDE) {
			dossier(verification.getDossierId()).remplacerPiece(verification.getTypePiece(),
					verification.getNumeroPiece(), verification.getDateExpiration(), maintenant);
		}
		return suivi(verification);
	}

	private void refuserSiSuspendue(Compte compte) {
		verifications.findFirstByCustomerIdOrderByCreeLeDesc(compte.getCustomerId())
				.filter(PieceIdentiteLivretA::suspend)
				.ifPresent(v -> {
					throw new DemandeRefusee(HttpStatus.CONFLICT, DEMARCHE_SUSPENDUE, true);
				});
	}

	private PieceIdentiteLivretA verification(String reference, Compte compte) {
		return verifications.findByReferenceAndCustomerId(reference, compte.getCustomerId())
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, VERIFICATION_INTROUVABLE));
	}

	private Suivi suivi(PieceIdentiteLivretA verification) {
		return new Suivi(verification,
				verification.getStatut() == StatutPieceLivretA.PIECE_A_VERIFIER
						? aVerifier(dossier(verification.getDossierId()), verification)
						: null);
	}

	private Dossier dossier(Long dossierId) {
		return dossiers.findById(dossierId)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, VERIFICATION_INTROUVABLE));
	}

	private LocalDate aujourdhui() {
		return LocalDate.now(horloge);
	}

	static PieceAVerifier aVerifier(Dossier dossier, PieceIdentiteLivretA verification) {
		return new PieceAVerifier(dossier.getNomNaissance(), dossier.getPrenoms(), dossier.getDateNaissance(),
				verification.getDateExpiration());
	}

	/** Numéro sans espaces ni tirets, en majuscules : 5 à 30 lettres ou chiffres. */
	static String numero(String saisi) {
		String lu = saisi == null ? "" : saisi.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
		if (!NUMERO.matcher(lu).matches()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, NUMERO_INVALIDE);
		}
		return lu;
	}

	private static int essais(Integer tentatives) {
		if (tentatives == null || tentatives < 1 || tentatives > ESSAIS_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, TENTATIVES_INVALIDES);
		}
		return tentatives;
	}

	/** Correspondance : score d'au moins 90 % ; non-correspondance : score inférieur ; sinon pas de score. */
	private static void controlerScore(Selfie resultat, Integer score) {
		boolean valide = switch (resultat) {
			case CORRESPONDANCE -> score != null && score >= PieceIdentiteLivretA.SEUIL_SELFIE && score <= 100;
			case NON_CORRESPONDANCE -> score != null && score >= 0 && score < PieceIdentiteLivretA.SEUIL_SELFIE;
			case ECHEC_VERIFICATION_PRESENCE, SANS_REPONSE -> score == null;
		};
		if (!valide) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, SCORE_INVALIDE);
		}
	}

}
