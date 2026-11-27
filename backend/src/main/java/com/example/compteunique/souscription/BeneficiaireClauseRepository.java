package com.example.compteunique.souscription;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BeneficiaireClauseRepository extends JpaRepository<BeneficiaireClause, Long> {

	List<BeneficiaireClause> findBySouscriptionIdOrderByRang(Long souscriptionId);

	/** La clause saisie remplace la précédente tant que le contrat n'est pas signé. */
	@Modifying
	@Query("delete from BeneficiaireClause b where b.souscriptionId = :souscriptionId")
	void supprimer(@Param("souscriptionId") Long souscriptionId);

}
