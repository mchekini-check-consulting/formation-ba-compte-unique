package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.profil.ProfilClientService.Statut;

/**
 * CU-382 (TECH-14) : événement « statut du profil modifié », publié dans le topic profil-client avec l'identifiant
 * client comme clé, à chaque changement de statut : « validé » à la validation du questionnaire, « à revalider » quand
 * la date métier atteint la date de revalidation (vérifié chaque minute). Le message contient le statut et l'ancien
 * statut, jamais les réponses du questionnaire. Le statut PPE viendra avec le contrôle PPE.
 */
@Service
@Loggable
public class PublicationStatutProfil {

	static final String TYPE = "StatutProfilModifie";

	private final StatutPublieRepository publies;
	private final Evenements evenements;
	private final DateMetier dateMetier;

	public PublicationStatutProfil(StatutPublieRepository publies, Evenements evenements, DateMetier dateMetier) {
		this.publies = publies;
		this.evenements = evenements;
		this.dateMetier = dateMetier;
	}

	/**
	 * Note le nouveau statut d'une cliente et enregistre l'événement s'il a changé. Appelé dans la transaction de
	 * l'opération (validation du questionnaire) : l'événement n'existe que si l'opération est enregistrée.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void changer(UUID customerId, Statut avant, Statut nouveau) {
		OffsetDateTime maintenant = dateMetier.maintenant().toOffsetDateTime();
		StatutPublie publie = publies.findById(customerId).orElse(null);
		Statut ancien = publie == null ? avant : publie.getStatut();
		if (ancien == nouveau) {
			return;
		}
		if (publie == null) {
			publies.save(new StatutPublie(customerId, nouveau, maintenant));
		} else {
			publie.changer(nouveau, maintenant);
		}
		evenements.enregistrer(Topic.PROFIL_CLIENT, TYPE, customerId.toString(),
				Map.of("statut", nouveau.name(), "ancienStatut", ancien.name()));
	}

	/** Profils arrivés à leur date de revalidation (date métier) : « à revalider » publié une seule fois. */
	@Scheduled(fixedDelayString = "${app.profil.verification-ms:60000}",
			initialDelayString = "${app.profil.verification-ms:60000}")
	@Transactional
	public void signalerRevalidations() {
		OffsetDateTime maintenant = dateMetier.maintenant().toOffsetDateTime();
		for (UUID customerId : publies.aRevaliderNonPublies(maintenant)) {
			changer(customerId, Statut.VALIDE, Statut.A_REVALIDER);
		}
	}

}
