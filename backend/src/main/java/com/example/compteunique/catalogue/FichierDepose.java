package com.example.compteunique.catalogue;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-381 : fichier catalogue déposé sur la page de test (prototype : pas de dépôt fournisseur réel).
@Entity
@Table(schema = "catalogue", name = "fichier_depose")
class FichierDepose {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String nomFichier;

	private LocalDate dateFichier;

	private String contenu;

	private OffsetDateTime deposeLe;

	protected FichierDepose() {
	}

	FichierDepose(String nomFichier, LocalDate dateFichier, String contenu, OffsetDateTime deposeLe) {
		this.nomFichier = nomFichier;
		this.dateFichier = dateFichier;
		this.contenu = contenu;
		this.deposeLe = deposeLe;
	}

	String getNomFichier() {
		return nomFichier;
	}

	LocalDate getDateFichier() {
		return dateFichier;
	}

	String getContenu() {
		return contenu;
	}

	OffsetDateTime getDeposeLe() {
		return deposeLe;
	}

}
