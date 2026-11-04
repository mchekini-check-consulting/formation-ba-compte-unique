package com.example.compteunique.dossier;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

	protected Dossier() {
	}

	public Dossier(String reference) {
		this.reference = reference;
		this.statut = StatutDossier.EN_COURS_DE_VERIFICATION;
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
	}

	/** CUB-8 : tout contrôle en échec (sanctions ou PEP) annule le dossier. */
	void enregistrerControles(ResultatControle sanctions, ResultatControle pep, OffsetDateTime maintenant) {
		controleSanctions = sanctions;
		controlePep = pep;
		controleLe = maintenant;
		statut = sanctions == ResultatControle.OK && pep == ResultatControle.OK
				? StatutDossier.EN_COURS_DE_VERIFICATION : StatutDossier.ANNULE;
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
