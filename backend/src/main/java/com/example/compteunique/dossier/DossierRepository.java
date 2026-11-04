package com.example.compteunique.dossier;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DossierRepository extends JpaRepository<Dossier, Long> {

	Optional<Dossier> findByReference(String reference);

}
