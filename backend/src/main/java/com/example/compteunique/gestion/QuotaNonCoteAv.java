package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-338 : quota minimal de non coté de la gestion pilotée en assurance vie, par profil (loi Industrie verte).
@Entity
@Table(schema = "gestion", name = "quota_non_cote_av")
public class QuotaNonCoteAv {

	@Id
	@Enumerated(EnumType.STRING)
	private ProfilGestion profil;

	private BigDecimal partMinNonCotePct;

	private OffsetDateTime modifieLe;

	protected QuotaNonCoteAv() {
	}

	QuotaNonCoteAv(ProfilGestion profil, BigDecimal partMinNonCotePct, OffsetDateTime le) {
		this.profil = profil;
		modifier(partMinNonCotePct, le);
	}

	void modifier(BigDecimal part, OffsetDateTime le) {
		this.partMinNonCotePct = part;
		this.modifieLe = le;
	}

	public ProfilGestion getProfil() {
		return profil;
	}

	public BigDecimal getPartMinNonCotePct() {
		return partMinNonCotePct;
	}

	public OffsetDateTime getModifieLe() {
		return modifieLe;
	}

}
