package com.example.compteunique.gestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.example.compteunique.catalogue.CatalogueService.Produit;

interface ContratGestionRepository extends JpaRepository<ContratGestion, Long> {

	Optional<ContratGestion> findByCustomerIdAndProduit(UUID customerId, Produit produit);

	/** Verrou pendant un changement de mode : deux choix simultanés ne se mélangent pas. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ContratGestion> findWithLockByCustomerIdAndProduit(UUID customerId, Produit produit);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ContratGestion> findWithLockByNumeroContrat(String numeroContrat);

	Optional<ContratGestion> findByNumeroContrat(String numeroContrat);

	List<ContratGestion> findTop50ByOrderByIdDesc();

	/** Lot 4 : contrats PER et assurance vie de la cliente, pour leurs lignes dans l'espace. */
	List<ContratGestion> findByCustomerIdOrderByProduit(UUID customerId);

}
