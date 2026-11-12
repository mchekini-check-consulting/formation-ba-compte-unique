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

// CU-31 : demande d'ouverture d'un Livret A (table créée par db/migration/V22__demande_livret_a.sql).
@Entity
@Table(name = "demande_livret_a")
public class DemandeLivretA {

	/** Délai de réponse de l'administration fiscale. */
	static final int DELAI_JOURS_OUVRES = 2;

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
	private StatutDemandeLivretA statut;

	@Column(name = "attestation_le", nullable = false)
	private OffsetDateTime attestationLe;

	@Column(name = "partage_detail_autorise", nullable = false)
	private boolean partageDetailAutorise;

	@Column(name = "choix_partage_le", nullable = false)
	private OffsetDateTime choixPartageLe;

	@Column(name = "interrogation_id", length = 64)
	private String interrogationId;

	@Column(name = "interrogation_le")
	private OffsetDateTime interrogationLe;

	@Enumerated(EnumType.STRING)
	@Column(name = "reponse_fiscale", length = 20)
	private ReponseFiscale reponseFiscale;

	@Column(name = "reponse_le")
	private OffsetDateTime reponseLe;

	@Column(name = "conseiller_alerte_le")
	private OffsetDateTime conseillerAlerteLe;

	@Column(name = "creee_le", nullable = false)
	private OffsetDateTime creeeLe;

	protected DemandeLivretA() {
	}

	/** Attestation validée et choix de partage enregistrés avec la date et l'heure. */
	public DemandeLivretA(Long dossierId, UUID customerId, boolean partageDetailAutorise, OffsetDateTime maintenant) {
		this.reference = UUID.randomUUID().toString();
		this.dossierId = dossierId;
		this.customerId = customerId;
		this.statut = StatutDemandeLivretA.EN_COURS_DE_VERIFICATION;
		this.attestationLe = maintenant;
		this.partageDetailAutorise = partageDetailAutorise;
		this.choixPartageLe = maintenant;
		this.creeeLe = maintenant;
	}

	/**
	 * Réponse de l'administration fiscale. Le délai de 2 jours ouvrés part de la première transmission.
	 * « Aucun autre Livret A » : ouverture validée ; « Livret A existant » : non ouvert (CU-296) ; sans réponse ou
	 * indisponible : la demande reste en cours de vérification.
	 */
	void recevoirReponse(String interrogation, ReponseFiscale reponse, OffsetDateTime maintenant) {
		if (interrogationLe == null) {
			interrogationId = interrogation;
			interrogationLe = maintenant;
		}
		reponseFiscale = reponse;
		reponseLe = maintenant;
		statut = switch (reponse) {
			case AUCUN_LIVRET_A -> StatutDemandeLivretA.OUVERTURE_VALIDEE;
			case LIVRET_A_EXISTANT -> StatutDemandeLivretA.LIVRET_A_EXISTANT;
			case SANS_REPONSE, INDISPONIBLE -> StatutDemandeLivretA.EN_COURS_DE_VERIFICATION;
		};
	}

	boolean estEnCours() {
		return statut == StatutDemandeLivretA.EN_COURS_DE_VERIFICATION;
	}

	/** Transmise, toujours sans réponse définitive, 2 jours ouvrés passés et conseiller pas encore alerté. */
	boolean aAlerter(OffsetDateTime maintenant) {
		return estEnCours() && interrogationLe != null && conseillerAlerteLe == null
				&& !maintenant.isBefore(JoursOuvres.ajouter(interrogationLe, DELAI_JOURS_OUVRES));
	}

	void alerterConseiller(OffsetDateTime maintenant) {
		conseillerAlerteLe = maintenant;
	}

	public Long getId() {
		return id;
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

	public StatutDemandeLivretA getStatut() {
		return statut;
	}

	public OffsetDateTime getAttestationLe() {
		return attestationLe;
	}

	public boolean isPartageDetailAutorise() {
		return partageDetailAutorise;
	}

	public OffsetDateTime getChoixPartageLe() {
		return choixPartageLe;
	}

	public String getInterrogationId() {
		return interrogationId;
	}

	public OffsetDateTime getInterrogationLe() {
		return interrogationLe;
	}

	public ReponseFiscale getReponseFiscale() {
		return reponseFiscale;
	}

	public OffsetDateTime getReponseLe() {
		return reponseLe;
	}

	public OffsetDateTime getConseillerAlerteLe() {
		return conseillerAlerteLe;
	}

	public OffsetDateTime getCreeeLe() {
		return creeeLe;
	}

}
