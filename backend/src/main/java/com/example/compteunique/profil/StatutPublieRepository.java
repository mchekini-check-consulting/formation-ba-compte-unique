package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StatutPublieRepository extends JpaRepository<StatutPublie, UUID> {

	/**
	 * Clientes dont la dernière version du profil a atteint sa date de revalidation, et pour qui « à revalider » n'a
	 * pas encore été publié.
	 */
	@Query(value = "select p.customer_id from profil_client.profil_kyc p"
			+ " where p.version = (select max(q.version) from profil_client.profil_kyc q where q.customer_id = p.customer_id)"
			+ " and p.revalider_le <= :maintenant"
			+ " and not exists (select 1 from profil_client.statut_publie s"
			+ " where s.customer_id = p.customer_id and s.statut = 'A_REVALIDER')", nativeQuery = true)
	List<UUID> aRevaliderNonPublies(OffsetDateTime maintenant);

	/** CU-415 : statut publié d'une cliente, verrouillé jusqu'à la fin de la transaction (tâche et lecture). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from StatutPublie s where s.customerId = :customerId")
	Optional<StatutPublie> verrouiller(@Param("customerId") UUID customerId);

	/** CU-415 : une seule tâche de revalidation à la fois, même avec 2 réplicas (preprod, prod). */
	@Query(value = "select 1 from pg_advisory_xact_lock(415)", nativeQuery = true)
	Integer verrouillerLaTache();

}
