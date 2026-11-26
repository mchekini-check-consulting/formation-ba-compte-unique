package com.example.compteunique.gestion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SignalementNonCoteRepository extends JpaRepository<SignalementNonCote, Long> {

	List<SignalementNonCote> findTop50ByOrderByIdDesc();

}
