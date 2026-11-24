package com.example.compteunique.catalogue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface FichierDeposeRepository extends JpaRepository<FichierDepose, Long> {

	Optional<FichierDepose> findFirstByDateFichierOrderByIdDesc(LocalDate dateFichier);

	List<FichierDepose> findTop20ByOrderByIdDesc();

}
