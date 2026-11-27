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

// CU-349 : opération vers une unité de compte refusée à la validation, car inadaptée au profil en vigueur.
@Entity
@Table(schema = "catalogue", name = "operation_refusee")
public class OperationRefusee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID customerId;

	@Enumerated(EnumType.STRING)
	private Produit produit;

	private String codeIsin;

	private String profil;

	private String typeOperation;

	private OffsetDateTime refuseLe;

	protected OperationRefusee() {
	}

	OperationRefusee(UUID customerId, Produit produit, String codeIsin, String profil, String typeOperation,
			OffsetDateTime refuseLe) {
		this.customerId = customerId;
		this.produit = produit;
		this.codeIsin = codeIsin;
		this.profil = profil;
		this.typeOperation = typeOperation;
		this.refuseLe = refuseLe;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public Produit getProduit() {
		return produit;
	}

	public String getCodeIsin() {
		return codeIsin;
	}

	public String getProfil() {
		return profil;
	}

	public String getTypeOperation() {
		return typeOperation;
	}

	public OffsetDateTime getRefuseLe() {
		return refuseLe;
	}

}
