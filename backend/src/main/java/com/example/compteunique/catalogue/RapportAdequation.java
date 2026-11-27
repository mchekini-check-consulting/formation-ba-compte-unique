package com.example.compteunique.catalogue;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.catalogue.CatalogueService.Produit;

// CU-348 : rapport d'adéquation, une ligne par proposition de supports (date, profil utilisé, supports proposés).
@Entity
@Table(schema = "catalogue", name = "rapport_adequation")
public class RapportAdequation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID customerId;

	@Enumerated(EnumType.STRING)
	private Produit produit;

	private String profil;

	private String supports;

	private OffsetDateTime proposeLe;

	protected RapportAdequation() {
	}

	RapportAdequation(UUID customerId, Produit produit, String profil, String supports, OffsetDateTime proposeLe) {
		this.customerId = customerId;
		this.produit = produit;
		this.profil = profil;
		this.supports = supports;
		this.proposeLe = proposeLe;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public Produit getProduit() {
		return produit;
	}

	public String getProfil() {
		return profil;
	}

	public String getSupports() {
		return supports;
	}

	public OffsetDateTime getProposeLe() {
		return proposeLe;
	}

}
