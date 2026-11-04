package com.example.compteunique.compte;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompteRepository extends JpaRepository<Compte, Long> {

	boolean existsByNumeroCompte(String numeroCompte);

	Optional<Compte> findByDossierId(Long dossierId);

	Optional<Compte> findByIdentifiantConnexionId(Long identifiantConnexionId);

}
