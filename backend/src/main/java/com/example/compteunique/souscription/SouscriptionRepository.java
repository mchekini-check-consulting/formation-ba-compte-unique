package com.example.compteunique.souscription;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.example.compteunique.catalogue.CatalogueService.Produit;

interface SouscriptionRepository extends JpaRepository<Souscription, Long> {

	Optional<Souscription> findByCustomerIdAndProduit(UUID customerId, Produit produit);

	/** Lue verrouillée : deux saisies de code simultanées ne comptent pas un seul code erroné. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Souscription> findWithLockByCustomerIdAndProduit(UUID customerId, Produit produit);

}
