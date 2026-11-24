package com.example.compteunique.catalogue;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JournalBatchRepository extends JpaRepository<JournalBatch, Long> {

	boolean existsByDateMetierAndDeclenchement(LocalDate dateMetier, JournalBatch.Declenchement declenchement);

	List<JournalBatch> findTop20ByOrderByIdDesc();

	/**
	 * Une seule exécution à la fois, même avec 2 réplicas (preprod, prod) : verrou PostgreSQL libéré à la fin de la
	 * transaction.
	 */
	@Query(value = "select 1 from pg_advisory_xact_lock(381)", nativeQuery = true)
	Integer verrouiller();

}
