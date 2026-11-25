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

// CU-376 (TECH-08) : un ordre, identifié par l'identifiant créé par l'écran (db/migration/V30__ordres.sql).
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

	private OffsetDateTime demandeLe;

	private LocalDate dateExecutionPrevue;

	@Enumerated(EnumType.STRING)
	private StatutOrdre statut;

	private String motifRejet;

	private UUID evenementId;

	protected Ordre() {
	}

	Ordre(DemandeOrdre demande, OffsetDateTime demandeLe, LocalDate dateExecutionPrevue) {
		this.id = demande.id();
		this.numeroContrat = demande.numeroContrat().trim();
		this.type = demande.type();
		this.supportDepart = demande.supportDepart();
		this.supportArrivee = demande.supportArrivee();
		this.montant = demande.montant().setScale(2, RoundingMode.UNNECESSARY);
		this.demandeLe = demandeLe;
		this.dateExecutionPrevue = dateExecutionPrevue;
		this.statut = StatutOrdre.DEMANDE;
	}

	void evenementEnregistre(UUID evenement) {
		this.evenementId = evenement;
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

	public BigDecimal getMontant() {
		return montant;
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

}
