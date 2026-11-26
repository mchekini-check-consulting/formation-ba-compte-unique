package com.example.compteunique.ordres;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PositionTestRepository extends JpaRepository<PositionTest, Long> {

	List<PositionTest> findByNumeroContratAndCodeIsin(String numeroContrat, String codeIsin);

	List<PositionTest> findByNumeroContrat(String numeroContrat);

	List<PositionTest> findTop50ByOrderByIdDesc();

}
