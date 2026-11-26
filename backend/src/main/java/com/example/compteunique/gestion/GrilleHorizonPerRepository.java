package com.example.compteunique.gestion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface GrilleHorizonPerRepository extends JpaRepository<GrilleHorizonPer, Long> {

	List<GrilleHorizonPer> findByProfilOrderByAnneesMinDesc(ProfilGestion profil);

	List<GrilleHorizonPer> findAllByOrderByProfilAscAnneesMinAsc();

}
