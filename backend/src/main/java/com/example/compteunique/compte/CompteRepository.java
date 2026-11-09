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

}
