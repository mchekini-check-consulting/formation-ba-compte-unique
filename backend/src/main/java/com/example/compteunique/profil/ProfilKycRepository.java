package com.example.compteunique.profil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProfilKycRepository extends JpaRepository<ProfilKyc, Long> {

	Optional<ProfilKyc> findFirstByCustomerIdOrderByVersionDesc(UUID customerId);

	List<ProfilKyc> findByCustomerIdOrderByVersionDesc(UUID customerId);

}
