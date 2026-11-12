package com.example.compteunique.livreta;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;

// CU-31 (US-22) : une même personne ne détient qu'un seul Livret A, toutes banques confondues.
// Cliente connectée (son identité est lue dans son dossier). Contrôles internes : Livret A déjà ouvert chez
// nous, ou demande déjà en cours, pour la même identité (même avec une pièce renouvelée). Puis attestation sur
// l'honneur obligatoire et choix de partage du détail, enregistrés avec la date et l'heure. L'écran interroge
// l'administration fiscale (bouchon CU-295) et transmet ici sa réponse ; aucun Livret A n'est ouvert avant.
// Sans réponse sous 2 jours ouvrés : la demande reste en attente et le conseiller est alerté.
@Service
@Loggable
public class LivretAService {

	static final String ATTESTATION_OBLIGATOIRE = "Vous devez attester sur l'honneur ne pas détenir de Livret A dans un autre établissement.";
	static final String CHOIX_PARTAGE_OBLIGATOIRE = "Indiquez si vous autorisez l'administration fiscale à communiquer le détail d'un éventuel Livret A existant.";
	static final String LIVRET_A_DETENU = "Vous détenez déjà un Livret A chez Hi-Tech Bank : consultez-le depuis votre espace.";
	static final String DEMANDE_EN_COURS = "Une demande d'ouverture de Livret A est déjà en cours.";
	static final String DEMANDE_INTROUVABLE = "Demande introuvable";
	static final String DEMANDE_TERMINEE = "Cette demande n'attend plus de réponse de l'administration fiscale.";
	static final String NON_TRANSMISE = "Cette demande n'a pas encore été transmise à l'administration fiscale.";
	static final String REPONSE_OBLIGATOIRE = "Réponse de l'administration fiscale obligatoire";

	/** Identité transmise à l'administration fiscale (critère : nom, prénom, sexe, date et lieu de naissance). */
	public record Transmission(String nom, String prenoms, String sexe, LocalDate dateNaissance,
			String lieuNaissance) {
	}

	/** Demande et, tant qu'elle est en cours de vérification, l'identité à transmettre. */
	public record Suivi(DemandeLivretA demande, Transmission transmission) {
	}

	private final CompteService comptes;
	private final DossierRepository dossiers;
	private final DemandeLivretARepository demandes;
	private final Clock horloge;

	@Autowired
	public LivretAService(CompteService comptes, DossierRepository dossiers, DemandeLivretARepository demandes) {
		this(comptes, dossiers, demandes, Clock.systemDefaultZone());
	}

	LivretAService(CompteService comptes, DossierRepository dossiers, DemandeLivretARepository demandes,
			Clock horloge) {
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.demandes = demandes;
		this.horloge = horloge;
	}

	/** Dernière demande de la cliente connectée, vide si elle n'en a pas. */
	@Transactional(readOnly = true)
	public Optional<Suivi> etat(String jetonSession) {
		Compte compte = comptes.compteCourant(jetonSession);
		return demandes.findFirstByCustomerIdOrderByCreeeLeDesc(compte.getCustomerId()).map(this::suivi);
	}

	/** Étape d'éligibilité validée : contrôles internes, attestation et choix de partage enregistrés. */
	@Transactional
	public Suivi demander(String jetonSession, Boolean attestation, Boolean partageDetailAutorise) {
		Compte compte = comptes.compteCourant(jetonSession);
		Dossier dossier = dossier(compte.getDossierId());
		controlerMultidetentionInterne(dossier);
		if (!Boolean.TRUE.equals(attestation)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, ATTESTATION_OBLIGATOIRE);
		}
		if (partageDetailAutorise == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, CHOIX_PARTAGE_OBLIGATOIRE);
		}
		DemandeLivretA demande = demandes.save(new DemandeLivretA(dossier.getId(), compte.getCustomerId(),
				partageDetailAutorise, OffsetDateTime.now(horloge)));
		return new Suivi(demande, transmission(dossier));
	}

	/** Réponse de l'administration fiscale transmise par l'écran, pour une demande de la cliente. */
	@Transactional
	public Suivi enregistrerReponse(String jetonSession, String reference, String interrogationId,
			ReponseFiscale reponse) {
		Compte compte = comptes.compteCourant(jetonSession);
		if (reponse == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, REPONSE_OBLIGATOIRE);
		}
		DemandeLivretA demande = demandes.findByReferenceAndCustomerId(reference, compte.getCustomerId())
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, DEMANDE_INTROUVABLE));
		if (!demande.estEnCours()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DEMANDE_TERMINEE);
		}
		demande.recevoirReponse(interrogationId, reponse, OffsetDateTime.now(horloge));
		return suivi(demande);
	}

	/** Tâche quotidienne : demandes sans réponse 2 jours ouvrés après la transmission, conseiller alerté. */
	@Scheduled(cron = "${app.livret-a.alerte-cron:0 0 6 * * *}")
	@Transactional
	public int alerterLesDemandesEnRetard() {
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		List<DemandeLivretA> enRetard = demandes
				.findByStatutAndConseillerAlerteLeIsNull(StatutDemandeLivretA.EN_COURS_DE_VERIFICATION).stream()
				.filter(demande -> demande.aAlerter(maintenant))
				.toList();
		enRetard.forEach(demande -> demande.alerterConseiller(maintenant));
		return enRetard.size();
	}

	/** CU-295 critère 5 (test) : la demande est traitée comme restée sans réponse au bout de 2 jours ouvrés. */
	@Transactional
	public DemandeLivretA simulerExpirationDelai(String reference) {
		DemandeLivretA demande = demandes.findByReference(reference)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, DEMANDE_INTROUVABLE));
		if (!demande.estEnCours()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DEMANDE_TERMINEE);
		}
		if (demande.getInterrogationLe() == null) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, NON_TRANSMISE);
		}
		demande.alerterConseiller(OffsetDateTime.now(horloge));
		return demande;
	}

	/** Livret A déjà ouvert chez nous, ou demande déjà en cours, pour la même identité. */
	private void controlerMultidetentionInterne(Dossier dossier) {
		List<Long> memeIdentite = dossiers.idsMemeIdentite(normaliser(dossier.getNomNaissance()),
				normaliser(dossier.getPrenoms()), dossier.getDateNaissance(),
				normaliser(dossier.getCommuneNaissance()), dossier.getPaysNaissance().name());
		List<DemandeLivretA> existantes = demandes
				.findByDossierIdIn(memeIdentite.isEmpty() ? List.of(dossier.getId()) : memeIdentite);
		if (existantes.stream().anyMatch(d -> d.getStatut() == StatutDemandeLivretA.OUVERTURE_VALIDEE)) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, LIVRET_A_DETENU, true);
		}
		if (!existantes.isEmpty()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DEMANDE_EN_COURS, true);
		}
	}

	private Suivi suivi(DemandeLivretA demande) {
		return new Suivi(demande, demande.estEnCours() ? transmission(dossier(demande.getDossierId())) : null);
	}

	private Dossier dossier(Long dossierId) {
		return dossiers.findById(dossierId)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, DEMANDE_INTROUVABLE));
	}

	/** Nom de naissance, prénoms, sexe (d'après la civilité), date et commune de naissance. */
	static Transmission transmission(Dossier dossier) {
		String sexe = "Mme".equalsIgnoreCase(dossier.getCivilite()) ? "F" : "M";
		return new Transmission(dossier.getNomNaissance(), dossier.getPrenoms(), sexe, dossier.getDateNaissance(),
				dossier.getCommuneNaissance());
	}

	/** Majuscules, espaces et tirets ignorés, comme le contrôle des doublons de CU-9. */
	static String normaliser(String valeur) {
		return valeur.toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
	}

}
