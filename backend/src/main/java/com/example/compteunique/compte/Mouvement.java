package com.example.compteunique.compte;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-11 (US-06) : mouvement du compte, table créée par db/migration/V17__mouvement_virement_entrant.sql.
// simule : virement du bouchon de test (CU-143), aucun virement réel.
@Entity
@Table(name = "mouvement")
public class Mouvement {

	public static final String VIREMENT_ENTRANT = "VIREMENT_ENTRANT";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "compte_id", nullable = false, updatable = false)
	private Long compteId;

	@Column(nullable = false, length = 20, updatable = false)
	private String type;

	@Column(name = "montant_centimes", nullable = false, updatable = false)
	private long montantCentimes;

	@Column(nullable = false, length = 140, updatable = false)
	private String libelle;

	@Column(nullable = false, length = 70, updatable = false)
	private String emetteur;

	@Column(nullable = false, updatable = false)
	private boolean simule;

	@Column(name = "effectue_le", nullable = false, updatable = false)
	private OffsetDateTime effectueLe;

	protected Mouvement() {
	}

	/** Virement entrant (CU-11). */
	public Mouvement(Long compteId, long montantCentimes, String libelle, String emetteur, boolean simule,
			OffsetDateTime effectueLe) {
		this.compteId = compteId;
		this.type = VIREMENT_ENTRANT;
		this.montantCentimes = montantCentimes;
		this.libelle = libelle;
		this.emetteur = emetteur;
		this.simule = simule;
		this.effectueLe = effectueLe;
	}

	public Long getId() {
		return id;
	}

	public Long getCompteId() {
		return compteId;
	}

	public String getType() {
		return type;
	}

	public long getMontantCentimes() {
		return montantCentimes;
	}

	public String getLibelle() {
		return libelle;
	}

	public String getEmetteur() {
		return emetteur;
	}

	public boolean isSimule() {
		return simule;
	}

	public OffsetDateTime getEffectueLe() {
		return effectueLe;
	}

}
