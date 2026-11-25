package com.example.compteunique.alertes;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Messagerie;

/**
 * CU-382 (TECH-14) : alertes à l'équipe, fonction publique commune à tous les traitements en échec (topic d'erreurs,
 * batch du catalogue, et plus tard exécution des ordres et rapprochement). Chaque alerte envoie un e-mail à
 * ALERTE_EQUIPE_EMAIL (en dev et qualif : capturé dans la boîte de test /test/emails) et ajoute une ligne au journal :
 * date métier, origine, message, e-mail envoyé ou non. Le journal est écrit dans sa propre transaction : il reste même
 * si le traitement qui alerte est annulé. Messages sans donnée personnelle.
 */
@Service
@Loggable
public class AlertesEquipe {

	private static final Logger log = LoggerFactory.getLogger(AlertesEquipe.class);

	private final AlerteRepository journal;
	private final Messagerie messagerie;
	private final DateMetier dateMetier;
	private final String adresse;

	@Autowired
	public AlertesEquipe(AlerteRepository journal, Messagerie messagerie, DateMetier dateMetier,
			@Value("${app.alertes.email:}") String adresse) {
		this.journal = journal;
		this.messagerie = messagerie;
		this.dateMetier = dateMetier;
		this.adresse = adresse == null ? "" : adresse.trim();
	}

	/** Alerte l'équipe : e-mail (si l'adresse est déclarée) et ligne au journal, même si l'e-mail échoue. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Alerte signaler(String origine, String message) {
		return journal.save(new Alerte(dateMetier.maintenant().toOffsetDateTime(), origine, message,
				envoyer(origine, message)));
	}

	/** Les 50 dernières alertes, de la plus récente à la plus ancienne (pages de test). */
	@Transactional(readOnly = true)
	public List<Alerte> dernieres() {
		return journal.findTop50ByOrderByIdDesc();
	}

	private boolean envoyer(String origine, String message) {
		if (adresse.isEmpty()) {
			return false;
		}
		try {
			return messagerie.envoyerEmail(adresse, "Alerte Hi-Tech Bank : " + origine, message);
		} catch (RuntimeException panne) {
			MDC.put("layer", "service");
			try {
				log.error("alerte equipe : e-mail non envoye ({})", panne.getClass().getSimpleName());
			} finally {
				MDC.remove("layer");
			}
			return false;
		}
	}

}
