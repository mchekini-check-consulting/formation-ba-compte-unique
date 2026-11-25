package com.example.compteunique.profil;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.compteunique.datemetier.DateMetier;

// CU-415 : tâche quotidienne de CU-10. Vérifie chaque minute la date métier (TECH-12) : une fois par jour métier, dès
// l'heure prévue (app.profil.heure-revalidation, 1 h par défaut), les profils échus passent à « à revalider ». Suivre
// la date métier permet aux testeuses de déclencher la tâche en l'avançant. Avec 2 réplicas, chacun peut la lancer :
// elle est verrouillée et ne publie jamais deux fois le même changement.
@Component
class TacheRevalidationProfils {

	private static final Logger log = LoggerFactory.getLogger(TacheRevalidationProfils.class);

	private final PublicationStatutProfil publication;
	private final DateMetier dateMetier;
	private final int heure;
	private final AtomicReference<LocalDate> dernierJour = new AtomicReference<>();

	@Autowired
	TacheRevalidationProfils(PublicationStatutProfil publication, DateMetier dateMetier,
			@Value("${app.profil.heure-revalidation:1}") int heure) {
		this.publication = publication;
		this.dateMetier = dateMetier;
		this.heure = heure;
	}

	@Scheduled(fixedDelayString = "${app.profil.verification-ms:60000}",
			initialDelayString = "${app.profil.verification-ms:60000}")
	void verifier() {
		try {
			ZonedDateTime maintenant = dateMetier.maintenant();
			LocalDate jour = maintenant.toLocalDate();
			if (maintenant.getHour() >= heure && !jour.equals(dernierJour.get())) {
				publication.revaliderLesProfilsEchus();
				dernierJour.set(jour);
			}
		} catch (RuntimeException erreur) {
			MDC.put("layer", "service");
			try {
				log.error("tache de revalidation des profils interrompue : {}", erreur.getClass().getSimpleName());
			} finally {
				MDC.remove("layer");
			}
		}
	}

}
