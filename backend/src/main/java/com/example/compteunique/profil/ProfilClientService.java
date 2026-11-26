package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.logging.Loggable;

/**
 * CU-10 (US-05) : profil client (KYC) rempli une fois, puis réutilisé pour toutes les souscriptions de PER et
 * d'assurance vie tant qu'il a moins de 12 mois (date métier, ADR-001). Le statut est calculé à chaque lecture :
 * sans version, « à compléter » ; à partir de la date de revalidation, « à revalider » ; sinon « validé ».
 * <p>
 * Les stories de souscription (PER-01, AV-01) appellent {@link #souscriptionAutorisee(UUID)}.
 */
@Service
@Loggable
public class ProfilClientService {

	static final String VERSION_QUESTIONNAIRE = "V1";
	// Règle de la maquette CU-10, à faire confirmer par la conformité.
	static final String VERSION_REGLES = "V1";
	static final String CONSENTEMENT = "UTILISATION_REPONSES";
	static final String VERSION_TEXTE_CONSENTEMENT = "V1";

	static final String REPONDEZ = "Répondez à cette question.";
	static final String MESSAGE_PLAFOND = "Saisissez le montant indiqué sur votre avis d'imposition";
	static final String MESSAGE_ANNEE_PLAFOND = "Saisissez l'année indiquée sur votre avis d'imposition.";
	static final String MESSAGE_CONSENTEMENT = "Cochez cette case pour valider votre profil.";

	private static final Map<String, Set<String>> CODES = Map.of(
			"statutMatrimonial", Set.of("MARIE", "PACSE", "CONCUBINAGE", "CELIBATAIRE", "VEUF", "DIVORCE"),
			"situationProfessionnelle", Set.of("SALARIE", "INDEPENDANT", "RETRAITE", "SANS_ACTIVITE"),
			"patrimoine", Set.of("MOINS_10K", "DE_10K_A_100K", "PLUS_100K"),
			"produitsDetenus", Set.of("AUCUN", "LIVRETS", "AV_EUROS", "AV_OU_UC"),
			"objectif", Set.of("RETRAITE", "MOYEN_TERME", "TRANSMETTRE", "AUTRE"),
			"horizon", Set.of("MOINS_3_ANS", "DE_3_A_8_ANS", "PLUS_8_ANS"),
			"reactionBaisse", Set.of("RETIRER", "ATTENDRE", "INVESTIR"),
			"perteAcceptable", Set.of("AUCUNE", "JUSQU_A_10", "DE_10_A_30", "PLUS_30"),
			"financeDurable", Set.of("AUCUNE", "PART_MINIMALE", "SANS_PRECISION"));

	private static final Set<Integer> TMI = Set.of(0, 11, 30, 41, 45);

	public enum Statut {
		VALIDE, A_COMPLETER, A_REVALIDER
	}

	/** Statut et dernière version (vide sans profil), qui pré-remplit le questionnaire. */
	public record Etat(Statut statut, Optional<ProfilKyc> profil) {
	}

	private final CompteService comptes;
	private final DateMetier dateMetier;
	private final ProfilKycRepository profils;
	private final ConsentementRepository consentements;
	private final PublicationStatutProfil publication;

	public ProfilClientService(CompteService comptes, DateMetier dateMetier, ProfilKycRepository profils,
			ConsentementRepository consentements, PublicationStatutProfil publication) {
		this.comptes = comptes;
		this.dateMetier = dateMetier;
		this.profils = profils;
		this.consentements = consentements;
		this.publication = publication;
	}

	/** Pour PER-01 et AV-01 : souscription possible seulement avec un profil validé de moins de 12 mois. */
	@Transactional // CU-415 : la lecture peut enregistrer « à revalider »
	public boolean souscriptionAutorisee(UUID customerId) {
		return statut(customerId) == Statut.VALIDE;
	}

	@Transactional // CU-415 : la lecture peut enregistrer « à revalider »
	public Statut statut(UUID customerId) {
		return etat(customerId).statut();
	}

	/** CU-314 / CU-338 : profil validé de moins de 12 mois (vide sinon), lu par le module Gestion. */
	@Transactional // CU-415 : la lecture peut enregistrer « à revalider »
	public Optional<ProfilKyc> profilValide(UUID customerId) {
		Etat etat = etat(customerId);
		return etat.statut() == Statut.VALIDE ? etat.profil() : Optional.empty();
	}

	@Transactional // CU-415 : la lecture peut enregistrer « à revalider »
	public Etat etat(String jetonSession) {
		return etat(customerId(jetonSession));
	}

	/** Toutes les versions, de la plus récente à la plus ancienne. */
	@Transactional(readOnly = true)
	public List<ProfilKyc> versions(String jetonSession) {
		return profils.findByCustomerIdOrderByVersionDesc(customerId(jetonSession));
	}

	/** Valide le questionnaire : nouvelle version et consentement tracé, ou refus listant les champs à corriger. */
	@Transactional
	public ProfilKyc valider(String jetonSession, Reponses reponses, Boolean consentement) {
		UUID customerId = customerId(jetonSession);
		OffsetDateTime maintenant = dateMetier.maintenant().toOffsetDateTime();
		Map<String, String> erreurs = erreurs(reponses, consentement, maintenant.getYear());
		if (!erreurs.isEmpty()) {
			throw new ProfilIncomplet(erreurs);
		}
		int version = profils.findFirstByCustomerIdOrderByVersionDesc(customerId).map(ProfilKyc::getVersion)
				.orElse(0) + 1;
		int score = switch (reponses.reactionBaisse()) {
			case "RETIRER" -> 1;
			case "ATTENDRE" -> 2;
			default -> 3;
		};
		String profil = List.of("PRUDENT", "EQUILIBRE", "DYNAMIQUE").get(score - 1);
		Statut avant = etat(customerId).statut();
		consentements.save(new Consentement(customerId, CONSENTEMENT, true, VERSION_TEXTE_CONSENTEMENT, maintenant));
		ProfilKyc enregistre = profils.save(new ProfilKyc(customerId, version, VERSION_QUESTIONNAIRE, maintenant,
				reponses, profil, score, VERSION_REGLES));
		// CU-382 : « statut du profil modifié » enregistré dans la boîte d'envoi, dans la même transaction.
		publication.changer(customerId, avant, Statut.VALIDE);
		return enregistre;
	}

	private Etat etat(UUID customerId) {
		Optional<ProfilKyc> dernier = profils.findFirstByCustomerIdOrderByVersionDesc(customerId);
		OffsetDateTime maintenant = dateMetier.maintenant().toOffsetDateTime();
		Statut statut = dernier.map(p -> maintenant.isBefore(p.getRevaliderLe()) ? Statut.VALIDE : Statut.A_REVALIDER)
				.orElse(Statut.A_COMPLETER);
		if (statut == Statut.A_REVALIDER) {
			// CU-415 : si la tâche de la nuit n'est pas encore passée, le statut et l'événement sont enregistrés ici,
			// une seule fois (rien n'est publié si « à revalider » l'est déjà).
			publication.changer(customerId, Statut.VALIDE, Statut.A_REVALIDER);
		}
		return new Etat(statut, dernier);
	}

	private UUID customerId(String jetonSession) {
		return comptes.compteCourant(jetonSession).getCustomerId();
	}

	static Map<String, String> erreurs(Reponses reponses, Boolean consentement, int annee) {
		Reponses r = reponses != null ? reponses
				: new Reponses(null, null, null, null, null, null, null, null, null, null, null, null, null);
		Map<String, String> erreurs = new LinkedHashMap<>();
		code(erreurs, "statutMatrimonial", r.statutMatrimonial());
		code(erreurs, "situationProfessionnelle", r.situationProfessionnelle());
		code(erreurs, "patrimoine", r.patrimoine());
		code(erreurs, "produitsDetenus", r.produitsDetenus());
		code(erreurs, "objectif", r.objectif());
		code(erreurs, "horizon", r.horizon());
		code(erreurs, "reactionBaisse", r.reactionBaisse());
		code(erreurs, "perteAcceptable", r.perteAcceptable());
		code(erreurs, "financeDurable", r.financeDurable());
		if (r.plafondRetraiteEuros() == null || r.plafondRetraiteEuros() < 0) {
			erreurs.put("plafondRetraiteEuros", MESSAGE_PLAFOND);
		}
		if (r.plafondRetraiteAnnee() == null || Math.abs(r.plafondRetraiteAnnee() - annee) > 1) {
			erreurs.put("plafondRetraiteAnnee", MESSAGE_ANNEE_PLAFOND);
		}
		if (r.tmi() == null || !TMI.contains(r.tmi())) {
			erreurs.put("tmi", REPONDEZ);
		}
		Integer depart = r.anneeDepartRetraite();
		if (depart == null || depart < annee || depart > annee + 50) {
			erreurs.put("anneeDepartRetraite", "Saisissez une année entre " + annee + " et " + (annee + 50) + ".");
		}
		if (!Boolean.TRUE.equals(consentement)) {
			erreurs.put("consentement", MESSAGE_CONSENTEMENT);
		}
		return erreurs;
	}

	private static void code(Map<String, String> erreurs, String champ, String valeur) {
		if (valeur == null || !CODES.get(champ).contains(valeur)) {
			erreurs.put(champ, REPONDEZ);
		}
	}

	/** Questionnaire incomplet ou invalide : le profil reste « à compléter », rien n'est enregistré. */
	public static class ProfilIncomplet extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final transient Map<String, String> champs;

		ProfilIncomplet(Map<String, String> champs) {
			super("Profil à compléter");
			this.champs = champs; // ordre du questionnaire conservé
		}

		public Map<String, String> getChamps() {
			return champs;
		}

	}

}
