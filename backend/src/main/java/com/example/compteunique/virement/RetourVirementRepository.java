package com.example.compteunique.virement;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RetourVirementRepository extends JpaRepository<RetourVirement, Long> {

	List<RetourVirement> findByCompteId(Long compteId);

}
