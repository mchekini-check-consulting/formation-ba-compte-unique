package com.example.compteunique.connexion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentifiantConnexionRepository extends JpaRepository<IdentifiantConnexion, Long> {

	Optional<IdentifiantConnexion> findByIdentifiant(String identifiant);

	boolean existsByIdentifiant(String identifiant);

}
