package com.example.compteunique.virement;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-11 : virement refusé et renvoyé à l'émetteur, table créée par db/migration/V17__mouvement_virement_entrant.sql.
@Entity
@Table(name = "retour_virement")
public class RetourVirement {

	/** Code SEPA : compte clôturé. */
	public static final String COMPTE_CLOTURE = "AC04";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "compte_id", nullable = false, updatable = false)
	private Long compteId;

	@Column(name = "montant_centimes", nullable = false, updatable = false)
	private long montantCentimes;

	@Column(nullable = false, length = 140, updatable = false)
	private String libelle;

	@Column(nullable = false, length = 70, updatable = false)
	private String emetteur;

	@Column(nullable = false, length = 4, updatable = false)
	private String motif;

	@Column(nullable = false, updatable = false)
	private boolean simule;

	@Column(name = "retourne_le", nullable = false, updatable = false)
	private OffsetDateTime retourneLe;

	protected RetourVirement() {
	}

	public RetourVirement(Long compteId, VirementEntrant virement, String motif, OffsetDateTime retourneLe) {
		this.compteId = compteId;
		this.montantCentimes = virement.montantCentimes();
		this.libelle = virement.libelle();
		this.emetteur = virement.emetteur();
		this.simule = virement.simule();
		this.motif = motif;
		this.retourneLe = retourneLe;
	}

	public Long getId() {
		return id;
	}

	public Long getCompteId() {
		return compteId;
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

	public String getMotif() {
		return motif;
	}

	public boolean isSimule() {
		return simule;
	}

	public OffsetDateTime getRetourneLe() {
		return retourneLe;
	}

}
