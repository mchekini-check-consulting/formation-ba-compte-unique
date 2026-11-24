package com.example.compteunique.datemetier;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-380 : date simulée en vigueur (db/migration/V25__date_metier.sql). Une seule ligne, id = 1.
@Entity
@Table(schema = "date_metier", name = "reglage")
class ReglageDateMetier {

	static final short ID = 1;

	@Id
	private Short id;

	@Column(name = "decalage_secondes", nullable = false)
	private long decalageSecondes;

	@Column(name = "modifie_le", nullable = false)
	private OffsetDateTime modifieLe;

	protected ReglageDateMetier() {
	}

	ReglageDateMetier(long decalageSecondes, OffsetDateTime modifieLe) {
		this.id = ID;
		this.decalageSecondes = decalageSecondes;
		this.modifieLe = modifieLe;
	}

	long getDecalageSecondes() {
		return decalageSecondes;
	}

	void decaler(long secondes, OffsetDateTime le) {
		this.decalageSecondes = secondes;
		this.modifieLe = le;
	}

}
