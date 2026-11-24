package com.example.compteunique.catalogue;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

interface EvenementCatalogueRepository extends JpaRepository<EvenementCatalogue, Long> {

	boolean existsByTypeAndDateMetier(String type, LocalDate dateMetier);

}
