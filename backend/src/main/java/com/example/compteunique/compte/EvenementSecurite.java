package com.example.compteunique.compte;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Ligne du journal de sécurité, table créée par db/migration/V9__journal_securite.sql et complétée
// par V12 : accès refusé à un compte (CU-20), doublon à l'ouverture (CU-9), authentification (CU-48).
// Jamais de mot de passe, de jeton, de numéro de pièce ni de montant.
@Entity
@Table(name = "journal_securite")
@SuppressWarnings("PMD.TooManyFields") // une colonne par information exigée par CU-48
public class EvenementSecurite {

	public static final String ACCES_COMPTE_REFUSE = "ACCES_COMPTE_REFUSE";
	/** CU-9 : ouverture bloquée, la personne a déjà un profil client. */
	public static final String DOUBLON_DETECTE = "DOUBLON_DETECTE";
	/** CU-9 : nouvelle ouverture renvoyée vers le dossier en cours de la même personne. */
	public static final String REPRISE_DOSSIER = "REPRISE_DOSSIER";
	/** CU-9 : dossier annulé après 30 jours sans action de la cliente. */
	public static final String ANNULATION_INACTIVITE = "ANNULATION_INACTIVITE";

	// CU-48 : événements d'authentification.
	public static final String CONNEXION_REUSSIE = "CONNEXION_REUSSIE";
	public static final String CONNEXION_ECHOUEE = "CONNEXION_ECHOUEE";
	public static final String BLOCAGE_IDENTIFIANT = "BLOCAGE_IDENTIFIANT";
	public static final String BLOCAGE_ADRESSE_IP = "BLOCAGE_ADRESSE_IP";
	public static final String ACTIVATION = "ACTIVATION";
	public static final String DEMANDE_REINITIALISATION = "DEMANDE_REINITIALISATION";
	public static final String REINITIALISATION = "REINITIALISATION";
	public static final String DECONNEXION = "DECONNEXION";
	public static final String EXPIRATION_SESSION = "EXPIRATION_SESSION";

	public static final String REUSSITE = "REUSSITE";
	public static final String ECHEC = "ECHEC";

	static final int COMPTE_DEMANDE_MAX = 64;
	static final int IDENTIFIANT_SAISI_MAX = 32;
	static final int ADRESSE_IP_MAX = 45;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40, updatable = false)
	private String evenement;

	@Column(name = "identifiant_connexion_id", updatable = false)
	private Long identifiantConnexionId;

	@Column(name = "customer_id", updatable = false)
	private UUID customerId;

	@Column(name = "compte_demande", length = COMPTE_DEMANDE_MAX, updatable = false)
	private String compteDemande;

	@Column(name = "identifiant_saisi", length = IDENTIFIANT_SAISI_MAX, updatable = false)
	private String identifiantSaisi;

	@Column(name = "adresse_ip", length = ADRESSE_IP_MAX, updatable = false)
	private String adresseIp;

	@Column(length = 10, updatable = false)
	private String resultat;

	@Column(length = 40, updatable = false)
	private String motif;

	@Column(name = "survenu_le", nullable = false, updatable = false)
	private OffsetDateTime survenuLe;

	protected EvenementSecurite() {
	}

	/** CU-20 : accès refusé à un compte. */
	public EvenementSecurite(Long identifiantConnexionId, UUID customerId, String compteDemande,
			OffsetDateTime survenuLe) {
		this(ACCES_COMPTE_REFUSE, survenuLe);
		this.identifiantConnexionId = identifiantConnexionId;
		this.customerId = customerId;
		this.compteDemande = tronque(compteDemande, COMPTE_DEMANDE_MAX);
		this.resultat = ECHEC;
	}

	private EvenementSecurite(String evenement, OffsetDateTime survenuLe) {
		this.evenement = evenement;
		this.survenuLe = survenuLe;
	}

	/**
	 * CU-9 : événement sur un dossier d'ouverture (blocage, reprise, annulation pour inactivité).
	 * Seul le motif est gardé, jamais la pièce ni l'identité. Un blocage ou une reprise refusent la
	 * nouvelle ouverture (résultat ECHEC).
	 */
	public static EvenementSecurite dossier(String type, String motif, OffsetDateTime survenuLe) {
		EvenementSecurite evenement = new EvenementSecurite(type, survenuLe);
		evenement.resultat = ANNULATION_INACTIVITE.equals(type) ? null : ECHEC;
		evenement.motif = motif;
		return evenement;
	}

	/**
	 * CU-48 : événement d'authentification. identifiantSaisi : tel que saisi (identifiant inconnu
	 * compris), identifiantConnexionId : le client quand il est connu. motif : vide en cas de réussite.
	 */
	public static EvenementSecurite authentification(String type, String identifiantSaisi,
			Long identifiantConnexionId, String motif, OffsetDateTime survenuLe) {
		EvenementSecurite evenement = new EvenementSecurite(type, survenuLe);
		evenement.identifiantSaisi = tronque(identifiantSaisi, IDENTIFIANT_SAISI_MAX);
		evenement.identifiantConnexionId = identifiantConnexionId;
		evenement.resultat = motif == null ? REUSSITE : ECHEC;
		evenement.motif = motif;
		return evenement;
	}

	/** Adresse IP de la demande (renseignée par le journal, CU-48). */
	public void depuis(String adresse) {
		this.adresseIp = tronque(adresse, ADRESSE_IP_MAX);
	}

	private static String tronque(String valeur, int max) {
		return valeur == null || valeur.length() <= max ? valeur : valeur.substring(0, max);
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

	public String getIdentifiantSaisi() {
		return identifiantSaisi;
	}

	public String getAdresseIp() {
		return adresseIp;
	}

	public String getResultat() {
		return resultat;
	}

	public String getMotif() {
		return motif;
	}

	public OffsetDateTime getSurvenuLe() {
		return survenuLe;
	}

}
