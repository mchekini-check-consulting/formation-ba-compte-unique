package com.example.compteunique.beneficiaire;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-146 : ajout d'un bénéficiaire vérifié, en attente du code SMS, table créée par db/migration/V20__beneficiaire.sql.
// Code haché seulement ; valable 5 minutes, à usage unique (statut EN_ATTENTE), 3 essais.
@Entity
@Table(name = "ajout_beneficiaire")
public class AjoutBeneficiaire {

	public static final String EN_ATTENTE = "EN_ATTENTE";
	public static final String CONFIRME = "CONFIRME";
	public static final String ANNULE = "ANNULE";
	public static final String EXPIRE = "EXPIRE";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private UUID customerId;

	@Column(nullable = false, length = 34, updatable = false)
	private String iban;

	@Column(name = "nom_verifie", nullable = false, length = Beneficiaire.NOM_MAX, updatable = false)
	private String nomVerifie;

	@Enumerated(EnumType.STRING)
	@Column(name = "resultat_verification", nullable = false, length = 20, updatable = false)
	private ResultatVerification resultatVerification;

	@Column(nullable = false, length = Beneficiaire.LIBELLE_MAX, updatable = false)
	private String libelle;

	@Column(name = "code_hache", nullable = false, length = 100, updatable = false)
	private String codeHache;

	@Column(name = "code_envoye_le", nullable = false, updatable = false)
	private OffsetDateTime codeEnvoyeLe;

	@Column(name = "codes_faux", nullable = false)
	private int codesFaux;

	@Column(nullable = false, length = 20)
	private String statut;

	protected AjoutBeneficiaire() {
	}

	AjoutBeneficiaire(UUID customerId, String iban, Verification verification, String libelle, String codeHache,
			OffsetDateTime codeEnvoyeLe) {
		this.customerId = customerId;
		this.iban = iban;
		this.nomVerifie = verification.nomVerifie();
		this.resultatVerification = verification.resultat();
		this.libelle = libelle;
		this.codeHache = codeHache;
		this.codeEnvoyeLe = codeEnvoyeLe;
		this.statut = EN_ATTENTE;
	}

	boolean isEnAttente() {
		return EN_ATTENTE.equals(statut);
	}

	void codeFaux() {
		codesFaux++;
	}

	void terminer(String nouveauStatut) {
		this.statut = nouveauStatut;
	}

	public Long getId() {
		return id;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getIban() {
		return iban;
	}

	public String getNomVerifie() {
		return nomVerifie;
	}

	public ResultatVerification getResultatVerification() {
		return resultatVerification;
	}

	public String getLibelle() {
		return libelle;
	}

	public String getCodeHache() {
		return codeHache;
	}

	public OffsetDateTime getCodeEnvoyeLe() {
		return codeEnvoyeLe;
	}

	public int getCodesFaux() {
		return codesFaux;
	}

	public String getStatut() {
		return statut;
	}

}
