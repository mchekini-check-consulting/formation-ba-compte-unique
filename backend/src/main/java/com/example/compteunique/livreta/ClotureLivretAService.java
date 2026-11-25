package com.example.compteunique.livreta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Messagerie;

// CU-296 (US-22B) : Livret A déjà détenu dans une autre banque (réponse « Livret A existant » de CU-31).
// Décision PO du 23/11/2026 : pas de transfert par la banque. La cliente clôture elle-même son ancien livret
// (certificat de clôture à déposer sous 3 mois, vérifié par un conseiller) ou renonce. Relance par e-mail 15 jours
// avant la date limite ; à la date limite dépassée sans certificat validé, la demande est annulée (écran et e-mail).
// Les dates suivent la date métier (CU-380) : en test, avancer la date suffit à déclencher relance et annulation,
// dès que la demande est affichée ou à la tâche quotidienne. Pas encore de back-office : la vérification du
// conseiller est simulée par les boutons de test (LivretATestController).
@Service
@Loggable
public class ClotureLivretAService {

	static final long TAILLE_MAX = 2L * 1024 * 1024;
	static final Set<String> TYPES_ACCEPTES = Set.of("image/png", "image/jpeg", "image/bmp", "image/gif",
			"application/pdf");
	static final String CONSEILLER_SIMULE = "Conseiller (simulation)";

	static final String DEMANDE_INTROUVABLE = LivretAService.DEMANDE_INTROUVABLE;
	static final String CHOIX_OBLIGATOIRE = "Choisissez de clôturer vous-même votre ancien livret ou de renoncer à l'ouverture.";
	static final String PAS_DE_CHOIX_ATTENDU = "Cette demande n'attend pas de choix de votre part.";
	static final String PAS_DE_CERTIFICAT_ATTENDU = "Cette demande n'attend pas de certificat de clôture.";
	static final String DATE_LIMITE_DEPASSEE = "La date limite de dépôt du certificat de clôture est dépassée : votre demande a été annulée.";
	static final String FICHIER_OBLIGATOIRE = "Sélectionnez votre certificat de clôture.";
	static final String FORMAT_REFUSE = "Format non accepté : PNG, JPEG, BMP, GIF ou PDF uniquement";
	static final String TROP_LOURD = "Fichier trop lourd : 2 Mo maximum";
	static final String PAS_DE_CERTIFICAT_A_VERIFIER = "Aucun certificat de clôture à vérifier pour cette demande.";
	static final String MOTIF_OBLIGATOIRE = "Le motif du refus est obligatoire.";

	static final String OBJET_RELANCE = "Votre Livret A : certificat de clôture attendu";
	static final String OBJET_ANNULATION = "Votre demande de Livret A est annulée";

	private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final int MOTIF_LONGUEUR_MAX = 500;

	private final CompteService comptes;
	private final DemandeLivretARepository demandes;
	private final CertificatClotureLivretARepository certificats;
	private final DossierRepository dossiers;
	private final Messagerie messagerie;
	private final DateMetier dateMetier;
	private final String adresseBase;

	public ClotureLivretAService(CompteService comptes, DemandeLivretARepository demandes,
			CertificatClotureLivretARepository certificats, DossierRepository dossiers, Messagerie messagerie,
			DateMetier dateMetier, @Value("${app.liens.adresse-base}") String adresseBase) {
		this.comptes = comptes;
		this.demandes = demandes;
		this.certificats = certificats;
		this.dossiers = dossiers;
		this.messagerie = messagerie;
		this.dateMetier = dateMetier;
		this.adresseBase = adresseBase;
	}

	/** « Je clôture moi-même mon ancien livret » ou « Je renonce à l'ouverture ». */
	@Transactional
	public DemandeLivretA choisirSuite(String jetonSession, String reference, SuiteLivretAExistant choix) {
		DemandeLivretA demande = demandeDe(jetonSession, reference);
		if (choix == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, CHOIX_OBLIGATOIRE);
		}
		if (demande.getStatut() != StatutDemandeLivretA.LIVRET_A_EXISTANT) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, PAS_DE_CHOIX_ATTENDU);
		}
		OffsetDateTime maintenant = maintenant();
		if (choix == SuiteLivretAExistant.RENONCIATION) {
			demande.renoncer(maintenant);
		} else {
			demande.choisirCloture(maintenant);
			appliquerEcheance(demande);
		}
		return demande;
	}

	/** Dépôt du certificat de clôture (PNG, JPEG, BMP, GIF ou PDF, 2 Mo maximum) : à vérifier par un conseiller. */
	@Transactional
	public DemandeLivretA deposerCertificat(String jetonSession, String reference, String nomFichier,
			String typeFichier, byte[] contenu) {
		DemandeLivretA demande = demandeDe(jetonSession, reference);
		appliquerEcheance(demande);
		if (demande.getStatut() == StatutDemandeLivretA.ANNULEE) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DATE_LIMITE_DEPASSEE, true);
		}
		if (demande.getStatut() != StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, PAS_DE_CERTIFICAT_ATTENDU);
		}
		controlerFichier(typeFichier, contenu);
		certificats.save(new CertificatClotureLivretA(demande.getId(), contenu, nomFichierValide(nomFichier),
				typeFichier, maintenant()));
		demande.certificatDepose();
		return demande;
	}

	/** Dernier certificat déposé pour la demande. */
	@Transactional(readOnly = true)
	public Optional<CertificatClotureLivretA> dernierCertificat(DemandeLivretA demande) {
		if (demande.getId() == null || demande.getSuiteChoisie() != SuiteLivretAExistant.CLOTURE_PAR_CLIENTE) {
			return Optional.empty();
		}
		return certificats.findFirstByDemandeIdOrderByIdDesc(demande.getId());
	}

	/**
	 * Relance 15 jours avant la date limite, annulation une fois la date limite dépassée, chacune avec son e-mail.
	 * Appelée à chaque affichage de la demande et par la tâche quotidienne.
	 */
	@Transactional
	public void appliquerEcheance(DemandeLivretA demande) {
		LocalDate aujourdhui = dateMetier.aujourdhui();
		if (demande.dateLimiteDepassee(aujourdhui)) {
			demande.annuler(maintenant());
			envoyerEmail(demande, OBJET_ANNULATION, texteAnnulation(demande));
		} else if (demande.aRelancer(aujourdhui)) {
			demande.relancer(maintenant());
			envoyerEmail(demande, OBJET_RELANCE, texteRelance(demande));
		}
	}

	/** Tâche quotidienne : relances et annulations du jour. */
	@Scheduled(cron = "${app.livret-a.cloture-cron:0 0 7 * * *}")
	@Transactional
	public int traiterLesEcheances() {
		List<DemandeLivretA> concernees = demandes.findByStatutIn(
				List.of(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, StatutDemandeLivretA.CERTIFICAT_A_VERIFIER));
		concernees.forEach(this::appliquerEcheance);
		return concernees.size();
	}

	/** Test : « Simuler la validation du certificat par un conseiller » ; l'ouverture reprend. */
	@Transactional
	public DemandeLivretA simulerValidation(String reference) {
		DemandeLivretA demande = demandeAVerifier(reference);
		certificatAVerifier(demande).valider(CONSEILLER_SIMULE, maintenant());
		demande.certificatValide();
		return demande;
	}

	/** Test : « Simuler le refus du certificat par un conseiller », avec un motif montré à la cliente. */
	@Transactional
	public DemandeLivretA simulerRefus(String reference, String motif) {
		String motifLu = motif == null ? "" : motif.trim();
		if (motifLu.isEmpty()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MOTIF_OBLIGATOIRE);
		}
		DemandeLivretA demande = demandeAVerifier(reference);
		certificatAVerifier(demande).refuser(CONSEILLER_SIMULE,
				motifLu.length() > MOTIF_LONGUEUR_MAX ? motifLu.substring(0, MOTIF_LONGUEUR_MAX) : motifLu,
				maintenant());
		demande.certificatRefuse();
		return demande;
	}

	private DemandeLivretA demandeAVerifier(String reference) {
		DemandeLivretA demande = demandes.findByReference(reference)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, DEMANDE_INTROUVABLE));
		appliquerEcheance(demande);
		if (demande.getStatut() != StatutDemandeLivretA.CERTIFICAT_A_VERIFIER) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, PAS_DE_CERTIFICAT_A_VERIFIER);
		}
		return demande;
	}

	private CertificatClotureLivretA certificatAVerifier(DemandeLivretA demande) {
		return certificats.findFirstByDemandeIdOrderByIdDesc(demande.getId())
				.filter(CertificatClotureLivretA::estAVerifier)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.CONFLICT, PAS_DE_CERTIFICAT_A_VERIFIER));
	}

	private DemandeLivretA demandeDe(String jetonSession, String reference) {
		Compte compte = comptes.compteCourant(jetonSession);
		return demandes.findByReferenceAndCustomerId(reference, compte.getCustomerId())
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, DEMANDE_INTROUVABLE));
	}

	/** Mêmes règles que la pièce d'identité (CU-29), contrôlées aussi à l'écran avant l'envoi. */
	static void controlerFichier(String typeFichier, byte[] contenu) {
		if (contenu == null || contenu.length == 0) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, FICHIER_OBLIGATOIRE);
		}
		if (typeFichier == null || !TYPES_ACCEPTES.contains(typeFichier)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, FORMAT_REFUSE);
		}
		if (contenu.length > TAILLE_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, TROP_LOURD);
		}
	}

	private static String nomFichierValide(String nom) {
		String lu = nom == null || nom.isBlank() ? "certificat" : nom.trim();
		return lu.length() > 255 ? lu.substring(0, 255) : lu;
	}

	private void envoyerEmail(DemandeLivretA demande, String objet, String texte) {
		dossiers.findById(demande.getDossierId()).map(Dossier::getEmail).filter(email -> !email.isBlank())
				.ifPresent(email -> messagerie.envoyerEmail(email, objet, texte));
	}

	String texteRelance(DemandeLivretA demande) {
		return "Bonjour,\n\nNous n'avons pas encore reçu le certificat de clôture de votre ancien Livret A. "
				+ "Déposez-le depuis votre espace avant le " + jour(demande.getDateLimiteCertificat())
				+ " : passé cette date, votre demande d'ouverture de Livret A sera annulée.\n\n" + lien()
				+ "\n\nHi-Tech Bank";
	}

	String texteAnnulation(DemandeLivretA demande) {
		return "Bonjour,\n\nAucun certificat de clôture validé n'a été reçu avant le "
				+ jour(demande.getDateLimiteCertificat())
				+ " : votre demande d'ouverture de Livret A est annulée. Vous pouvez faire une nouvelle demande "
				+ "depuis votre espace.\n\n" + lien() + "\n\nHi-Tech Bank";
	}

	private String lien() {
		return adresseBase + "/livret-a";
	}

	static String jour(LocalDate date) {
		return date == null ? "" : date.format(JOUR);
	}

	private OffsetDateTime maintenant() {
		return dateMetier.maintenant().toOffsetDateTime();
	}

}
