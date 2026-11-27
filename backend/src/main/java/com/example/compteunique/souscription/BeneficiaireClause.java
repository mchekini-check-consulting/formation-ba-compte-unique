package com.example.compteunique.souscription;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-330 (AV-01) : bénéficiaire de la clause de l'assurance vie, désignation initiale (version 1).
@Entity
@Table(schema = "souscription", name = "beneficiaire")
public class BeneficiaireClause {

	static final int VERSION_INITIALE = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long souscriptionId;

	private int version;

	private int rang;

	private String nom;

	private String prenom;

	private LocalDate dateNaissance;

	private BigDecimal quotePartPct;

	protected BeneficiaireClause() {
	}

	BeneficiaireClause(Long souscriptionId, int rang, String nom, String prenom, LocalDate dateNaissance,
			BigDecimal quotePartPct) {
		this.souscriptionId = souscriptionId;
		this.version = VERSION_INITIALE;
		this.rang = rang;
		this.nom = nom;
		this.prenom = prenom;
		this.dateNaissance = dateNaissance;
		this.quotePartPct = quotePartPct;
	}

	public int getVersion() {
		return version;
	}

	public String getNom() {
		return nom;
	}

	public String getPrenom() {
		return prenom;
	}

	public LocalDate getDateNaissance() {
		return dateNaissance;
	}

	public BigDecimal getQuotePartPct() {
		return quotePartPct;
	}

}
