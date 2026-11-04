package com.example.compteunique.signature;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignatureConventionRepository extends JpaRepository<SignatureConvention, Long> {

	Optional<SignatureConvention> findByDossierId(Long dossierId);

}
