package com.example.bouchonsidentite.selfie;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.bouchonsidentite.commun.StatutDossier;

// Résultat d'une vérification de selfie (table créée par db/migration/V2__verifications.sql).
// Aucune image : seulement le résultat, le score, le statut et la date.
@Entity
@Table(name = "verification_selfie")
public class VerificationSelfie {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false, length = 64)
	private String dossierId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ResultatSelfie resultat;

	private Integer score;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut_dossier", nullable = false, length = 30)
	private StatutDossier statutDossier;

	@Column(name = "verifie_le", nullable = false)
	private OffsetDateTime verifieLe;

	protected VerificationSelfie() {
	}

	public VerificationSelfie(String dossierId, ResultatSelfie resultat, Integer score, StatutDossier statutDossier,
			OffsetDateTime verifieLe) {
		this.dossierId = dossierId;
		this.resultat = resultat;
		this.score = score;
		this.statutDossier = statutDossier;
		this.verifieLe = verifieLe;
	}

	public Long getId() {
		return id;
	}

	public String getDossierId() {
		return dossierId;
	}

	public ResultatSelfie getResultat() {
		return resultat;
	}

	public Integer getScore() {
		return score;
	}

	public StatutDossier getStatutDossier() {
		return statutDossier;
	}

	public OffsetDateTime getVerifieLe() {
		return verifieLe;
	}

}
