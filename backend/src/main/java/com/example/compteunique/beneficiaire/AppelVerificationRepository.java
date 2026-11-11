package com.example.compteunique.beneficiaire;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppelVerificationRepository extends JpaRepository<AppelVerification, Long> {

	/** CU-145 : les 50 derniers appels du journal de test, du plus récent au plus ancien. */
	List<AppelVerification> findTop50ByOrderByAppeleLeDescIdDesc();

}
