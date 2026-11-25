package com.example.compteunique.ordres;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-376 : position de test (dev et qualif), en euros ou en parts, remplacée par les positions de TECH-10.
@Entity
@Table(schema = "ordres", name = "position_test")
class PositionTest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String numeroContrat;

	private String codeIsin;

	private BigDecimal montant;

	private BigDecimal nombreParts;

	private OffsetDateTime enregistreeLe;

	protected PositionTest() {
	}

	PositionTest(String numeroContrat, String codeIsin, BigDecimal montant, BigDecimal nombreParts,
			OffsetDateTime enregistreeLe) {
		this.numeroContrat = numeroContrat;
		this.codeIsin = codeIsin;
		this.montant = montant;
		this.nombreParts = nombreParts;
		this.enregistreeLe = enregistreeLe;
	}

	String getNumeroContrat() {
		return numeroContrat;
	}

	String getCodeIsin() {
		return codeIsin;
	}

	BigDecimal getMontant() {
		return montant;
	}

	BigDecimal getNombreParts() {
		return nombreParts;
	}

	OffsetDateTime getEnregistreeLe() {
		return enregistreeLe;
	}

}
