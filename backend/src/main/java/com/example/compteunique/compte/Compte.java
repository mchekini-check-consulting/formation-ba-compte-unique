package com.example.compteunique.compte;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Compte courant (CU-13, US-08), table créée par db/migration/V5__compte.sql.
// Créé au statut ACTIF, à 0 €, sans versement.
@Entity
@Table(name = "compte")
public class Compte {

	public static final String ACTIF = "ACTIF";
	public static final String CLOTURE = "CLOTURE";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false, unique = true, updatable = false)
	private Long dossierId;

	@Column(name = "customer_id", nullable = false, unique = true, updatable = false)
	private UUID customerId;

	@Column(nullable = false, unique = true, length = 27, updatable = false)
	private String iban;

	@Column(name = "numero_compte", nullable = false, unique = true, length = 11, updatable = false)
	private String numeroCompte;

	@Column(name = "solde_centimes", nullable = false)
	private long soldeCentimes;

	@Column(name = "solde_mis_a_jour_le", nullable = false)
	private OffsetDateTime soldeMisAJourLe;

	@Column(nullable = false, length = 20)
	private String statut;

	@Column(name = "ouvert_le", nullable = false, updatable = false)
	private OffsetDateTime ouvertLe;

	@Column(name = "identifiant_connexion_id", nullable = false, unique = true, updatable = false)
	private Long identifiantConnexionId;

	protected Compte() {
	}

	public Compte(Long dossierId, UUID customerId, Iban iban, Long identifiantConnexionId, OffsetDateTime ouvertLe) {
		this.dossierId = dossierId;
		this.customerId = customerId;
		this.iban = iban.valeur();
		this.numeroCompte = iban.numeroCompte();
		this.soldeCentimes = 0;
		this.soldeMisAJourLe = ouvertLe;
		this.statut = ACTIF;
		this.ouvertLe = ouvertLe;
		this.identifiantConnexionId = identifiantConnexionId;
	}

	public Long getId() {
		return id;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getIban() {
		return iban;
	}

	public String getNumeroCompte() {
		return numeroCompte;
	}

	public long getSoldeCentimes() {
		return soldeCentimes;
	}

	public OffsetDateTime getSoldeMisAJourLe() {
		return soldeMisAJourLe;
	}

	public boolean isCloture() {
		return CLOTURE.equals(statut);
	}

	/** CU-19 : 0 si le compte est clôturé ; vide si le solde est négatif, jamais affiché. */
	public Optional<Long> getSoldeAffichable() {
		if (isCloture()) {
			return Optional.of(0L);
		}
		return soldeCentimes < 0 ? Optional.empty() : Optional.of(soldeCentimes);
	}

	/** CU-19 : compte ouvert, à 0, dont le solde n'a jamais changé depuis l'ouverture. */
	public boolean isSansVersement() {
		return isActif() && soldeCentimes == 0 && soldeMisAJourLe.isEqual(ouvertLe);
	}

	public boolean isActif() {
		return ACTIF.equals(statut);
	}

	/**
	 * Versement : augmente le solde et sa date de mise à jour. Refusé si le compte n'est pas actif, si le montant
	 * n'est pas strictement positif ou si le solde dépasserait la capacité d'un nombre entier.
	 */
	public void crediter(long montantCentimes, OffsetDateTime le) {
		if (!isActif()) {
			throw new CompteNonActifException();
		}
		if (montantCentimes <= 0) {
			throw new MontantInvalideException();
		}
		try {
			this.soldeCentimes = Math.addExact(soldeCentimes, montantCentimes);
		} catch (ArithmeticException depassement) {
			throw new MontantInvalideException();
		}
		this.soldeMisAJourLe = le;
	}

	/**
	 * CU-144 : virement sortant, diminue le solde et sa date de mise à jour. Refusé si le compte n'est pas actif
	 * (clôturé), si le montant n'est pas strictement positif ou s'il dépasse le solde : le solde ne devient jamais
	 * négatif.
	 */
	public void debiter(long montantCentimes, OffsetDateTime le) {
		if (!isActif()) {
			throw new CompteNonActifException();
		}
		if (montantCentimes <= 0) {
			throw new MontantInvalideException();
		}
		if (montantCentimes > soldeCentimes) {
			throw new SoldeInsuffisantException();
		}
		this.soldeCentimes -= montantCentimes;
		this.soldeMisAJourLe = le;
	}

	public String getStatut() {
		return statut;
	}

	public OffsetDateTime getOuvertLe() {
		return ouvertLe;
	}

	public Long getIdentifiantConnexionId() {
		return identifiantConnexionId;
	}

	/** CU-11 : IBAN complet à communiquer à l'émetteur, affiché seulement dans la rubrique Versements. */
	public String getIbanParQuatre() {
		return Iban.de(numeroCompte).parQuatre();
	}

	/** IBAN tel qu'affiché à la cliente (quatre derniers chiffres). */
	public String getIbanMasque() {
		return Iban.de(numeroCompte).masque();
	}

}
