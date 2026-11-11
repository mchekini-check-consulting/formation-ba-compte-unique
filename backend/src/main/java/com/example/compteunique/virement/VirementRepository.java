package com.example.compteunique.virement;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VirementRepository extends JpaRepository<Virement, Long> {

	/** CU-147 critère 6 : un virement déjà exécuté vers ce bénéficiaire (ce n'est plus le premier). */
	boolean existsByBeneficiaireIdAndStatut(Long beneficiaireId, String statut);

	/** Virement du compte, verrouillé : un même code saisi deux fois en même temps n'est utilisé qu'une fois. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Virement v where v.id = :id and v.compteId = :compteId")
	Optional<Virement> verrouiller(@Param("id") Long id, @Param("compteId") Long compteId);

	/** CU-147 critère 14 (bouchon de test) : virement à refuser, verrouillé. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Virement v where v.id = :id")
	Optional<Virement> verrouiller(@Param("id") Long id);

}
