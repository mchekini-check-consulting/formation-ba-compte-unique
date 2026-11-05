package com.example.compteunique.compte;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Ligne du journal de sécurité (CU-20), table créée par db/migration/V9__journal_securite.sql.
@Entity
@Table(name = "journal_securite")
public class EvenementSecurite {

	public static final String ACCES_COMPTE_REFUSE = "ACCES_COMPTE_REFUSE";
	static final int COMPTE_DEMANDE_MAX = 64;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40, updatable = false)
	private String evenement;

	@Column(name = "identifiant_connexion_id", nullable = false, updatable = false)
	private Long identifiantConnexionId;

	@Column(name = "customer_id", updatable = false)
	private UUID customerId;

	@Column(name = "compte_demande", nullable = false, length = COMPTE_DEMANDE_MAX, updatable = false)
	private String compteDemande;

	@Column(name = "survenu_le", nullable = false, updatable = false)
	private OffsetDateTime survenuLe;

	protected EvenementSecurite() {
	}

	public EvenementSecurite(Long identifiantConnexionId, UUID customerId, String compteDemande,
			OffsetDateTime survenuLe) {
		this.evenement = ACCES_COMPTE_REFUSE;
		this.identifiantConnexionId = identifiantConnexionId;
		this.customerId = customerId;
		this.compteDemande = compteDemande.length() > COMPTE_DEMANDE_MAX
				? compteDemande.substring(0, COMPTE_DEMANDE_MAX)
				: compteDemande;
		this.survenuLe = survenuLe;
	}

	public Long getId() {
		return id;
	}

	public String getEvenement() {
		return evenement;
	}

	public Long getIdentifiantConnexionId() {
		return identifiantConnexionId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getCompteDemande() {
		return compteDemande;
	}

	public OffsetDateTime getSurvenuLe() {
		return survenuLe;
	}

}
