package com.example.compteunique.catalogue;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-381 : événement « catalogue mis à jour », un seul par batch terminé (ADR-001, règle 1).
@Entity
@Table(schema = "catalogue", name = "evenement")
class EvenementCatalogue {

	static final String CATALOGUE_MIS_A_JOUR = "CATALOGUE_MIS_A_JOUR";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String type;

	private LocalDate dateMetier;

	private Long journalId;

	private int ajoutes;

	private int modifies;

	private int retires;

	private int rejetees;

	private OffsetDateTime creeLe;

	protected EvenementCatalogue() {
	}

	EvenementCatalogue(JournalBatch journal) {
		this.type = CATALOGUE_MIS_A_JOUR;
		this.dateMetier = journal.getDateMetier();
		this.journalId = journal.getId();
		this.ajoutes = journal.getAjoutes();
		this.modifies = journal.getModifies();
		this.retires = journal.getRetires();
		this.rejetees = journal.getRejetees();
		this.creeLe = journal.getFin();
	}

	String getType() {
		return type;
	}

	LocalDate getDateMetier() {
		return dateMetier;
	}

	int getAjoutes() {
		return ajoutes;
	}

	int getModifies() {
		return modifies;
	}

	int getRetires() {
		return retires;
	}

	int getRejetees() {
		return rejetees;
	}

}
