package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// CU-381 : une valeur liquidative d'un support à une date, jamais modifiée (une seule par support et par date).
@Entity
@Table(schema = "catalogue", name = "historique_vl")
class HistoriqueVl {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "support_id")
	private Support support;

	private BigDecimal valeurLiquidative;

	private LocalDate dateVl;

	private String source;

	protected HistoriqueVl() {
	}

	HistoriqueVl(Support support, BigDecimal valeurLiquidative, LocalDate dateVl, String source) {
		this.support = support;
		this.valeurLiquidative = valeurLiquidative;
		this.dateVl = dateVl;
		this.source = source;
	}

	Support getSupport() {
		return support;
	}

	BigDecimal getValeurLiquidative() {
		return valeurLiquidative;
	}

	LocalDate getDateVl() {
		return dateVl;
	}

	String getSource() {
		return source;
	}

}
