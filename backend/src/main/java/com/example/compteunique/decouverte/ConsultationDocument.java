package com.example.compteunique.decouverte;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.decouverte.DocumentsProduits.Document;
import com.example.compteunique.decouverte.DocumentsProduits.Produit;

// CU-311 / CU-331 : une ouverture d'un document (date, heure et version), et la case « J'ai pris connaissance »
// cochée ensuite (coche_le, vide tant qu'elle ne l'est pas).
@Entity
@Table(schema = "decouverte", name = "consultation_document")
public class ConsultationDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID customerId;

	@Enumerated(EnumType.STRING)
	private Produit produit;

	@Enumerated(EnumType.STRING)
	private Document document;

	private String versionDocument;

	private OffsetDateTime ouvertLe;

	private OffsetDateTime cocheLe;

	protected ConsultationDocument() {
	}

	ConsultationDocument(UUID customerId, Produit produit, Document document, String versionDocument,
			OffsetDateTime ouvertLe) {
		this.customerId = customerId;
		this.produit = produit;
		this.document = document;
		this.versionDocument = versionDocument;
		this.ouvertLe = ouvertLe;
	}

	void cocher(OffsetDateTime le) {
		this.cocheLe = le;
	}

	public String getVersionDocument() {
		return versionDocument;
	}

	public OffsetDateTime getOuvertLe() {
		return ouvertLe;
	}

	public OffsetDateTime getCocheLe() {
		return cocheLe;
	}

}
