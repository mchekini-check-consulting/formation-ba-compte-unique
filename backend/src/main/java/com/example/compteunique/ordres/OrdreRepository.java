package com.example.compteunique.ordres;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrdreRepository extends JpaRepository<Ordre, UUID> {

	/** Montant des ordres en euros d'un contrat qui vendent ce support et sont encore en cours (montant bloqué). */
	@Query("select coalesce(sum(o.montant), 0) from Ordre o where o.numeroContrat = :contrat "
			+ "and o.supportDepart = :support and o.statut in :statuts")
	BigDecimal montantEnCours(@Param("contrat") String contrat, @Param("support") String support,
			@Param("statuts") Collection<StatutOrdre> statuts);

	/** Nombre de parts des ordres en parts d'un contrat qui vendent ce support et sont encore en cours. */
	@Query("select coalesce(sum(o.nombreParts), 0) from Ordre o where o.numeroContrat = :contrat "
			+ "and o.supportDepart = :support and o.statut in :statuts")
	BigDecimal partsEnCours(@Param("contrat") String contrat, @Param("support") String support,
			@Param("statuts") Collection<StatutOrdre> statuts);

	/**
	 * Verrou du contrat jusqu'à la fin de la transaction : deux ordres simultanés du même contrat ne peuvent pas
	 * engager deux fois le même montant disponible.
	 */
	@Query(value = "select 1 from pg_advisory_xact_lock(376, hashtext(:contrat))", nativeQuery = true)
	Integer verrouiller(@Param("contrat") String contrat);

	/** L'ordre, verrouillé jusqu'à la fin de la transaction : deux annulations simultanées n'en font qu'une. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Ordre o where o.id = :id")
	Optional<Ordre> verrouillerOrdre(@Param("id") UUID id);

	List<Ordre> findTop50ByOrderByDemandeLeDesc();

}
