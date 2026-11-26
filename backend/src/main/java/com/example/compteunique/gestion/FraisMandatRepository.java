package com.example.compteunique.gestion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface FraisMandatRepository extends JpaRepository<FraisMandat, Long> {

	List<FraisMandat> findTop50ByOrderByIdDesc();

}
