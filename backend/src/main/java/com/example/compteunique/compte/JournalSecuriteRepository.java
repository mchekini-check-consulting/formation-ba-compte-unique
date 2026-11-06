package com.example.compteunique.compte;

import java.time.OffsetDateTime;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalSecuriteRepository extends JpaRepository<EvenementSecurite, Long> {

	/** CU-48 : échecs d'une adresse IP depuis une date (fenêtre glissante), pour les motifs donnés. */
	long countByAdresseIpAndEvenementAndMotifInAndSurvenuLeAfter(String adresseIp, String evenement,
			Collection<String> motifs, OffsetDateTime depuis);

	/** CU-48 critère 9 : purge des événements plus anciens que la durée de conservation. */
	@Modifying
	@Query("delete from EvenementSecurite e where e.survenuLe < :limite")
	int purgerAvant(@Param("limite") OffsetDateTime limite);

}
