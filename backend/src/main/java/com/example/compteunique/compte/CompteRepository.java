package com.example.compteunique.compte;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CompteRepository extends JpaRepository<Compte, Long> {

	boolean existsByNumeroCompte(String numeroCompte);

	Optional<Compte> findByDossierId(Long dossierId);

	Optional<Compte> findByIdentifiantConnexionId(Long identifiantConnexionId);

	/** CU-20 : les comptes dont la cliente est titulaire. */
	List<Compte> findByCustomerId(UUID customerId);

	/** CU-20 : un compte seulement si la cliente en est titulaire. */
	Optional<Compte> findByIdAndCustomerId(Long id, UUID customerId);

	/**
	 * CU-143 et CU-11 : compte qui reçoit un virement, verrouillé jusqu'à la fin de la transaction pour que deux
	 * virements simultanés s'additionnent.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Compte c where c.iban = :iban")
	Optional<Compte> verrouillerParIban(@Param("iban") String iban);

	/** CU-147 : identifiant du compte de la cliente, sans le charger (il est ensuite lu verrouillé). */
	@Query("select c.id from Compte c where c.identifiantConnexionId = :identifiantConnexionId")
	Optional<Long> idParIdentifiantConnexion(@Param("identifiantConnexionId") Long identifiantConnexionId);

	/** CU-145 : compte d'un IBAN de test, pour retrouver le nom de son titulaire. */
	Optional<Compte> findByIban(String iban);

	/**
	 * CU-147 : compte débité par un virement, verrouillé jusqu'à la fin de la transaction : deux virements signés
	 * en même temps sont traités l'un après l'autre et le solde ne devient jamais négatif.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Compte c where c.id = :id")
	Optional<Compte> verrouiller(@Param("id") Long id);

}
