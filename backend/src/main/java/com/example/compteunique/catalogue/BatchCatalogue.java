package com.example.compteunique.catalogue;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.compteunique.catalogue.JournalBatch.Declenchement;
import com.example.compteunique.datemetier.DateMetier;

// CU-381 (TECH-13) : déclenchement nocturne. Vérifie chaque minute la date métier (TECH-12) : un jour ouvré, dès
// l'heure du batch (app.catalogue.heure, 2 h par défaut, à confirmer), le chargement s'exécute une fois pour ce jour.
// Suivre la date métier (et non l'horloge du serveur) permet aux testeuses de déclencher le batch en l'avançant.
@Component
class BatchCatalogue {

	private static final Logger log = LoggerFactory.getLogger(BatchCatalogue.class);

	private final ChargementCatalogue chargement;
	private final DateMetier dateMetier;
	private final int heure;

	BatchCatalogue(ChargementCatalogue chargement, DateMetier dateMetier,
			@Value("${app.catalogue.heure:2}") int heure) {
		this.chargement = chargement;
		this.dateMetier = dateMetier;
		this.heure = heure;
	}

	@Scheduled(fixedDelayString = "${app.catalogue.verification-ms:60000}",
			initialDelayString = "${app.catalogue.verification-ms:60000}")
	void verifier() {
		try {
			ZonedDateTime maintenant = dateMetier.maintenant();
			if (ChargementCatalogue.estOuvre(maintenant.toLocalDate()) && maintenant.getHour() >= heure
					&& !chargement.dejaLance(maintenant.toLocalDate())) {
				chargement.lancer(Declenchement.AUTOMATIQUE);
			}
		} catch (RuntimeException erreur) {
			MDC.put("layer", "service");
			try {
				log.error("batch catalogue interrompu : {}", erreur.getClass().getSimpleName());
			} finally {
				MDC.remove("layer");
			}
		}
	}

}
