package com.example.compteunique.alertes;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AlerteRepository extends JpaRepository<Alerte, Long> {

	List<Alerte> findTop50ByOrderByIdDesc();

}
