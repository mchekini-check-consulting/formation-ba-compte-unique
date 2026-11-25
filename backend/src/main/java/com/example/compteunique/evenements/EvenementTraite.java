package com.example.compteunique.evenements;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-382 : un événement déjà traité par un module lecteur (règle 3 de l'ADR-001 : pas d'effet en double).
@Entity
@Table(schema = "evenements", name = "traite")
class EvenementTraite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String groupe;

	private UUID evenementId;

	private OffsetDateTime traiteLe;

	protected EvenementTraite() {
	}

	String getGroupe() {
		return groupe;
	}

	UUID getEvenementId() {
		return evenementId;
	}

	OffsetDateTime getTraiteLe() {
		return traiteLe;
	}

}
