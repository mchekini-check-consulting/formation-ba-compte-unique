package com.example.compteunique.profil;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConsentementRepository extends JpaRepository<Consentement, Long> {

	/** CU-32 : la cliente a-t-elle accepté cette version du texte pour ce type de consentement ? */
	boolean existsByCustomerIdAndTypeAndVersionTexteAndChoixTrue(UUID customerId, String type, String versionTexte);

}
