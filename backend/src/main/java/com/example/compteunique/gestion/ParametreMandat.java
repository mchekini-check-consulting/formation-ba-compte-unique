package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.catalogue.CatalogueService.Produit;

// CU-314 / CU-338 : taux de frais tout compris et montant minimum de la gestion sous mandat, par produit.
@Entity
@Table(schema = "gestion", name = "parametre_mandat")
public class ParametreMandat {

	@Id
	@Enumerated(EnumType.STRING)
	private Produit produit;

	private BigDecimal tauxToutComprisPct;

	private BigDecimal montantMinimum;

	private OffsetDateTime modifieLe;

	protected ParametreMandat() {
	}

	ParametreMandat(Produit produit, BigDecimal tauxToutComprisPct, BigDecimal montantMinimum, OffsetDateTime le) {
		this.produit = produit;
		modifier(tauxToutComprisPct, montantMinimum, le);
	}

	void modifier(BigDecimal taux, BigDecimal minimum, OffsetDateTime le) {
		this.tauxToutComprisPct = taux;
		this.montantMinimum = minimum;
		this.modifieLe = le;
	}

	public Produit getProduit() {
		return produit;
	}

	public BigDecimal getTauxToutComprisPct() {
		return tauxToutComprisPct;
	}

	public BigDecimal getMontantMinimum() {
		return montantMinimum;
	}

	public OffsetDateTime getModifieLe() {
		return modifieLe;
	}

}
