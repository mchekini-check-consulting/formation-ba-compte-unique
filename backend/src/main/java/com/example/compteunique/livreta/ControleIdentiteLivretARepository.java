package com.example.compteunique.livreta;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ControleIdentiteLivretARepository extends JpaRepository<ControleIdentiteLivretA, Long> {

	/** Dernier contrôle d'identité de la cliente. */
	Optional<ControleIdentiteLivretA> findFirstByCustomerIdOrderByCreeLeDesc(UUID customerId);

	/** Contrôle de la cliente, seulement s'il lui appartient. */
	Optional<ControleIdentiteLivretA> findByReferenceAndCustomerId(String reference, UUID customerId);

	Optional<ControleIdentiteLivretA> findByReference(String reference);

}
