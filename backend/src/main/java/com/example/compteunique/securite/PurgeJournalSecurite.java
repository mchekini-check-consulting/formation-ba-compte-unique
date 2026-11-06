package com.example.compteunique.securite;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Period;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.logging.Loggable;

// CU-48 critère 9 : les événements du journal de sécurité plus anciens que la durée de
// conservation fixée par la conformité (6 mois) sont supprimés chaque nuit.
@Service
@Loggable
public class PurgeJournalSecurite {

	static final Period CONSERVATION = Period.ofMonths(6);

	private final JournalSecuriteRepository journal;
	private final Clock horloge;

	@Autowired
	public PurgeJournalSecurite(JournalSecuriteRepository journal) {
		this(journal, Clock.systemDefaultZone());
	}

	PurgeJournalSecurite(JournalSecuriteRepository journal, Clock horloge) {
		this.journal = journal;
		this.horloge = horloge;
	}

	/** Renvoie le nombre d'événements supprimés. */
	@Scheduled(cron = "${app.journal.purge-cron:0 30 3 * * *}")
	@Transactional
	public int purger() {
		return journal.purgerAvant(OffsetDateTime.now(horloge).minus(CONSERVATION));
	}

}
