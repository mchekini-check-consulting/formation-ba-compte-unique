package com.example.compteunique.livreta;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PieceIdentiteLivretARepository extends JpaRepository<PieceIdentiteLivretA, Long> {

	/** Dernière vérification de pièce de la cliente. */
	Optional<PieceIdentiteLivretA> findFirstByCustomerIdOrderByCreeLeDesc(UUID customerId);

	/** Vérification de la cliente, seulement si elle lui appartient. */
	Optional<PieceIdentiteLivretA> findByReferenceAndCustomerId(String reference, UUID customerId);

}
