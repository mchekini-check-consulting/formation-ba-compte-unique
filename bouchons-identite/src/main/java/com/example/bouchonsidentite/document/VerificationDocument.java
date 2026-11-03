package com.example.bouchonsidentite.document;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Résultat d'une vérification de pièce (table créée par db/migration/V2__verifications.sql).
// Ni image ni donnée d'identité : seulement le statut, le motif, la date et la concordance par champ.
@Entity
@Table(name = "verification_document")
public class VerificationDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false, length = 64)
	private String dossierId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatutVerificationDocument statut;

	@Column(length = 200)
	private String motif;

	@Column(name = "verifie_le", nullable = false)
	private OffsetDateTime verifieLe;

	@Column(name = "concordance_nom")
	private Boolean concordanceNom;

	@Column(name = "concordance_prenoms")
	private Boolean concordancePrenoms;

	@Column(name = "concordance_date_naissance")
	private Boolean concordanceDateNaissance;

	@Column(name = "concordance_date_expiration")
	private Boolean concordanceDateExpiration;

	protected VerificationDocument() {
	}

	public VerificationDocument(String dossierId, StatutVerificationDocument statut, String motif,
			Concordance concordance, OffsetDateTime verifieLe) {
		this.dossierId = dossierId;
		this.statut = statut;
		this.motif = motif;
		this.verifieLe = verifieLe;
		if (concordance != null) {
			this.concordanceNom = concordance.nomNaissance();
			this.concordancePrenoms = concordance.prenoms();
			this.concordanceDateNaissance = concordance.dateNaissance();
			this.concordanceDateExpiration = concordance.dateExpiration();
		}
	}

	public Long getId() {
		return id;
	}

	public String getDossierId() {
		return dossierId;
	}

	public StatutVerificationDocument getStatut() {
		return statut;
	}

	public String getMotif() {
		return motif;
	}

	public OffsetDateTime getVerifieLe() {
		return verifieLe;
	}

	public Boolean getConcordanceNom() {
		return concordanceNom;
	}

	public Boolean getConcordancePrenoms() {
		return concordancePrenoms;
	}

	public Boolean getConcordanceDateNaissance() {
		return concordanceDateNaissance;
	}

	public Boolean getConcordanceDateExpiration() {
		return concordanceDateExpiration;
	}

}
