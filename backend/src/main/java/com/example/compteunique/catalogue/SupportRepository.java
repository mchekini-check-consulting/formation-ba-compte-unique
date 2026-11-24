package com.example.compteunique.catalogue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SupportRepository extends JpaRepository<Support, Long> {

	List<Support> findByCodeIsinIn(Collection<String> codesIsin);

	Optional<Support> findByCodeIsin(String codeIsin);

	List<Support> findByStatutOrderByNom(String statut);

}
