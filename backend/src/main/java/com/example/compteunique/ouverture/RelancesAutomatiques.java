package com.example.compteunique.ouverture;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.DossierRepository;

// CU-13 : relances automatiques, à 1 minute d'intervalle, des créations de compte et des
// envois (e-mail, SMS) en échec. Vérifié toutes les 20 secondes (app.relances.intervalle-ms).
@Configuration
@EnableScheduling
public class RelancesAutomatiques {

	private final DossierRepository dossiers;
	private final EnvoiMessageRepository envois;
	private final OuvertureService ouverture;
	private final EnvoisIdentifiants envoisIdentifiants;
	private final Clock horloge;

	@Autowired
	public RelancesAutomatiques(DossierRepository dossiers, EnvoiMessageRepository envois, OuvertureService ouverture,
			EnvoisIdentifiants envoisIdentifiants) {
		this(dossiers, envois, ouverture, envoisIdentifiants, Clock.systemDefaultZone());
	}

	RelancesAutomatiques(DossierRepository dossiers, EnvoiMessageRepository envois, OuvertureService ouverture,
			EnvoisIdentifiants envoisIdentifiants, Clock horloge) {
		this.dossiers = dossiers;
		this.envois = envois;
		this.ouverture = ouverture;
		this.envoisIdentifiants = envoisIdentifiants;
		this.horloge = horloge;
	}

	@Scheduled(fixedDelayString = "${app.relances.intervalle-ms:20000}",
			initialDelayString = "${app.relances.intervalle-ms:20000}")
	public void relancer() {
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		dossiers.findByStatutAndCreationProchaineLeLessThanEqual(StatutDossier.CONVENTION_SIGNEE, maintenant)
				.forEach(dossier -> ouverture.ouvrir(dossier.getId()));
		envois.findByResultatAndProchaineTentativeLeLessThanEqual(ResultatEnvoi.EN_ECHEC, maintenant)
				.forEach(envoi -> envoisIdentifiants.envoyer(envoi.getDossierId(), envoi.getCanal()));
	}

}
