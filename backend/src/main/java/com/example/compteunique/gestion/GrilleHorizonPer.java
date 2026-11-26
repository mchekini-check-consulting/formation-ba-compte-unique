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

// CU-314 : une ligne de la grille PER par horizon (profil, à partir de annees_min années avant la retraite).
// Valeurs fictives tant que a_valider est vrai ; la conformité les relève dans l'arrêté du 7 août 2019 modifié.
@Entity
@Table(schema = "gestion", name = "grille_horizon_per")
public class GrilleHorizonPer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private ProfilGestion profil;

	private int anneesMin;

	private BigDecimal partMinFaibleRisquePct;

	private BigDecimal partMinNonCotePct;

	private boolean aValider;

	private OffsetDateTime modifieLe;

	protected GrilleHorizonPer() {
	}

	GrilleHorizonPer(ProfilGestion profil, int anneesMin, BigDecimal faibleRisque, BigDecimal nonCote,
			OffsetDateTime le) {
		this.profil = profil;
		this.anneesMin = anneesMin;
		this.aValider = true;
		modifier(faibleRisque, nonCote, le);
	}

	void modifier(BigDecimal faibleRisque, BigDecimal nonCote, OffsetDateTime le) {
		this.partMinFaibleRisquePct = faibleRisque;
		this.partMinNonCotePct = nonCote;
		this.modifieLe = le;
	}

	public Long getId() {
		return id;
	}

	public ProfilGestion getProfil() {
		return profil;
	}

	public int getAnneesMin() {
		return anneesMin;
	}

	public BigDecimal getPartMinFaibleRisquePct() {
		return partMinFaibleRisquePct;
	}

	public BigDecimal getPartMinNonCotePct() {
		return partMinNonCotePct;
	}

	public boolean isAValider() {
		return aValider;
	}

	public OffsetDateTime getModifieLe() {
		return modifieLe;
	}

}
