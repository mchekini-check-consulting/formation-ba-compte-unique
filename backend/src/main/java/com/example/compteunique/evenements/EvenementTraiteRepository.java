package com.example.compteunique.evenements;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EvenementTraiteRepository extends JpaRepository<EvenementTraite, Long> {

	/** 1 si l'événement est noté traité pour ce groupe, 0 s'il l'était déjà (même avec 2 lecteurs en parallèle). */
	@Modifying
	@Query(value = "insert into evenements.traite (groupe, evenement_id, traite_le) values (:groupe, :evenementId, :le)"
			+ " on conflict (groupe, evenement_id) do nothing", nativeQuery = true)
	int noter(String groupe, UUID evenementId, OffsetDateTime le);

}
