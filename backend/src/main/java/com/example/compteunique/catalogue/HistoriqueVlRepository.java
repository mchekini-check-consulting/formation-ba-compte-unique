package com.example.compteunique.catalogue;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface HistoriqueVlRepository extends JpaRepository<HistoriqueVl, Long> {

	boolean existsBySupportAndDateVl(Support support, LocalDate dateVl);

	Optional<HistoriqueVl> findBySupportCodeIsinAndDateVl(String codeIsin, LocalDate dateVl);

}
