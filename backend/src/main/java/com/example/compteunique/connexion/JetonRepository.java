package com.example.compteunique.connexion;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JetonRepository extends JpaRepository<Jeton, Long> {

	Optional<Jeton> findByJetonHacheAndType(String jetonHache, TypeJeton type);

	/** CU-46 : nombre de liens créés depuis une date (limite de demandes par heure). */
	long countByIdentifiantConnexionIdAndTypeAndCreeLeAfter(Long identifiantConnexionId, TypeJeton type,
			OffsetDateTime depuis);

	@Modifying
	@Query("""
			update Jeton j set j.invalideLe = :date
			where j.identifiantConnexionId = :identifiantConnexionId and j.type = :type
			and j.utiliseLe is null and j.invalideLe is null
			""")
	int invaliderLesJetonsActifs(@Param("identifiantConnexionId") Long identifiantConnexionId,
			@Param("type") TypeJeton type, @Param("date") OffsetDateTime date);

}
