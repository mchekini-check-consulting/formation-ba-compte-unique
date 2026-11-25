package com.example.compteunique.livreta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

	/** CU-296 : délai de dépôt du certificat de clôture, à partir de la réponse de l'administration fiscale. */
	static final int DELAI_CERTIFICAT_MOIS = 3;

	/** CU-296 : relance par e-mail 15 jours avant la date limite. */
	static final int JOURS_AVANT_RELANCE = 15;

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

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

	// CU-296 (V28) : suite d'un Livret A détenu ailleurs.
	@Column(name = "detail_etablissement", length = 100)
	private String detailEtablissement;

	@Column(name = "detail_agence", length = 100)
	private String detailAgence;

	@Column(name = "detail_date_ouverture")
	private LocalDate detailDateOuverture;

	@Enumerated(EnumType.STRING)
	@Column(name = "suite_choisie", length = 30)
	private SuiteLivretAExistant suiteChoisie;

	@Column(name = "suite_choisie_le")
	private OffsetDateTime suiteChoisieLe;

	@Column(name = "date_limite_certificat")
	private LocalDate dateLimiteCertificat;

	@Column(name = "relance_le")
	private OffsetDateTime relanceLe;

	@Column(name = "annulee_le")
	private OffsetDateTime annuleeLe;

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

	/** CU-296 : détail du livret existant, enregistré seulement si la cliente a autorisé son partage. */
	void recevoirDetail(DetailLivretA detail) {
		if (detail == null || !partageDetailAutorise || statut != StatutDemandeLivretA.LIVRET_A_EXISTANT) {
			return;
		}
		detailEtablissement = detail.etablissement();
		detailAgence = detail.agence();
		detailDateOuverture = detail.dateOuverture();
	}

	/** CU-296 : « Je clôture moi-même » ; date limite = date de la réponse fiscale (Paris) + 3 mois. */
	void choisirCloture(OffsetDateTime maintenant) {
		suiteChoisie = SuiteLivretAExistant.CLOTURE_PAR_CLIENTE;
		suiteChoisieLe = maintenant;
		statut = StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT;
		OffsetDateTime depart = reponseLe == null ? maintenant : reponseLe;
		dateLimiteCertificat = depart.atZoneSameInstant(PARIS).toLocalDate().plusMonths(DELAI_CERTIFICAT_MOIS);
	}

	/** CU-296 : « Je renonce à l'ouverture » : demande annulée. */
	void renoncer(OffsetDateTime maintenant) {
		suiteChoisie = SuiteLivretAExistant.RENONCIATION;
		suiteChoisieLe = maintenant;
		statut = StatutDemandeLivretA.RENONCEE;
		annuleeLe = maintenant;
	}

	void certificatDepose() {
		statut = StatutDemandeLivretA.CERTIFICAT_A_VERIFIER;
	}

	/** CU-296 : certificat validé par un conseiller, l'ouverture reprend. */
	void certificatValide() {
		statut = StatutDemandeLivretA.OUVERTURE_VALIDEE;
	}

	/** CU-296 : certificat refusé, un nouveau dépôt est possible jusqu'à la date limite. */
	void certificatRefuse() {
		statut = StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT;
	}

	/** CU-296 : certificat attendu ou à vérifier, et date limite dépassée. */
	boolean dateLimiteDepassee(LocalDate aujourdhui) {
		return (statut == StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT
				|| statut == StatutDemandeLivretA.CERTIFICAT_A_VERIFIER)
				&& dateLimiteCertificat != null && aujourdhui.isAfter(dateLimiteCertificat);
	}

	void annuler(OffsetDateTime maintenant) {
		statut = StatutDemandeLivretA.ANNULEE;
		annuleeLe = maintenant;
	}

	/** CU-296 : certificat toujours attendu, 15 jours ou moins avant la date limite, pas encore relancée. */
	boolean aRelancer(LocalDate aujourdhui) {
		return statut == StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT && relanceLe == null
				&& dateLimiteCertificat != null
				&& !aujourdhui.isBefore(dateLimiteCertificat.minusDays(JOURS_AVANT_RELANCE))
				&& !aujourdhui.isAfter(dateLimiteCertificat);
	}

	void relancer(OffsetDateTime maintenant) {
		relanceLe = maintenant;
	}

	/** Détail du livret existant, null s'il n'a pas été communiqué. */
	public DetailLivretA getDetail() {
		if (detailEtablissement == null && detailAgence == null && detailDateOuverture == null) {
			return null;
		}
		return new DetailLivretA(detailEtablissement, detailAgence, detailDateOuverture);
	}

	public SuiteLivretAExistant getSuiteChoisie() {
		return suiteChoisie;
	}

	public OffsetDateTime getSuiteChoisieLe() {
		return suiteChoisieLe;
	}

	public LocalDate getDateLimiteCertificat() {
		return dateLimiteCertificat;
	}

	public OffsetDateTime getRelanceLe() {
		return relanceLe;
	}

	public OffsetDateTime getAnnuleeLe() {
		return annuleeLe;
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
