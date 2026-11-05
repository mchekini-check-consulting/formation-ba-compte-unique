package com.example.compteunique.compte;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompteRepository extends JpaRepository<Compte, Long> {

	boolean existsByNumeroCompte(String numeroCompte);

	Optional<Compte> findByDossierId(Long dossierId);

	Optional<Compte> findByIdentifiantConnexionId(Long identifiantConnexionId);

	/** CU-20 : les comptes dont la cliente est titulaire. */
	List<Compte> findByCustomerId(UUID customerId);

	/** CU-20 : un compte seulement si la cliente en est titulaire. */
	Optional<Compte> findByIdAndCustomerId(Long id, UUID customerId);

}
