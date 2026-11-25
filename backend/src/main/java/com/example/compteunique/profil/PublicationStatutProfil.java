package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * la date métier atteint la date de revalidation (tâche quotidienne ou lecture du profil, CU-415). Le message
 * contient le statut et l'ancien statut, jamais les réponses du questionnaire. Le statut PPE viendra avec le
 * contrôle PPE.
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
		// CU-415 : verrou sur la ligne, pour que la tâche et la lecture du profil ne publient pas deux fois.
		StatutPublie publie = publies.verrouiller(customerId).orElse(null);
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

	/**
	 * CU-415 : tâche quotidienne (TacheRevalidationProfils) et bouton de test. Les profils arrivés à leur date de
	 * revalidation (date métier) passent à « à revalider » : statut et événement enregistrés dans la même transaction,
	 * une seule fois (relancer la tâche le même jour ne publie rien de plus). Renvoie le nombre de profils passés.
	 */
	@Transactional
	public int revaliderLesProfilsEchus() {
		publies.verrouillerLaTache();
		OffsetDateTime maintenant = dateMetier.maintenant().toOffsetDateTime();
		List<UUID> echus = publies.aRevaliderNonPublies(maintenant);
		for (UUID customerId : echus) {
			changer(customerId, Statut.VALIDE, Statut.A_REVALIDER);
		}
		return echus.size();
	}

}
