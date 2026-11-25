package com.example.compteunique.livreta;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-296 : certificat de clôture d'un Livret A détenu ailleurs (table créée par
// db/migration/V28__livret_a_detenu_ailleurs.sql). Un par dépôt ; decision vide = à vérifier.
@Entity
@Table(name = "certificat_cloture_livret_a")
public class CertificatClotureLivretA {

	/** Décision du conseiller. */
	public enum Decision {
		VALIDE, REFUSE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "demande_id", nullable = false)
	private Long demandeId;

	@Column(nullable = false)
	private byte[] fichier;

	@Column(name = "nom_fichier", nullable = false, length = 255)
	private String nomFichier;

	@Column(name = "type_fichier", nullable = false, length = 50)
	private String typeFichier;

	@Column(nullable = false)
	private int taille;

	@Column(name = "depose_le", nullable = false)
	private OffsetDateTime deposeLe;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Decision decision;

	@Column(name = "verifie_par", length = 100)
	private String verifiePar;

	@Column(name = "verifie_le")
	private OffsetDateTime verifieLe;

	@Column(name = "motif_refus", length = 500)
	private String motifRefus;

	protected CertificatClotureLivretA() {
	}

	public CertificatClotureLivretA(Long demandeId, byte[] fichier, String nomFichier, String typeFichier,
			OffsetDateTime deposeLe) {
		this.demandeId = demandeId;
		this.fichier = fichier.clone();
		this.nomFichier = nomFichier;
		this.typeFichier = typeFichier;
		this.taille = fichier.length;
		this.deposeLe = deposeLe;
	}

	boolean estAVerifier() {
		return decision == null;
	}

	void valider(String conseiller, OffsetDateTime maintenant) {
		decision = Decision.VALIDE;
		verifiePar = conseiller;
		verifieLe = maintenant;
	}

	void refuser(String conseiller, String motif, OffsetDateTime maintenant) {
		decision = Decision.REFUSE;
		verifiePar = conseiller;
		verifieLe = maintenant;
		motifRefus = motif;
	}

	public Long getId() {
		return id;
	}

	public Long getDemandeId() {
		return demandeId;
	}

	public byte[] getFichier() {
		return fichier.clone();
	}

	public String getNomFichier() {
		return nomFichier;
	}

	public String getTypeFichier() {
		return typeFichier;
	}

	public int getTaille() {
		return taille;
	}

	public OffsetDateTime getDeposeLe() {
		return deposeLe;
	}

	public Decision getDecision() {
		return decision;
	}

	public String getVerifiePar() {
		return verifiePar;
	}

	public OffsetDateTime getVerifieLe() {
		return verifieLe;
	}

	public String getMotifRefus() {
		return motifRefus;
	}

}
