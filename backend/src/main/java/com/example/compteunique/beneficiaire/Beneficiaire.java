package com.example.compteunique.beneficiaire;

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

// CU-146 (US-22) : bénéficiaire de virement d'une cliente, table créée par db/migration/V20__beneficiaire.sql.
// Nom vérifié et IBAN jamais modifiés ; seul le libellé change. Suppression = statut SUPPRIME, ligne conservée.
@Entity
@Table(name = "beneficiaire")
public class Beneficiaire {

	public static final String ACTIF = "ACTIF";
	public static final String SUPPRIME = "SUPPRIME";
	public static final int NOM_MAX = 70;
	public static final int LIBELLE_MAX = 70;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private UUID customerId;

	@Column(nullable = false, length = 34, updatable = false)
	private String iban;

	@Column(name = "nom_verifie", nullable = false, length = NOM_MAX, updatable = false)
	private String nomVerifie;

	@Enumerated(EnumType.STRING)
	@Column(name = "resultat_verification", nullable = false, length = 20, updatable = false)
	private ResultatVerification resultatVerification;

	@Column(nullable = false, length = LIBELLE_MAX)
	private String libelle;

	@Column(name = "ajoute_le", nullable = false, updatable = false)
	private OffsetDateTime ajouteLe;

	@Column(nullable = false, length = 20)
	private String statut;

	@Column(name = "supprime_le")
	private OffsetDateTime supprimeLe;

	protected Beneficiaire() {
	}

	/** Bénéficiaire actif, créé quand le code SMS de l'ajout est confirmé. */
	Beneficiaire(AjoutBeneficiaire ajout, OffsetDateTime ajouteLe) {
		this.customerId = ajout.getCustomerId();
		this.iban = ajout.getIban();
		this.nomVerifie = ajout.getNomVerifie();
		this.resultatVerification = ajout.getResultatVerification();
		this.libelle = ajout.getLibelle();
		this.ajouteLe = ajouteLe;
		this.statut = ACTIF;
	}

	void renommer(String nouveauLibelle) {
		this.libelle = nouveauLibelle;
	}

	void supprimer(OffsetDateTime le) {
		this.statut = SUPPRIME;
		this.supprimeLe = le;
	}

	public boolean isActif() {
		return ACTIF.equals(statut);
	}

	public Long getId() {
		return id;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getIban() {
		return iban;
	}

	public String getNomVerifie() {
		return nomVerifie;
	}

	public ResultatVerification getResultatVerification() {
		return resultatVerification;
	}

	public String getLibelle() {
		return libelle;
	}

	public OffsetDateTime getAjouteLe() {
		return ajouteLe;
	}

	public String getStatut() {
		return statut;
	}

	public OffsetDateTime getSupprimeLe() {
		return supprimeLe;
	}

}
