package com.example.compteunique.connexion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionClientRepository extends JpaRepository<SessionClient, Long> {

	Optional<SessionClient> findByJetonHache(String jetonHache);

	/** CU-46 : ferme toutes les sessions ouvertes du client. */
	@Modifying
	@Query("delete from SessionClient s where s.identifiantConnexionId = :identifiantConnexionId")
	int fermerToutesLesSessions(@Param("identifiantConnexionId") Long identifiantConnexionId);

}
