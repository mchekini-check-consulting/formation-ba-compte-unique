package com.example.compteunique.dossier;

import java.time.Duration;
import java.time.LocalDate;
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

import com.example.compteunique.dossier.Categories.MotifAnnulation;
import com.example.compteunique.dossier.Categories.Nationalite;
import com.example.compteunique.dossier.Categories.PaysNaissance;
import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.Categories.TypePiece;

// Dossier d'ouverture de compte (CUB-8), table créée par db/migration/V3__dossier.sql.
@Entity
@Table(name = "dossier")
@SuppressWarnings("PMD.TooManyFields") // une colonne par champ de l'écran « Mon identité »
public class Dossier {

	/** CU-13 : premier essai de création + 3 relances automatiques. */
	public static final int CREATION_ESSAIS_MAX = 4;
	public static final Duration DELAI_RELANCE = Duration.ofMinutes(1);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64, updatable = false)
	private String reference;

	@Column(nullable = false, length = 5)
	private String civilite;

	@Column(name = "nom_naissance", nullable = false, length = 100)
	private String nomNaissance;

	@Column(name = "nom_usage", length = 100)
	private String nomUsage;

	@Column(nullable = false, length = 200)
	private String prenoms;

	@Column(name = "date_naissance", nullable = false)
	private LocalDate dateNaissance;

	@Column(name = "commune_naissance", nullable = false, length = 100)
	private String communeNaissance;

	@Enumerated(EnumType.STRING)
	@Column(name = "pays_naissance", nullable = false, length = 20)
	private PaysNaissance paysNaissance;

	@Column(name = "indice_americain", nullable = false)
	private boolean indiceAmericain;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Nationalite nationalite;

	@Column(nullable = false, length = 200)
	private String voie;

	@Column(name = "complement_adresse", length = 200)
	private String complement;

	@Column(name = "code_postal", nullable = false, length = 10)
	private String codePostal;

	@Column(nullable = false, length = 100)
	private String ville;

	@Column(name = "pays_adresse", nullable = false, length = 60)
	private String paysAdresse;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(nullable = false, length = 20)
	private String mobile;

	@Column(length = 20)
	private String fixe;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_piece", nullable = false, length = 30)
	private TypePiece typePiece;

	@Column(name = "numero_piece", nullable = false, length = 30)
	private String numeroPiece;

	@Column(name = "date_expiration_piece", nullable = false)
	private LocalDate dateExpiration;

	@Column(name = "residence_francaise", nullable = false)
	private boolean residenceFrancaise;

	@Column(name = "saisi_le", nullable = false)
	private OffsetDateTime saisiLe;

	@Enumerated(EnumType.STRING)
	@Column(name = "controle_sanctions", length = 3)
	private ResultatControle controleSanctions;

	@Enumerated(EnumType.STRING)
	@Column(name = "controle_pep", length = 3)
	private ResultatControle controlePep;

	@Column(name = "controle_le")
	private OffsetDateTime controleLe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatutDossier statut;

	/** CU-13 : profil technique, attribué à la signature, jamais montré à la cliente. */
	@Column(name = "customer_id", unique = true)
	private UUID customerId;

	@Column(name = "creation_tentatives", nullable = false)
	private int creationTentatives;

	@Column(name = "creation_prochaine_le")
	private OffsetDateTime creationProchaineLe;

	/** CU-9 : motif d'un dossier « Annulé » ; une annulation par contrôle bloque les nouvelles ouvertures. */
	@Enumerated(EnumType.STRING)
	@Column(name = "motif_annulation", length = 30)
	private MotifAnnulation motifAnnulation;

	@Column(name = "annule_le")
	private OffsetDateTime annuleLe;

	/** CU-9 : dernière action de la cliente ; 30 jours sans action annulent le dossier. */
	@Column(name = "derniere_action_le", nullable = false)
	private OffsetDateTime derniereActionLe;

	/** CU-9 critère 7 : en attente de l'analyste (PPE), jamais annulé pour inactivité. */
	@Column(name = "attente_analyste", nullable = false)
	private boolean attenteAnalyste;

	protected Dossier() {
	}

	public Dossier(String reference) {
		this.reference = reference;
		this.statut = StatutDossier.EN_COURS_DE_VERIFICATION;
		this.derniereActionLe = OffsetDateTime.now();
	}

	/** CU-9 : une action de la cliente sur son dossier (saisie, contrôle, signature). */
	public void actionCliente(OffsetDateTime maintenant) {
		derniereActionLe = maintenant;
	}

	/**
	 * Enregistre (ou remplace, si la cliente revient à l'étape) l'identité saisie. Les champs
	 * reçus sont déjà contrôlés et nettoyés par DossierService. Une nouvelle saisie efface le
	 * résultat des contrôles précédents : ils seront relancés sur la nouvelle identité.
	 */
	void renseignerIdentite(IdentiteRequete identite, OffsetDateTime maintenant) {
		civilite = identite.civilite();
		nomNaissance = identite.nomNaissance();
		nomUsage = identite.nomUsage();
		prenoms = identite.prenoms();
		dateNaissance = identite.dateNaissance();
		communeNaissance = identite.communeNaissance();
		paysNaissance = identite.paysNaissance();
		indiceAmericain = paysNaissance == PaysNaissance.ETATS_UNIS;
		nationalite = identite.nationalite();
		voie = identite.voie();
		complement = identite.complement();
		codePostal = identite.codePostal();
		ville = identite.ville();
		paysAdresse = identite.paysAdresse();
		email = identite.email();
		mobile = identite.mobile();
		fixe = identite.fixe();
		typePiece = identite.typePiece();
		numeroPiece = identite.numeroPiece();
		dateExpiration = identite.dateExpiration();
		// CUB-8 : une carte nationale d'identité ou un titre de séjour rend la cliente résidente française.
		residenceFrancaise = true;
		saisiLe = maintenant;
		controleSanctions = null;
		controlePep = null;
		controleLe = null;
		statut = StatutDossier.EN_COURS_DE_VERIFICATION;
		derniereActionLe = maintenant;
	}

	/** CUB-8 : tout contrôle en échec (sanctions ou PEP) annule le dossier (CU-9 : annulé par contrôle). */
	void enregistrerControles(ResultatControle sanctions, ResultatControle pep, OffsetDateTime maintenant) {
		controleSanctions = sanctions;
		controlePep = pep;
		controleLe = maintenant;
		derniereActionLe = maintenant;
		if (sanctions != ResultatControle.OK || pep != ResultatControle.OK) {
			annuler(MotifAnnulation.CONTROLE_AUTOMATIQUE, maintenant);
		}
	}

	/** CU-12 : convention prête à signer = identité enregistrée et contrôles sanctions et PEP OK. */
	public boolean estPretASigner() {
		return statut == StatutDossier.EN_COURS_DE_VERIFICATION && controleSanctions == ResultatControle.OK
				&& controlePep == ResultatControle.OK;
	}

	/** CU-12 : la signature fait passer le dossier à « Convention signée » ; le customer_id est attribué. */
	public void conventionSignee(OffsetDateTime maintenant) {
		statut = StatutDossier.CONVENTION_SIGNEE;
		if (customerId == null) {
			customerId = UUID.randomUUID();
		}
		creationTentatives = 0;
		// La création est lancée tout de suite après la signature ; filet de sécurité si elle
		// n'a pas pu l'être (arrêt du service) : les relances automatiques la reprendront.
		creationProchaineLe = maintenant.plus(DELAI_RELANCE);
	}

	public void annuler(MotifAnnulation motif, OffsetDateTime maintenant) {
		statut = StatutDossier.ANNULE;
		motifAnnulation = motif;
		annuleLe = maintenant;
		creationProchaineLe = null;
	}

	public MotifAnnulation getMotifAnnulation() {
		return motifAnnulation;
	}

	public OffsetDateTime getAnnuleLe() {
		return annuleLe;
	}

	public OffsetDateTime getDerniereActionLe() {
		return derniereActionLe;
	}

	public boolean isAttenteAnalyste() {
		return attenteAnalyste;
	}

	/** CU-13 : compte et identifiant de connexion créés. */
	public void compteOuvert() {
		statut = StatutDossier.COMPTE_OUVERT;
		creationProchaineLe = null;
	}

	/**
	 * CU-13 : création en échec, relancée jusqu'à 3 fois à 1 minute d'intervalle, puis « Création à
	 * relancer ». definitif : échec qui ne se corrigera pas en relançant (identifiants épuisés).
	 */
	public void creationEchouee(OffsetDateTime maintenant, boolean definitif) {
		if (statut != StatutDossier.CONVENTION_SIGNEE) {
			return;
		}
		creationTentatives++;
		if (definitif || creationTentatives >= CREATION_ESSAIS_MAX) {
			statut = StatutDossier.CREATION_A_RELANCER;
			creationProchaineLe = null;
		} else {
			creationProchaineLe = maintenant.plus(DELAI_RELANCE);
		}
	}

	/** CU-13 : relance manuelle d'un dossier « Création à relancer », avec le même customer_id. */
	public void relancerCreation(OffsetDateTime maintenant) {
		statut = StatutDossier.CONVENTION_SIGNEE;
		creationTentatives = 0;
		creationProchaineLe = maintenant.plus(DELAI_RELANCE);
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public int getCreationTentatives() {
		return creationTentatives;
	}

	public OffsetDateTime getCreationProchaineLe() {
		return creationProchaineLe;
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public String getCivilite() {
		return civilite;
	}

	public String getNomNaissance() {
		return nomNaissance;
	}

	public String getNomUsage() {
		return nomUsage;
	}

	public String getPrenoms() {
		return prenoms;
	}

	public LocalDate getDateNaissance() {
		return dateNaissance;
	}

	public String getCommuneNaissance() {
		return communeNaissance;
	}

	public PaysNaissance getPaysNaissance() {
		return paysNaissance;
	}

	public boolean isIndiceAmericain() {
		return indiceAmericain;
	}

	public Nationalite getNationalite() {
		return nationalite;
	}

	public String getVoie() {
		return voie;
	}

	public String getComplement() {
		return complement;
	}

	public String getCodePostal() {
		return codePostal;
	}

	public String getVille() {
		return ville;
	}

	public String getPaysAdresse() {
		return paysAdresse;
	}

	public String getEmail() {
		return email;
	}

	public String getMobile() {
		return mobile;
	}

	public String getFixe() {
		return fixe;
	}

	public TypePiece getTypePiece() {
		return typePiece;
	}

	public String getNumeroPiece() {
		return numeroPiece;
	}

	public LocalDate getDateExpiration() {
		return dateExpiration;
	}

	public boolean isResidenceFrancaise() {
		return residenceFrancaise;
	}

	public OffsetDateTime getSaisiLe() {
		return saisiLe;
	}

	public ResultatControle getControleSanctions() {
		return controleSanctions;
	}

	public ResultatControle getControlePep() {
		return controlePep;
	}

	public OffsetDateTime getControleLe() {
		return controleLe;
	}

	public StatutDossier getStatut() {
		return statut;
	}

}
