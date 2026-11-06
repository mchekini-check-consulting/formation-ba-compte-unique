package com.example.compteunique.connexion;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface BlocageIdentifiantRepository extends JpaRepository<BlocageIdentifiant, String> {

	/** Verrouillé jusqu'à la fin de la tentative : deux tentatives simultanées comptent bien deux erreurs. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<BlocageIdentifiant> findWithLockByIdentifiant(String identifiant);

}
