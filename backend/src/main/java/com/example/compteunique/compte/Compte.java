package com.example.compteunique.compte;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Compte courant (CU-13, US-08), table créée par db/migration/V5__compte.sql.
// Créé au statut ACTIF, à 0 €, sans versement.
@Entity
@Table(name = "compte")
public class Compte {

	public static final String ACTIF = "ACTIF";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false, unique = true, updatable = false)
	private Long dossierId;

	@Column(name = "customer_id", nullable = false, unique = true, updatable = false)
	private UUID customerId;

	@Column(nullable = false, unique = true, length = 27, updatable = false)
	private String iban;

	@Column(name = "numero_compte", nullable = false, unique = true, length = 11, updatable = false)
	private String numeroCompte;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal solde;

	@Column(nullable = false, length = 20)
	private String statut;

	@Column(name = "ouvert_le", nullable = false, updatable = false)
	private OffsetDateTime ouvertLe;

	@Column(name = "identifiant_connexion_id", nullable = false, unique = true, updatable = false)
	private Long identifiantConnexionId;

	protected Compte() {
	}

	public Compte(Long dossierId, UUID customerId, Iban iban, Long identifiantConnexionId, OffsetDateTime ouvertLe) {
		this.dossierId = dossierId;
		this.customerId = customerId;
		this.iban = iban.valeur();
		this.numeroCompte = iban.numeroCompte();
		this.solde = BigDecimal.ZERO;
		this.statut = ACTIF;
		this.ouvertLe = ouvertLe;
		this.identifiantConnexionId = identifiantConnexionId;
	}

	public Long getId() {
		return id;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getIban() {
		return iban;
	}

	public String getNumeroCompte() {
		return numeroCompte;
	}

	public BigDecimal getSolde() {
		return solde;
	}

	public String getStatut() {
		return statut;
	}

	public OffsetDateTime getOuvertLe() {
		return ouvertLe;
	}

	public Long getIdentifiantConnexionId() {
		return identifiantConnexionId;
	}

	/** IBAN tel qu'affiché à la cliente (quatre derniers chiffres). */
	public String getIbanMasque() {
		return Iban.de(numeroCompte).masque();
	}

}
