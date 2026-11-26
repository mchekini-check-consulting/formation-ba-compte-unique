package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// AV-07 (décision PO du 26/11/2026) : support non coté conservé au passage en gestion pilotée, dont le niveau de
// risque dépasse le maximum du profil de gestion choisi ; signalé au back-office, « à traiter ».
@Entity
@Table(schema = "gestion", name = "signalement_non_cote")
public class SignalementNonCote {

	static final String A_TRAITER = "A_TRAITER";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String numeroContrat;

	private String codeIsin;

	private BigDecimal montant;

	private short niveauRisque;

	@Enumerated(EnumType.STRING)
	private ProfilGestion profil;

	private OffsetDateTime signaleLe;

	private String statut;

	protected SignalementNonCote() {
	}

	SignalementNonCote(String numeroContrat, String codeIsin, BigDecimal montant, int niveauRisque,
			ProfilGestion profil, OffsetDateTime signaleLe) {
		this.numeroContrat = numeroContrat;
		this.codeIsin = codeIsin;
		this.montant = montant;
		this.niveauRisque = (short) niveauRisque;
		this.profil = profil;
		this.signaleLe = signaleLe;
		this.statut = A_TRAITER;
	}

	public String getNumeroContrat() {
		return numeroContrat;
	}

	public String getCodeIsin() {
		return codeIsin;
	}

	public BigDecimal getMontant() {
		return montant;
	}

	public int getNiveauRisque() {
		return niveauRisque;
	}

	public ProfilGestion getProfil() {
		return profil;
	}

	public OffsetDateTime getSignaleLe() {
		return signaleLe;
	}

	public String getStatut() {
		return statut;
	}

}
