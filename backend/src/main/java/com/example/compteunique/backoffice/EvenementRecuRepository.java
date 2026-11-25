package com.example.compteunique.backoffice;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface EvenementRecuRepository extends JpaRepository<EvenementRecu, Long> {

	List<EvenementRecu> findTop50ByOrderByIdDesc();

}
