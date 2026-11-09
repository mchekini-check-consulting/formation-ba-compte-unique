package com.example.compteunique.compte;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MouvementRepository extends JpaRepository<Mouvement, Long> {

	/** CU-11 : les 50 derniers mouvements du compte, du plus récent au plus ancien. */
	List<Mouvement> findTop50ByCompteIdOrderByEffectueLeDescIdDesc(Long compteId);

}
