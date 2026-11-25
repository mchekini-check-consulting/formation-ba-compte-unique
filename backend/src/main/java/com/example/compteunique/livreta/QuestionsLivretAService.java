package com.example.compteunique.livreta;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.profil.Consentements;

// CU-32 (US-23) : étape « Questions et données » de l'ouverture d'un Livret A, après l'identité (CU-28) et la
// pièce (CU-29), avant l'éligibilité (CU-31) où les données partent à l'administration fiscale. Questionnaire
// obligatoire (objet de l'épargne, origine des fonds) et texte d'information sur la transmission des données,
// à accepter une fois par version : l'acceptation est tracée dans le registre des consentements de CU-10.
// Aucune ouverture sans questionnaire rempli pour cette ouverture (LivretAService).
@Service
@Loggable
public class QuestionsLivretAService {

	/** Type de consentement dans le registre de CU-10. */
	static final String TYPE_CONSENTEMENT = "INFORMATION_DONNEES_LIVRET_A";

	/** Version en vigueur du texte d'information : la changer redemande l'acceptation à chaque cliente. */
	static final String VERSION_TEXTE = "V1";

	/** Texte provisoire (V1), en attendant le texte définitif du juridique / DPO (point à valider de CU-32). */
	static final String TEXTE_INFORMATION = "Pour vérifier que vous ne détenez pas déjà un Livret A dans une autre "
			+ "banque, Hi-Tech Bank transmet à l'administration fiscale vos nom, prénom, sexe, date et lieu de "
			+ "naissance. Ces données servent uniquement à ce contrôle de multidétention, imposé par la "
			+ "réglementation (article L221-3 du Code monétaire et financier).";

	static final int PRECISION_MAX = 200;

	static final String OBJET_OBLIGATOIRE = "Choisissez l'objet de votre épargne.";
	static final String ORIGINE_OBLIGATOIRE = "Choisissez l'origine des fonds.";
	static final String PRECISION_OBLIGATOIRE = "Précisez l'origine des fonds.";
	static final String PRECISION_TROP_LONGUE = "Précision : 200 caractères au maximum.";
	static final String TEXTE_REFUSE = "Sans votre accord, nous ne pouvons pas vérifier auprès de l'administration "
			+ "fiscale que vous ne détenez pas déjà un Livret A. Vous pourrez reprendre votre demande plus tard.";
	static final String SANS_QUESTIONNAIRE = "Répondez d'abord aux questions et acceptez le texte d'information sur vos données.";

	/** Étape vue par la cliente : texte et version, texte à accepter ou non, questionnaire déjà rempli. */
	public record Etape(String versionTexte, String texte, boolean texteAAccepter, QuestionnaireLivretA questionnaire) {
	}

	private final CompteService comptes;
	private final DossierRepository dossiers;
	private final QuestionnaireLivretARepository questionnaires;
	private final IdentiteLivretAService identites;
	private final Consentements consentements;
	private final Clock horloge;

	@Autowired
	public QuestionsLivretAService(CompteService comptes, DossierRepository dossiers,
			QuestionnaireLivretARepository questionnaires, IdentiteLivretAService identites,
			Consentements consentements) {
		this(comptes, dossiers, questionnaires, identites, consentements, Clock.systemDefaultZone());
	}

	QuestionsLivretAService(CompteService comptes, DossierRepository dossiers,
			QuestionnaireLivretARepository questionnaires, IdentiteLivretAService identites,
			Consentements consentements, Clock horloge) {
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.questionnaires = questionnaires;
		this.identites = identites;
		this.consentements = consentements;
		this.horloge = horloge;
	}

	/**
	 * Critères « version en vigueur déjà acceptée » et « nouvelle version » : le texte n'est redemandé que si la
	 * cliente n'a pas accepté la version en vigueur. Questionnaire : celui de cette ouverture, s'il est rempli.
	 */
	@Transactional(readOnly = true)
	public Etape etat(String jetonSession) {
		Compte compte = comptes.compteCourant(jetonSession);
		return new Etape(VERSION_TEXTE, TEXTE_INFORMATION, !texteAccepte(compte),
				questionnaireUtilisable(compte).orElse(null));
	}

	/**
	 * Questionnaire complet et texte accepté (s'il est demandé) : réponses enregistrées avec la date et l'heure,
	 * acceptation tracée avec la version du texte. Sinon refus, sans rien enregistrer.
	 */
	@Transactional
	public QuestionnaireLivretA repondre(String jetonSession, ObjetEpargne objet, OrigineFonds origine,
			String precision, Boolean texteAccepteParLaCliente) {
		Compte compte = comptes.compteCourant(jetonSession);
		identites.controleValide(compte);
		Dossier dossier = dossiers.findById(compte.getDossierId())
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, LivretAService.DEMANDE_INTROUVABLE));
		if (dossier.pieceExpireeLe(LocalDate.now(horloge))) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, LivretAService.PIECE_EXPIREE);
		}
		if (objet == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, OBJET_OBLIGATOIRE);
		}
		if (origine == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, ORIGINE_OBLIGATOIRE);
		}
		String precisionLue = precision(origine, precision);
		boolean texteAAccepter = !texteAccepte(compte);
		if (texteAAccepter && !Boolean.TRUE.equals(texteAccepteParLaCliente)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, TEXTE_REFUSE);
		}
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		if (texteAAccepter) {
			consentements.accepter(compte.getCustomerId(), TYPE_CONSENTEMENT, VERSION_TEXTE, maintenant);
		}
		return questionnaires.save(new QuestionnaireLivretA(dossier.getId(), compte.getCustomerId(), objet, origine,
				precisionLue, VERSION_TEXTE, maintenant));
	}

	/**
	 * Règle d'ouverture : questionnaire rempli pour cette ouverture, avec la version en vigueur du texte
	 * acceptée. Sinon, aucune donnée n'est transmise à l'administration fiscale.
	 */
	public QuestionnaireLivretA questionnaireValide(Compte compte) {
		return questionnaireUtilisable(compte)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.CONFLICT, SANS_QUESTIONNAIRE));
	}

	private Optional<QuestionnaireLivretA> questionnaireUtilisable(Compte compte) {
		return questionnaires.findFirstByCustomerIdOrderByReponduLeDescIdDesc(compte.getCustomerId())
				.filter(QuestionnaireLivretA::permetUneDemande)
				.filter(q -> VERSION_TEXTE.equals(q.getVersionTexteInformation()));
	}

	private boolean texteAccepte(Compte compte) {
		return consentements.aAccepte(compte.getCustomerId(), TYPE_CONSENTEMENT, VERSION_TEXTE);
	}

	/** Précision seulement pour l'origine « Autre » : obligatoire, 200 caractères au plus ; ignorée sinon. */
	static String precision(OrigineFonds origine, String saisie) {
		if (origine != OrigineFonds.AUTRE) {
			return null;
		}
		String lue = saisie == null ? "" : saisie.strip();
		if (lue.isEmpty()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, PRECISION_OBLIGATOIRE);
		}
		if (lue.length() > PRECISION_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, PRECISION_TROP_LONGUE);
		}
		return lue;
	}

}
