package com.example.compteunique.signature;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Signature de la convention d'un dossier (CU-12), table créée par db/migration/V7__signature_convention.sql.
@Entity
@Table(name = "signature_convention")
public class SignatureConvention {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false, unique = true, updatable = false)
	private Long dossierId;

	@Column(name = "enveloppe_id", nullable = false, length = 64)
	private String enveloppeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut_enveloppe", nullable = false, length = 20)
	private StatutEnveloppe statutEnveloppe;

	@Column(name = "empreinte_document", nullable = false, length = 64)
	private String empreinteDocument;

	@Column(name = "code_envoye_le", nullable = false)
	private OffsetDateTime codeEnvoyeLe;

	@Column(name = "codes_faux", nullable = false)
	private int codesFaux;

	@Column(nullable = false)
	private int relances;

	@Column(name = "signee_le")
	private OffsetDateTime signeeLe;

	@Column(name = "motif_annulation", length = 40)
	private String motifAnnulation;

	protected SignatureConvention() {
	}

	public SignatureConvention(Long dossierId, String enveloppeId, String empreinteDocument,
			OffsetDateTime codeEnvoyeLe) {
		this.dossierId = dossierId;
		this.enveloppeId = enveloppeId;
		this.empreinteDocument = empreinteDocument;
		this.codeEnvoyeLe = codeEnvoyeLe;
		this.statutEnveloppe = StatutEnveloppe.ENVOYEE;
	}

	/** Nouvelle enveloppe après un code expiré : c'est une relance. Les codes faux restent comptés. */
	void relancer(String nouvelleEnveloppe, OffsetDateTime maintenant) {
		relances++;
		enveloppeId = nouvelleEnveloppe;
		codeEnvoyeLe = maintenant;
		statutEnveloppe = StatutEnveloppe.ENVOYEE;
	}

	void codeFaux() {
		codesFaux++;
	}

	void codeExpire() {
		statutEnveloppe = StatutEnveloppe.EXPIREE;
	}

	void signer(OffsetDateTime maintenant) {
		statutEnveloppe = StatutEnveloppe.SIGNEE;
		signeeLe = maintenant;
	}

	void annuler(String motif) {
		statutEnveloppe = StatutEnveloppe.ANNULEE;
		motifAnnulation = motif;
	}

	public Long getId() {
		return id;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public String getEnveloppeId() {
		return enveloppeId;
	}

	public StatutEnveloppe getStatutEnveloppe() {
		return statutEnveloppe;
	}

	public String getEmpreinteDocument() {
		return empreinteDocument;
	}

	public OffsetDateTime getCodeEnvoyeLe() {
		return codeEnvoyeLe;
	}

	public int getCodesFaux() {
		return codesFaux;
	}

	public int getRelances() {
		return relances;
	}

	public OffsetDateTime getSigneeLe() {
		return signeeLe;
	}

	public String getMotifAnnulation() {
		return motifAnnulation;
	}

}
