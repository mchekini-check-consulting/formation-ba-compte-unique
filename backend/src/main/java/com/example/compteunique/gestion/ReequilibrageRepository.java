package com.example.compteunique.gestion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReequilibrageRepository extends JpaRepository<Reequilibrage, Long> {

	List<Reequilibrage> findByNumeroContratOrderByIdDesc(String numeroContrat);

}
