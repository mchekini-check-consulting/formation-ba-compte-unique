package com.example.compteunique.dossier;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.dossier.Categories.MotifAnnulation;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.securite.JournalSecurite;

// CU-9 critères 5 à 7 : tâche quotidienne. Un dossier qui attend une action de la cliente (pièce à
// renvoyer, convention à signer : « En cours de vérification ») sans aucune action depuis 30 jours
// passe à « Annulé », motif « Inactivité » ; une nouvelle ouverture redevient possible.
// Jamais annulés : « Accepté », « Convention signée », « Création à relancer » (ils attendent le
// système ou la banque) et les dossiers en attente de l'analyste (PPE).
@Service
@Loggable
public class InactiviteDossiers {

	/** Durée à valider avec la conformité (ticket CU-9). */
	static final Duration DELAI_INACTIVITE = Duration.ofDays(30);

	private final DossierRepository dossiers;
	private final JournalSecurite journal;
	private final Clock horloge;

	@Autowired
	public InactiviteDossiers(DossierRepository dossiers, JournalSecurite journal) {
		this(dossiers, journal, Clock.systemDefaultZone());
	}

	InactiviteDossiers(DossierRepository dossiers, JournalSecurite journal, Clock horloge) {
		this.dossiers = dossiers;
		this.journal = journal;
		this.horloge = horloge;
	}

	/** Renvoie le nombre de dossiers annulés. */
	@Scheduled(cron = "${app.dossiers.inactivite-cron:0 0 4 * * *}")
	@Transactional
	public int annulerLesDossiersInactifs() {
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		List<Dossier> inactifs = dossiers.findByStatutAndAttenteAnalysteFalseAndDerniereActionLeBefore(
				StatutDossier.EN_COURS_DE_VERIFICATION, maintenant.minus(DELAI_INACTIVITE));
		for (Dossier dossier : inactifs) {
			dossier.annuler(MotifAnnulation.INACTIVITE, maintenant);
			journal.enregistrer(EvenementSecurite.dossier(EvenementSecurite.ANNULATION_INACTIVITE,
					MotifAnnulation.INACTIVITE.name(), maintenant));
		}
		return inactifs.size();
	}

}
