package com.example.compteunique.livreta;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-28 : contrôle d'identité d'une ouverture de Livret A (table créée par
// db/migration/V23__controle_identite_livret_a.sql).
@Entity
@Table(name = "controle_identite_livret_a")
public class ControleIdentiteLivretA {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64, unique = true)
	private String reference;

	@Column(name = "dossier_id", nullable = false)
	private Long dossierId;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatutControleIdentite statut;

	@Column(name = "residence_fiscale_confirmee_le", nullable = false)
	private OffsetDateTime residenceFiscaleConfirmeeLe;

	@Column(name = "coordonnees_modifiees_le")
	private OffsetDateTime coordonneesModifieesLe;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private ResultatSanctions resultat;

	@Column(nullable = false)
	private int tentatives;

	@Column(name = "resultat_le")
	private OffsetDateTime resultatLe;

	@Column(name = "alerte_conformite_le")
	private OffsetDateTime alerteConformiteLe;

	@Column(name = "conseiller_alerte_le")
	private OffsetDateTime conseillerAlerteLe;

	@Column(name = "responsable_valide_le")
	private OffsetDateTime responsableValideLe;

	@Column(name = "demande_reference", length = 64)
	private String demandeReference;

	@Column(name = "cree_le", nullable = false)
	private OffsetDateTime creeLe;

	protected ControleIdentiteLivretA() {
	}

	/** Identité confirmée (résidence fiscale comprise) : le contrôle sanctions/PPE est envoyé. */
	public ControleIdentiteLivretA(Long dossierId, UUID customerId, boolean coordonneesModifiees,
			OffsetDateTime maintenant) {
		this.reference = UUID.randomUUID().toString();
		this.dossierId = dossierId;
		this.customerId = customerId;
		this.statut = StatutControleIdentite.EN_COURS;
		this.residenceFiscaleConfirmeeLe = maintenant;
		this.coordonneesModifieesLe = coordonneesModifiees ? maintenant : null;
		this.creeLe = maintenant;
	}

	/**
	 * Résultat reçu (ou absence de réponse après les relances). Gel des avoirs : bloqué et conformité alertée ;
	 * PPE : validation d'un responsable attendue ; sans réponse : en attente et conseiller alerté.
	 */
	void recevoirResultat(ResultatSanctions recu, int essais, OffsetDateTime maintenant) {
		resultat = recu;
		tentatives = essais;
		resultatLe = maintenant;
		statut = switch (recu) {
			case AUCUNE_CORRESPONDANCE -> StatutControleIdentite.VALIDE;
			case CORRESPONDANCE_SANCTIONS -> StatutControleIdentite.BLOQUE;
			case CORRESPONDANCE_PEP -> StatutControleIdentite.ATTENTE_RESPONSABLE;
			case SANS_REPONSE -> StatutControleIdentite.EN_ATTENTE;
		};
		if (statut == StatutControleIdentite.BLOQUE) {
			alerteConformiteLe = maintenant;
		}
		if (statut == StatutControleIdentite.EN_ATTENTE) {
			conseillerAlerteLe = maintenant;
		}
	}

	/** PPE : la démarche continue après la validation d'un responsable. */
	void validerParResponsable(OffsetDateTime maintenant) {
		responsableValideLe = maintenant;
		statut = StatutControleIdentite.VALIDE;
	}

	/** Contrôle validé et pas encore utilisé par une demande : l'éligibilité peut commencer. */
	boolean permetUneDemande() {
		return statut == StatutControleIdentite.VALIDE && demandeReference == null;
	}

	void utiliserPour(String referenceDemande) {
		demandeReference = referenceDemande;
	}

	public String getReference() {
		return reference;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public StatutControleIdentite getStatut() {
		return statut;
	}

	public OffsetDateTime getResidenceFiscaleConfirmeeLe() {
		return residenceFiscaleConfirmeeLe;
	}

	public OffsetDateTime getCoordonneesModifieesLe() {
		return coordonneesModifieesLe;
	}

	public ResultatSanctions getResultat() {
		return resultat;
	}

	public int getTentatives() {
		return tentatives;
	}

	public OffsetDateTime getResultatLe() {
		return resultatLe;
	}

	public OffsetDateTime getAlerteConformiteLe() {
		return alerteConformiteLe;
	}

	public OffsetDateTime getConseillerAlerteLe() {
		return conseillerAlerteLe;
	}

	public OffsetDateTime getResponsableValideLe() {
		return responsableValideLe;
	}

	public String getDemandeReference() {
		return demandeReference;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

}
