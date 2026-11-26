package com.example.compteunique.gestion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChangementGestionRepository extends JpaRepository<ChangementGestion, Long> {

	List<ChangementGestion> findByNumeroContratOrderByIdDesc(String numeroContrat);

}
