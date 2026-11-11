package com.example.compteunique.beneficiaire;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AjoutBeneficiaireRepository extends JpaRepository<AjoutBeneficiaire, Long> {

	/** Ajout de la cliente, verrouillé : deux saisies simultanées du même code ne créent qu'un bénéficiaire. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AjoutBeneficiaire a where a.id = :id and a.customerId = :customerId")
	Optional<AjoutBeneficiaire> verrouiller(@Param("id") Long id, @Param("customerId") UUID customerId);

}
