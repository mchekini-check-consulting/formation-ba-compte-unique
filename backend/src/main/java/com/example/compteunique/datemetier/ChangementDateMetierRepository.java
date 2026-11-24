package com.example.compteunique.datemetier;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChangementDateMetierRepository extends JpaRepository<ChangementDateMetier, Long> {

	List<ChangementDateMetier> findTop100ByOrderByIdDesc();

}
