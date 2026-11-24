package com.example.compteunique.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-381 : une ligne du fichier rejetée par le batch, avec son motif.
@Entity
@Table(schema = "catalogue", name = "ligne_rejetee")
class LigneRejetee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private int numeroLigne;

	private String codeIsin;

	private String motif;

	protected LigneRejetee() {
	}

	LigneRejetee(int numeroLigne, String codeIsin, String motif) {
		this.numeroLigne = numeroLigne;
		this.codeIsin = codeIsin;
		this.motif = motif;
	}

	int getNumeroLigne() {
		return numeroLigne;
	}

	String getCodeIsin() {
		return codeIsin;
	}

	String getMotif() {
		return motif;
	}

}
