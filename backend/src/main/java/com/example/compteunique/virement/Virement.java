package com.example.compteunique.virement;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.beneficiaire.Beneficiaire;

// CU-147 (US-21) : virement vers un bénéficiaire, table créée par db/migration/V21__virement.sql.
// Nom et IBAN du bénéficiaire recopiés au moment du virement (un bénéficiaire supprimé garde son historique).
// Statut : en attente de signature (code SMS), exécuté, refusé par la banque destinataire, ou annulé (code faux trois
// fois, code expiré, solde devenu insuffisant). Code haché seulement.
@Entity
@Table(name = "virement")
public class Virement {

	public static final String EN_ATTENTE_SIGNATURE = "EN_ATTENTE_SIGNATURE";
	public static final String EXECUTE = "EXECUTE";
	public static final String REFUSE = "REFUSE";
	public static final String ANNULE = "ANNULE";
	public static final int LIBELLE_MAX = 140;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "compte_id", nullable = false, updatable = false)
	private Long compteId;

	@Column(name = "beneficiaire_id", nullable = false, updatable = false)
	private Long beneficiaireId;

	@Column(name = "nom_beneficiaire", nullable = false, length = Beneficiaire.NOM_MAX, updatable = false)
	private String nomBeneficiaire;

	@Column(name = "iban_beneficiaire", nullable = false, length = 34, updatable = false)
	private String ibanBeneficiaire;

	@Column(name = "montant_centimes", nullable = false, updatable = false)
	private long montantCentimes;

	@Column(nullable = false, length = LIBELLE_MAX, updatable = false)
	private String libelle;

	@Column(nullable = false, length = 30)
	private String statut;

	@Column(name = "motif_refus", length = 140)
	private String motifRefus;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private OffsetDateTime creeLe;

	@Column(name = "execute_le")
	private OffsetDateTime executeLe;

	@Column(name = "code_hache", nullable = false, length = 100, updatable = false)
	private String codeHache;

	@Column(name = "code_envoye_le", nullable = false, updatable = false)
	private OffsetDateTime codeEnvoyeLe;

	@Column(name = "codes_faux", nullable = false)
	private int codesFaux;

	protected Virement() {
	}

	Virement(Long compteId, Beneficiaire beneficiaire, long montantCentimes, String libelle, String codeHache,
			OffsetDateTime creeLe) {
		this.compteId = compteId;
		this.beneficiaireId = beneficiaire.getId();
		this.nomBeneficiaire = beneficiaire.getNomVerifie();
		this.ibanBeneficiaire = beneficiaire.getIban();
		this.montantCentimes = montantCentimes;
		this.libelle = libelle;
		this.codeHache = codeHache;
		this.codeEnvoyeLe = creeLe;
		this.creeLe = creeLe;
		this.statut = EN_ATTENTE_SIGNATURE;
	}

	boolean isEnAttenteDeSignature() {
		return EN_ATTENTE_SIGNATURE.equals(statut);
	}

	boolean isExecute() {
		return EXECUTE.equals(statut);
	}

	void codeFaux() {
		codesFaux++;
	}

	void annuler(String motif) {
		this.statut = ANNULE;
		this.motifRefus = motif;
	}

	void executer(OffsetDateTime le) {
		this.statut = EXECUTE;
		this.executeLe = le;
	}

	void refuser(String motif) {
		this.statut = REFUSE;
		this.motifRefus = motif;
	}

	public Long getId() {
		return id;
	}

	public Long getCompteId() {
		return compteId;
	}

	public Long getBeneficiaireId() {
		return beneficiaireId;
	}

	public String getNomBeneficiaire() {
		return nomBeneficiaire;
	}

	public String getIbanBeneficiaire() {
		return ibanBeneficiaire;
	}

	public long getMontantCentimes() {
		return montantCentimes;
	}

	public String getLibelle() {
		return libelle;
	}

	public String getStatut() {
		return statut;
	}

	public String getMotifRefus() {
		return motifRefus;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

	public OffsetDateTime getExecuteLe() {
		return executeLe;
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

}
