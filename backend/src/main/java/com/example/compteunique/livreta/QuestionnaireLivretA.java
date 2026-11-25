package com.example.compteunique.livreta;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-32 : réponses au questionnaire d'une ouverture de Livret A (table créée par
// db/migration/V30__questionnaire_livret_a.sql).
@Entity
@Table(name = "questionnaire_livret_a")
public class QuestionnaireLivretA {

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
	@Column(name = "objet_epargne", nullable = false, length = 30)
	private ObjetEpargne objetEpargne;

	@Enumerated(EnumType.STRING)
	@Column(name = "origine_fonds", nullable = false, length = 30)
	private OrigineFonds origineFonds;

	@Column(name = "precision_origine", length = 200)
	private String precisionOrigine;

	@Column(name = "version_texte_information", nullable = false, length = 10)
	private String versionTexteInformation;

	@Column(name = "repondu_le", nullable = false)
	private OffsetDateTime reponduLe;

	@Column(name = "demande_reference", length = 64)
	private String demandeReference;

	protected QuestionnaireLivretA() {
	}

	/** Questionnaire complet, texte d'information accepté dans sa version en vigueur. */
	public QuestionnaireLivretA(Long dossierId, UUID customerId, ObjetEpargne objetEpargne,
			OrigineFonds origineFonds, String precisionOrigine, String versionTexteInformation,
			OffsetDateTime maintenant) {
		this.reference = UUID.randomUUID().toString();
		this.dossierId = dossierId;
		this.customerId = customerId;
		this.objetEpargne = objetEpargne;
		this.origineFonds = origineFonds;
		this.precisionOrigine = precisionOrigine;
		this.versionTexteInformation = versionTexteInformation;
		this.reponduLe = maintenant;
	}

	/** Pas encore utilisé par une demande : l'éligibilité peut commencer. */
	boolean permetUneDemande() {
		return demandeReference == null;
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

	public ObjetEpargne getObjetEpargne() {
		return objetEpargne;
	}

	public OrigineFonds getOrigineFonds() {
		return origineFonds;
	}

	public String getPrecisionOrigine() {
		return precisionOrigine;
	}

	public String getVersionTexteInformation() {
		return versionTexteInformation;
	}

	public OffsetDateTime getReponduLe() {
		return reponduLe;
	}

	public String getDemandeReference() {
		return demandeReference;
	}

}
