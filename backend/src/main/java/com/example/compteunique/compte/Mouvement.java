package com.example.compteunique.compte;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Formula;

// CU-11 (US-06) : mouvement du compte, table créée par db/migration/V17__mouvement_virement_entrant.sql.
// CU-144 : virement sortant (V18__mouvement_virement_sortant.sql) : bénéficiaire et son IBAN, sans émetteur.
// simule : virement d'un bouchon de test (CU-143, CU-144), aucun virement réel.
// CU-147 (V21__virement.sql) : virement_id relie le débit (et le recrédit si la banque destinataire refuse) à son
// virement, dont le statut et le motif de refus sont lus pour l'affichage.
@Entity
@Table(name = "mouvement")
public class Mouvement {

	public static final String VIREMENT_ENTRANT = "VIREMENT_ENTRANT";
	public static final String VIREMENT_SORTANT = "VIREMENT_SORTANT";
	public static final String RECREDIT_VIREMENT = "RECREDIT_VIREMENT";

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

	@Column(name = "virement_id", updatable = false)
	private Long virementId;

	@Formula("(select v.statut from virement v where v.id = virement_id)")
	private String statutVirement;

	@Formula("(select v.motif_refus from virement v where v.id = virement_id)")
	private String motifRefus;

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

	/**
	 * CU-147 critère 14 : recrédit d'un virement refusé par la banque destinataire, au nom du bénéficiaire du
	 * virement.
	 */
	public static Mouvement recredit(Long compteId, long montantCentimes, String libelle, String beneficiaire,
			String ibanBeneficiaire, Long virementId, OffsetDateTime effectueLe) {
		Mouvement mouvement = virementSortant(compteId, montantCentimes, libelle, beneficiaire, ibanBeneficiaire,
				false, effectueLe);
		mouvement.type = RECREDIT_VIREMENT;
		mouvement.virementId = virementId;
		return mouvement;
	}

	/** CU-147 : débit d'un virement vers un bénéficiaire, relié à son virement. */
	public Mouvement duVirement(Long id) {
		this.virementId = id;
		return this;
	}

	public boolean isRecredit() {
		return RECREDIT_VIREMENT.equals(type);
	}

	public Long getVirementId() {
		return virementId;
	}

	/** Statut du virement relié (EN_ATTENTE_SIGNATURE, EXECUTE, REFUSE, ANNULE) ; vide sans virement. */
	public String getStatutVirement() {
		return statutVirement;
	}

	public String getMotifRefus() {
		return motifRefus;
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
