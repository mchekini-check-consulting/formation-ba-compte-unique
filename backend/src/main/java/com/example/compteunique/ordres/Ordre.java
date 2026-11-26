package com.example.compteunique.ordres;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.ordres.OrdresService.DemandeOrdre;

// CU-376 (TECH-08) : un ordre, identifié par l'identifiant créé par l'écran (db/migration/V31__ordres.sql), en euros
// (montant) ou en nombre de parts du support vendu, et sa date d'annulation (V33__ordres_parts_et_annulation.sql).
@Entity
@Table(schema = "ordres", name = "ordre")
public class Ordre {

	@Id
	private UUID id;

	private String numeroContrat;

	@Enumerated(EnumType.STRING)
	private TypeOrdre type;

	private String supportDepart;

	private String supportArrivee;

	private BigDecimal montant;

	private BigDecimal nombreParts;

	private OffsetDateTime demandeLe;

	private LocalDate dateExecutionPrevue;

	@Enumerated(EnumType.STRING)
	private StatutOrdre statut;

	private String motifRejet;

	private UUID evenementId;

	private OffsetDateTime annuleLe;

	protected Ordre() {
	}

	Ordre(DemandeOrdre demande, OffsetDateTime demandeLe, LocalDate dateExecutionPrevue) {
		this.id = demande.id();
		this.numeroContrat = demande.numeroContrat().trim();
		this.type = demande.type();
		this.supportDepart = demande.supportDepart();
		this.supportArrivee = demande.supportArrivee();
		this.montant = demande.montant() == null ? null : demande.montant().setScale(2, RoundingMode.UNNECESSARY);
		this.nombreParts = demande.nombreParts() == null ? null
				: demande.nombreParts().setScale(6, RoundingMode.UNNECESSARY);
		this.demandeLe = demandeLe;
		this.dateExecutionPrevue = dateExecutionPrevue;
		this.statut = StatutOrdre.DEMANDE;
	}

	void evenementEnregistre(UUID evenement) {
		this.evenementId = evenement;
	}

	void annuler(OffsetDateTime le) {
		this.statut = StatutOrdre.ANNULE;
		this.annuleLe = le;
	}

	/** Vrai pour un ordre exprimé en nombre de parts (le montant est alors vide). */
	public boolean enParts() {
		return nombreParts != null;
	}

	public UUID getId() {
		return id;
	}

	public String getNumeroContrat() {
		return numeroContrat;
	}

	public TypeOrdre getType() {
		return type;
	}

	public String getSupportDepart() {
		return supportDepart;
	}

	public String getSupportArrivee() {
		return supportArrivee;
	}

	/** Montant en euros ; vide pour un ordre en parts. */
	public BigDecimal getMontant() {
		return montant;
	}

	/** Nombre de parts vendues ; vide pour un ordre en euros. */
	public BigDecimal getNombreParts() {
		return nombreParts;
	}

	public OffsetDateTime getDemandeLe() {
		return demandeLe;
	}

	public LocalDate getDateExecutionPrevue() {
		return dateExecutionPrevue;
	}

	public StatutOrdre getStatut() {
		return statut;
	}

	public String getMotifRejet() {
		return motifRejet;
	}

	public UUID getEvenementId() {
		return evenementId;
	}

	public OffsetDateTime getAnnuleLe() {
		return annuleLe;
	}

}
