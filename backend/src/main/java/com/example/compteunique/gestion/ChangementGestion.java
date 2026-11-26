package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-314 / CU-338 : un choix de mode et de profil de gestion (création comprise), jamais modifié ; toujours gratuit.
@Entity
@Table(schema = "gestion", name = "changement_gestion")
public class ChangementGestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String numeroContrat;

	@Enumerated(EnumType.STRING)
	private ModeGestion mode;

	@Enumerated(EnumType.STRING)
	private ProfilGestion profil;

	private Integer horizonAnnees;

	private BigDecimal frais;

	private OffsetDateTime changeLe;

	protected ChangementGestion() {
	}

	ChangementGestion(String numeroContrat, ModeGestion mode, ProfilGestion profil, Integer horizonAnnees,
			OffsetDateTime changeLe) {
		this.numeroContrat = numeroContrat;
		this.mode = mode;
		this.profil = profil;
		this.horizonAnnees = horizonAnnees;
		this.frais = BigDecimal.ZERO.setScale(2);
		this.changeLe = changeLe;
	}

	public ModeGestion getMode() {
		return mode;
	}

	public ProfilGestion getProfil() {
		return profil;
	}

	public Integer getHorizonAnnees() {
		return horizonAnnees;
	}

	public BigDecimal getFrais() {
		return frais;
	}

	public OffsetDateTime getChangeLe() {
		return changeLe;
	}

}
