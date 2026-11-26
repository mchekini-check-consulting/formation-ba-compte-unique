package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-314 / CU-338 : un calcul des frais du mandat, enregistré sans prélèvement. La cliente paie le taux tout compris ; si les supports coûtent
// plus, l'écart est à la charge de l'établissement.
@Entity
@Table(schema = "gestion", name = "frais_mandat")
public class FraisMandat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String numeroContrat;

	private OffsetDateTime calculeLe;

	private BigDecimal encours;

	private BigDecimal tauxToutComprisPct;

	private BigDecimal fraisCliente;

	private BigDecimal coutReelSupports;

	private BigDecimal ecartEtablissement;

	protected FraisMandat() {
	}

	FraisMandat(String numeroContrat, OffsetDateTime calculeLe, BigDecimal encours, BigDecimal tauxToutComprisPct,
			BigDecimal fraisCliente, BigDecimal coutReelSupports, BigDecimal ecartEtablissement) {
		this.numeroContrat = numeroContrat;
		this.calculeLe = calculeLe;
		this.encours = encours;
		this.tauxToutComprisPct = tauxToutComprisPct;
		this.fraisCliente = fraisCliente;
		this.coutReelSupports = coutReelSupports;
		this.ecartEtablissement = ecartEtablissement;
	}

	public String getNumeroContrat() {
		return numeroContrat;
	}

	public OffsetDateTime getCalculeLe() {
		return calculeLe;
	}

	public BigDecimal getEncours() {
		return encours;
	}

	public BigDecimal getTauxToutComprisPct() {
		return tauxToutComprisPct;
	}

	public BigDecimal getFraisCliente() {
		return fraisCliente;
	}

	public BigDecimal getCoutReelSupports() {
		return coutReelSupports;
	}

	public BigDecimal getEcartEtablissement() {
		return ecartEtablissement;
	}

}
