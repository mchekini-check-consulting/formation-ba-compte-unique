package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-338 : un arbitrage d'un rééquilibrage, avec l'identifiant de l'ordre passé au module Ordres.
@Entity
@Table(schema = "gestion", name = "reequilibrage_ordre")
public class ReequilibrageOrdre {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID ordreId;

	private String supportDepart;

	private String supportArrivee;

	private BigDecimal montant;

	protected ReequilibrageOrdre() {
	}

	ReequilibrageOrdre(UUID ordreId, String supportDepart, String supportArrivee, BigDecimal montant) {
		this.ordreId = ordreId;
		this.supportDepart = supportDepart;
		this.supportArrivee = supportArrivee;
		this.montant = montant;
	}

	public UUID getOrdreId() {
		return ordreId;
	}

	public String getSupportDepart() {
		return supportDepart;
	}

	public String getSupportArrivee() {
		return supportArrivee;
	}

	public BigDecimal getMontant() {
		return montant;
	}

}
