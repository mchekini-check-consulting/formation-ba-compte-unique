package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-10 : une version du profil client, créée à chaque validation du questionnaire et jamais modifiée.
@Entity
@Table(schema = "profil_client", name = "profil_kyc")
public class ProfilKyc {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID customerId;

	private int version;

	private String versionQuestionnaire;

	private OffsetDateTime valideLe;

	private OffsetDateTime revaliderLe;

	@Embedded
	private Reponses reponses;

	private String profilInvestisseur;

	private int scoreInvestisseur;

	private String versionRegles;

	private OffsetDateTime profilCalculeLe;

	protected ProfilKyc() {
	}

	ProfilKyc(UUID customerId, int version, String versionQuestionnaire, OffsetDateTime valideLe, Reponses reponses,
			String profilInvestisseur, int scoreInvestisseur, String versionRegles) {
		this.customerId = customerId;
		this.version = version;
		this.versionQuestionnaire = versionQuestionnaire;
		this.valideLe = valideLe;
		this.revaliderLe = valideLe.plusMonths(12);
		this.reponses = reponses;
		this.profilInvestisseur = profilInvestisseur;
		this.scoreInvestisseur = scoreInvestisseur;
		this.versionRegles = versionRegles;
		this.profilCalculeLe = valideLe;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public int getVersion() {
		return version;
	}

	public OffsetDateTime getValideLe() {
		return valideLe;
	}

	public OffsetDateTime getRevaliderLe() {
		return revaliderLe;
	}

	public Reponses getReponses() {
		return reponses;
	}

	public String getProfilInvestisseur() {
		return profilInvestisseur;
	}

	public int getScoreInvestisseur() {
		return scoreInvestisseur;
	}

}
