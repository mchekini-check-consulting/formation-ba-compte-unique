package com.example.compteunique.souscription;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.catalogue.CatalogueService.Produit;

// CU-312 (PER-04) : souscription d'une cliente (premier versement, signature par code SMS, contrat créé).
@Entity
@Table(schema = "souscription", name = "souscription")
public class Souscription {

	static final String EN_COURS = "EN_COURS";
	static final String SIGNEE = "SIGNEE";
	static final short COMPARTIMENT_VOLONTAIRE = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID customerId;

	@Enumerated(EnumType.STRING)
	private Produit produit;

	private String statut;

	private BigDecimal montant;

	private String origineFonds;

	private String originePrecision;

	/** PER seulement (vide pour l'assurance vie). */
	private Short compartiment;

	/** Assurance vie : âge de la titulaire à la signature, donc au moment du versement initial. */
	private Short ageTitulaire;

	private String codeHache;

	private OffsetDateTime codeEnvoyeLe;

	private int codesFaux;

	private OffsetDateTime bloqueeJusquAu;

	private String numeroContrat;

	private OffsetDateTime creeLe;

	private OffsetDateTime signeeLe;

	protected Souscription() {
	}

	Souscription(UUID customerId, Produit produit, OffsetDateTime le) {
		this.customerId = customerId;
		this.produit = produit;
		this.statut = EN_COURS;
		this.compartiment = produit == Produit.PER ? COMPARTIMENT_VOLONTAIRE : null;
		this.creeLe = le;
	}

	void ageTitulaire(int age) {
		this.ageTitulaire = (short) age;
	}

	public Long getId() {
		return id;
	}

	public Short getAgeTitulaire() {
		return ageTitulaire;
	}

	/** Premier versement saisi ou modifié : un code déjà envoyé ne vaut plus pour ce nouveau montant. */
	void versement(BigDecimal montant, String origineFonds, String originePrecision) {
		this.montant = montant;
		this.origineFonds = origineFonds;
		this.originePrecision = originePrecision;
		this.codeHache = null;
		this.codeEnvoyeLe = null;
	}

	/** Vrai pendant le blocage de 30 minutes qui suit le 3e code erroné. */
	boolean estBloquee(OffsetDateTime maintenant) {
		return bloqueeJusquAu != null && maintenant.isBefore(bloqueeJusquAu);
	}

	/** Nouveau code envoyé ; un blocage terminé remet le compteur de codes erronés à zéro. */
	void codeEnvoye(String codeHache, OffsetDateTime le) {
		if (bloqueeJusquAu != null) {
			bloqueeJusquAu = null;
			codesFaux = 0;
		}
		this.codeHache = codeHache;
		this.codeEnvoyeLe = le;
	}

	void codeExpire() {
		codeHache = null;
		codeEnvoyeLe = null;
	}

	void codeFaux() {
		codesFaux++;
	}

	void bloquer(OffsetDateTime maintenant, Duration duree) {
		codesFaux++;
		codeExpire();
		bloqueeJusquAu = maintenant.plus(duree);
	}

	void signer(String numeroContrat, OffsetDateTime le) {
		this.statut = SIGNEE;
		this.numeroContrat = numeroContrat;
		this.signeeLe = le;
		codeExpire();
	}

	boolean estSignee() {
		return SIGNEE.equals(statut);
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public Produit getProduit() {
		return produit;
	}

	public String getStatut() {
		return statut;
	}

	public BigDecimal getMontant() {
		return montant;
	}

	public String getOrigineFonds() {
		return origineFonds;
	}

	public String getOriginePrecision() {
		return originePrecision;
	}

	public Short getCompartiment() {
		return compartiment;
	}

	public String getCodeHache() {
		return codeHache;
	}

	public OffsetDateTime getCodeEnvoyeLe() {
		return codeEnvoyeLe;
	}

	public int getCodesFaux() {
		return codesFaux;
	}

	public OffsetDateTime getBloqueeJusquAu() {
		return bloqueeJusquAu;
	}

	public String getNumeroContrat() {
		return numeroContrat;
	}

	public OffsetDateTime getSigneeLe() {
		return signeeLe;
	}

}
