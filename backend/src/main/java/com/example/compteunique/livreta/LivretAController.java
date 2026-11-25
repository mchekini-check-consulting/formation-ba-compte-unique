package com.example.compteunique.livreta;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.livreta.IdentiteLivretAService.Identite;
import com.example.compteunique.livreta.PieceLivretAService.EtatPiece;
import com.example.compteunique.livreta.ResultatsPiece.Document;
import com.example.compteunique.livreta.ResultatsPiece.Selfie;

// CU-31 (US-22) : ouverture d'un Livret A par la cliente connectée (session obligatoire), contrôle de
// multidétention.
// GET  /livret-a/demande                                 : {"demande"} dernière demande, null s'il n'y en a pas.
// POST /livret-a/demandes                                : {"attestation", "partageDetailAutorise"} -> demande (201).
// POST /livret-a/demandes/{reference}/reponse-fiscale    : {"interrogationId", "resultat", "detail"?} -> demande.
// CU-296 (US-22B) : Livret A déjà détenu dans une autre banque.
// POST /livret-a/demandes/{reference}/suite              : {"choix": CLOTURE_PAR_CLIENTE | RENONCIATION} -> demande.
// POST /livret-a/demandes/{reference}/certificats        : multipart « fichier » (PNG, JPEG, BMP, GIF, PDF, 2 Mo) -> demande (201).
// CU-28 (US-19) : étape « Mon identité », avant l'éligibilité.
// GET  /livret-a/identite                                : {"identite", "controle"} pré-remplie depuis le dossier.
// POST /livret-a/identite                                : {"coordonnees", "residenceFiscaleConfirmee"} -> contrôle (201).
// POST /livret-a/identite/{reference}/resultat           : {"resultat", "tentatives"} -> contrôle.
// CU-29 (US-20, avec ex-CU-30) : étape « Ma pièce d'identité », seulement si la pièce du dossier a expiré.
// GET  /livret-a/piece                                   : {"requise", "dateExpirationActuelle", "verification"}.
// POST /livret-a/piece                                   : {"typePiece", "numeroPiece", "dateExpiration"} -> vérification (201).
// POST /livret-a/piece/{reference}/document              : {"resultat", "tentatives"} -> vérification.
// POST /livret-a/piece/{reference}/selfie                : {"resultat", "score", "tentatives"} -> vérification.
// Refus : {"message", "termine"} avec le message affiché à la cliente.
@RestController
@RequestMapping("/livret-a")
public class LivretAController {

	static final String SANS_COMPTE = "Aucun compte courant";
	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final LivretAService service;
	private final IdentiteLivretAService identites;
	private final PieceLivretAService pieces;
	private final ClotureLivretAService clotures;

	public LivretAController(LivretAService service, IdentiteLivretAService identites, PieceLivretAService pieces,
			ClotureLivretAService clotures) {
		this.service = service;
		this.identites = identites;
		this.pieces = pieces;
		this.clotures = clotures;
	}

	/** CU-28 : identité pré-remplie et dernier contrôle (null s'il n'y en a pas). */
	public record EtapeIdentite(Identite identite, ControleIdentiteVue controle) {
	}

	/** CU-28 : coordonnées (éventuellement corrigées) et confirmation de la résidence fiscale. */
	public record Confirmation(Coordonnees coordonnees, Boolean residenceFiscaleConfirmee) {
	}

	/** CU-28 : résultat du contrôle sanctions/PPE et nombre d'essais (1 à 4). */
	public record ResultatControle(ResultatSanctions resultat, Integer tentatives) {
	}

	@GetMapping("/identite")
	public EtapeIdentite identite(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return new EtapeIdentite(identites.identite(jetonSession),
				identites.dernierControle(jetonSession).map(ControleIdentiteVue::de).orElse(null));
	}

	@PostMapping("/identite")
	public ResponseEntity<ControleIdentiteVue> confirmerIdentite(
			@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Confirmation confirmation) {
		Confirmation lue = confirmation == null ? new Confirmation(null, null) : confirmation;
		return ResponseEntity.status(HttpStatus.CREATED).body(ControleIdentiteVue
				.de(identites.confirmer(jetonSession, lue.coordonnees(), lue.residenceFiscaleConfirmee())));
	}

	@PostMapping("/identite/{reference}/resultat")
	public ControleIdentiteVue resultatControle(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) ResultatControle resultat) {
		ResultatControle lu = resultat == null ? new ResultatControle(null, null) : resultat;
		return ControleIdentiteVue
				.de(identites.enregistrerResultat(jetonSession, reference, lu.resultat(), lu.tentatives()));
	}

	/** CU-29 : nouvelle pièce à fournir ou non, date d'expiration actuelle et dernière vérification. */
	public record EtapePiece(boolean requise, LocalDate dateExpirationActuelle, PieceLivretAVue verification) {
	}

	/** CU-29 : nouvelle pièce saisie par la cliente. */
	public record NouvellePiece(TypePiece typePiece, String numeroPiece, LocalDate dateExpiration) {
	}

	/** CU-29 : résultat de l'analyse de la pièce et nombre d'essais (1 à 4). */
	public record ResultatDocument(Document resultat, Integer tentatives) {
	}

	/** CU-29 : résultat du selfie, score de correspondance et nombre d'essais (1 à 4). */
	public record ResultatSelfie(Selfie resultat, Integer score, Integer tentatives) {
	}

	@GetMapping("/piece")
	public EtapePiece piece(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		EtatPiece etat = pieces.etat(jetonSession);
		return new EtapePiece(etat.requise(), etat.dateExpirationActuelle(),
				etat.suivi() == null ? null : PieceLivretAVue.de(etat.suivi()));
	}

	@PostMapping("/piece")
	public ResponseEntity<PieceLivretAVue> soumettrePiece(
			@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) NouvellePiece piece) {
		NouvellePiece lue = piece == null ? new NouvellePiece(null, null, null) : piece;
		return ResponseEntity.status(HttpStatus.CREATED).body(PieceLivretAVue
				.de(pieces.soumettre(jetonSession, lue.typePiece(), lue.numeroPiece(), lue.dateExpiration())));
	}

	@PostMapping("/piece/{reference}/document")
	public PieceLivretAVue resultatDocument(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) ResultatDocument resultat) {
		ResultatDocument lu = resultat == null ? new ResultatDocument(null, null) : resultat;
		return PieceLivretAVue.de(pieces.enregistrerDocument(jetonSession, reference, lu.resultat(), lu.tentatives()));
	}

	@PostMapping("/piece/{reference}/selfie")
	public PieceLivretAVue resultatSelfie(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) ResultatSelfie resultat) {
		ResultatSelfie lu = resultat == null ? new ResultatSelfie(null, null, null) : resultat;
		return PieceLivretAVue
				.de(pieces.enregistrerSelfie(jetonSession, reference, lu.resultat(), lu.score(), lu.tentatives()));
	}

	public record Etat(DemandeLivretAVue demande) {
	}

	public record Eligibilite(Boolean attestation, Boolean partageDetailAutorise) {
	}

	/** CU-296 : detail transmis tel que reçu de l'administration fiscale (null s'il n'est pas communiqué). */
	public record ResultatInterrogation(String interrogationId, ReponseFiscale resultat, DetailLivretA detail) {

		public ResultatInterrogation(String interrogationId, ReponseFiscale resultat) {
			this(interrogationId, resultat, null);
		}
	}

	/** CU-296 : « Je clôture moi-même mon ancien livret » ou « Je renonce à l'ouverture ». */
	public record ChoixSuite(SuiteLivretAExistant choix) {
	}

	public record Refus(String message, boolean termine) {
	}

	@GetMapping("/demande")
	public Etat etat(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		return new Etat(service.etat(jetonSession).map(DemandeLivretAVue::de).orElse(null));
	}

	@PostMapping("/demandes")
	public ResponseEntity<DemandeLivretAVue> demander(
			@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Eligibilite eligibilite) {
		Eligibilite lue = eligibilite == null ? new Eligibilite(null, null) : eligibilite;
		return ResponseEntity.status(HttpStatus.CREATED).body(DemandeLivretAVue
				.de(service.demander(jetonSession, lue.attestation(), lue.partageDetailAutorise())));
	}

	@PostMapping("/demandes/{reference}/reponse-fiscale")
	public DemandeLivretAVue reponseFiscale(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) ResultatInterrogation resultat) {
		ResultatInterrogation lu = resultat == null ? new ResultatInterrogation(null, null) : resultat;
		return DemandeLivretAVue.de(service.enregistrerReponse(jetonSession, reference, lu.interrogationId(),
				lu.resultat(), lu.detail()));
	}

	@PostMapping("/demandes/{reference}/suite")
	public DemandeLivretAVue choisirSuite(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@PathVariable String reference, @RequestBody(required = false) ChoixSuite choix) {
		return DemandeLivretAVue.de(service.suivi(
				clotures.choisirSuite(jetonSession, reference, choix == null ? null : choix.choix())));
	}

	// Pas de « consumes » : sans session, la réponse doit rester 401 quel que soit le format envoyé
	// (RoutesProtegeesIT) ; sans fichier multipart, le service refuse avec un message clair.
	@PostMapping("/demandes/{reference}/certificats")
	public ResponseEntity<DemandeLivretAVue> deposerCertificat(
			@CookieValue(name = COOKIE, required = false) String jetonSession, @PathVariable String reference,
			@RequestParam(name = "fichier", required = false) MultipartFile fichier) throws IOException {
		DemandeLivretA demande = clotures.deposerCertificat(jetonSession, reference,
				fichier == null ? null : fichier.getOriginalFilename(),
				fichier == null ? null : fichier.getContentType(), fichier == null ? null : fichier.getBytes());
		return ResponseEntity.status(HttpStatus.CREATED).body(DemandeLivretAVue.de(service.suivi(demande)));
	}

	/** Sans session : 401 sans corps ; sans compte courant : 404 ; demande refusée : son code et son message. */
	@ExceptionHandler({ DemandeRefusee.class, ClienteNonConnecteeException.class, CompteInaccessibleException.class })
	public ResponseEntity<Refus> refus(RuntimeException erreur) {
		return reponseDe(erreur);
	}

	static ResponseEntity<Refus> reponseDe(RuntimeException erreur) {
		if (erreur instanceof DemandeRefusee demande) {
			return ResponseEntity.status(demande.getStatut()).body(new Refus(demande.getMessage(), demande.isTermine()));
		}
		if (erreur instanceof CompteInaccessibleException) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(SANS_COMPTE, true));
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

}
