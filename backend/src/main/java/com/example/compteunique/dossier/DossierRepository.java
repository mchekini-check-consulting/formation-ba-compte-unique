package com.example.compteunique.dossier;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.compteunique.dossier.Categories.StatutDossier;

public interface DossierRepository extends JpaRepository<Dossier, Long> {

	Optional<Dossier> findByReference(String reference);

	/** CU-13 : dossiers dont la création du compte est à (re)lancer automatiquement. */
	List<Dossier> findByStatutAndCreationProchaineLeLessThanEqual(StatutDossier statut, OffsetDateTime date);

}
