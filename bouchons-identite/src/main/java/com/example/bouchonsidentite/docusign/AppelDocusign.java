package com.example.bouchonsidentite.docusign;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Trace d'un appel au bouchon Docusign (table créée par db/migration/V3__appels_docusign.sql).
@Entity
@Table(name = "appel_docusign")
public class AppelDocusign {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false, length = 64)
	private String dossierId;

	@Column(name = "enveloppe_id", nullable = false, length = 64)
	private String enveloppeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OperationDocusign operation;

	@Column(name = "code_recu", length = 6)
	private String codeRecu;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ResultatDocusign resultat;

	@Column(name = "appele_le", nullable = false)
	private OffsetDateTime appeleLe;

	protected AppelDocusign() {
	}

	public AppelDocusign(String dossierId, String enveloppeId, OperationDocusign operation, String codeRecu,
			ResultatDocusign resultat, OffsetDateTime appeleLe) {
		this.dossierId = dossierId;
		this.enveloppeId = enveloppeId;
		this.operation = operation;
		this.codeRecu = codeRecu;
		this.resultat = resultat;
		this.appeleLe = appeleLe;
	}

	public Long getId() {
		return id;
	}

	public String getDossierId() {
		return dossierId;
	}

	public String getEnveloppeId() {
		return enveloppeId;
	}

	public OperationDocusign getOperation() {
		return operation;
	}

	public String getCodeRecu() {
		return codeRecu;
	}

	public ResultatDocusign getResultat() {
		return resultat;
	}

	public OffsetDateTime getAppeleLe() {
		return appeleLe;
	}

}
