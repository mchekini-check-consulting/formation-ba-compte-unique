package com.example.compteunique.beneficiaire;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-145 critère 4 : sélecteur « Service indisponible » du bouchon, une seule ligne (id = 1),
// table créée par db/migration/V19__verification_beneficiaire.sql.
@Entity
@Table(name = "verification_beneficiaire_bouchon")
public class BouchonVerification {

	static final short ID = 1;

	@Id
	private Short id;

	@Column(nullable = false)
	private boolean indisponible;

	protected BouchonVerification() {
	}

	BouchonVerification(boolean indisponible) {
		this.id = ID;
		this.indisponible = indisponible;
	}

	public boolean isIndisponible() {
		return indisponible;
	}

	void setIndisponible(boolean indisponible) {
		this.indisponible = indisponible;
	}

}
