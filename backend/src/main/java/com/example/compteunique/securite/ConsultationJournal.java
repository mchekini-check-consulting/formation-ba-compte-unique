package com.example.compteunique.securite;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.logging.Loggable;

// CU-167 et CU-48 : en environnement de test seulement (app.test.journal-consultable,
// JOURNAL_CONSULTABLE dans helm.yaml), les derniers événements du journal de sécurité sont
// consultables sans accès à la base. Vide (donc 404) en preprod et prod.
@Service
@Loggable
public class ConsultationJournal {

	private final JournalSecuriteRepository journal;
	private final boolean consultable;

	public ConsultationJournal(JournalSecuriteRepository journal,
			@Value("${app.test.journal-consultable:false}") boolean consultable) {
		this.journal = journal;
		this.consultable = consultable;
	}

	/** Les 100 derniers événements, éventuellement d'un seul identifiant saisi ; vide hors test. */
	@Transactional(readOnly = true)
	public Optional<List<EvenementSecurite>> derniers(String identifiantSaisi) {
		if (!consultable) {
			return Optional.empty();
		}
		return Optional.of(identifiantSaisi == null || identifiantSaisi.isBlank() ? journal.findTop100ByOrderByIdDesc()
				: journal.findTop100ByIdentifiantSaisiOrderByIdDesc(identifiantSaisi.trim()));
	}

}
