package com.example.compteunique.compte;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-11 (US-06) : mouvement du compte, table créée par db/migration/V17__mouvement_virement_entrant.sql.
// CU-144 : virement sortant (V18__mouvement_virement_sortant.sql) : bénéficiaire et son IBAN, sans émetteur.
// simule : virement d'un bouchon de test (CU-143, CU-144), aucun virement réel.
@Entity
@Table(name = "mouvement")
public class Mouvement {

	public static final String VIREMENT_ENTRANT = "VIREMENT_ENTRANT";
	public static final String VIREMENT_SORTANT = "VIREMENT_SORTANT";

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

	@Column(length = 70, updatable = false)
	private String emetteur;

	@Column(length = 70, updatable = false)
	private String beneficiaire;

	@Column(name = "iban_beneficiaire", length = 34, updatable = false)
	private String ibanBeneficiaire;

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

	/** Virement sortant (CU-144) : le montant est débité du compte, au profit du bénéficiaire. */
	public static Mouvement virementSortant(Long compteId, long montantCentimes, String libelle, String beneficiaire,
			String ibanBeneficiaire, boolean simule, OffsetDateTime effectueLe) {
		Mouvement mouvement = new Mouvement();
		mouvement.compteId = compteId;
		mouvement.type = VIREMENT_SORTANT;
		mouvement.montantCentimes = montantCentimes;
		mouvement.libelle = libelle;
		mouvement.beneficiaire = beneficiaire;
		mouvement.ibanBeneficiaire = ibanBeneficiaire;
		mouvement.simule = simule;
		mouvement.effectueLe = effectueLe;
		return mouvement;
	}

	public boolean isSortant() {
		return VIREMENT_SORTANT.equals(type);
	}

	public String getBeneficiaire() {
		return beneficiaire;
	}

	public String getIbanBeneficiaire() {
		return ibanBeneficiaire;
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
