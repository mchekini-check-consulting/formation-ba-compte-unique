package com.example.compteunique.evenements;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EvenementADistribuerRepository extends JpaRepository<EvenementADistribuer, Long> {

	/** Événements pas encore publiés, dans l'ordre d'enregistrement (l'ordre d'un même contrat est conservé). */
	@Query(value = "select * from evenements.a_distribuer where publie_le is null order by id limit 100",
			nativeQuery = true)
	List<EvenementADistribuer> enAttente();

	List<EvenementADistribuer> findTop50ByOrderByIdDesc();

	/**
	 * Un seul relais à la fois, même avec 2 réplicas (preprod, prod) : sinon deux relais pourraient publier les
	 * événements d'un même contrat dans le désordre. Verrou PostgreSQL libéré à la fin de la transaction.
	 */
	@Query(value = "select pg_try_advisory_xact_lock(382)", nativeQuery = true)
	boolean verrouiller();

}
