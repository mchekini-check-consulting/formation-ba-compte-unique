package com.example.compteunique.beneficiaire;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-145 critère 7 : journal de test du bouchon, table créée par db/migration/V19__verification_beneficiaire.sql.
@Entity
@Table(name = "verification_beneficiaire_appel")
public class AppelVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "appele_le", nullable = false, updatable = false)
	private OffsetDateTime appeleLe;

	@Column(nullable = false, length = 34, updatable = false)
	private String iban;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private ResultatVerification resultat;

	protected AppelVerification() {
	}

	public AppelVerification(OffsetDateTime appeleLe, String iban, ResultatVerification resultat) {
		this.appeleLe = appeleLe;
		this.iban = iban;
		this.resultat = resultat;
	}

	public Long getId() {
		return id;
	}

	public OffsetDateTime getAppeleLe() {
		return appeleLe;
	}

	public String getIban() {
		return iban;
	}

	public ResultatVerification getResultat() {
		return resultat;
	}

}
