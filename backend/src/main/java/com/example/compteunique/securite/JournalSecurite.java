package com.example.compteunique.securite;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.logging.LogMetier;
import com.example.compteunique.logging.Loggable;

// CU-9 et CU-48 : écriture d'un événement dans le journal de sécurité, avec l'adresse IP de la
// demande en cours. L'écriture est faite tout de suite (flush) : si elle échoue, l'équipe technique
// est prévenue (log de niveau ERROR) et l'action en cours est refusée.
@Service
@Loggable
public class JournalSecurite {

	private static final Logger log = LoggerFactory.getLogger(JournalSecurite.class);

	private final JournalSecuriteRepository journal;

	public JournalSecurite(JournalSecuriteRepository journal) {
		this.journal = journal;
	}

	@LogMetier(champs = { "evenement.evenement", "evenement.motif", "evenement.identifiantConnexionId" })
	public void enregistrer(EvenementSecurite evenement) {
		adresseIpCourante().ifPresent(evenement::depuis);
		try {
			journal.saveAndFlush(evenement);
		} catch (DataAccessException e) {
			// Ni identifiant ni adresse dans ce log : seulement le type d'événement et la cause.
			log.error("Journal de sécurité indisponible, événement {} non enregistré : {}", evenement.getEvenement(),
					e.getClass().getSimpleName());
			throw new JournalIndisponibleException(e);
		}
	}

	/**
	 * Adresse IP de la cliente pour la demande HTTP en cours, vide hors demande (tâche planifiée,
	 * arrière-plan). Derrière le gateway, l'adresse d'origine est lue dans X-Forwarded-For
	 * (server.forward-headers-strategy dans application.yaml).
	 */
	public static Optional<String> adresseIpCourante() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs
				? Optional.ofNullable(attributs.getRequest()).map(HttpServletRequest::getRemoteAddr)
				: Optional.empty();
	}

}
