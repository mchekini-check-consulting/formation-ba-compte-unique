package com.example.compteunique.gestion;

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

// CU-314 / CU-338 : mode et profil de gestion en vigueur d'un contrat PER ou assurance vie.
@Entity
@Table(schema = "gestion", name = "contrat_gestion")
public class ContratGestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String numeroContrat;

	private UUID customerId;

	@Enumerated(EnumType.STRING)
	private Produit produit;

	@Enumerated(EnumType.STRING)
	private ModeGestion mode;

	@Enumerated(EnumType.STRING)
	private ProfilGestion profil;

	private Integer anneeDepartRetraite;

	private OffsetDateTime creeLe;

	private OffsetDateTime changeLe;

	protected ContratGestion() {
	}

	ContratGestion(String numeroContrat, UUID customerId, Produit produit, Integer anneeDepartRetraite,
			ProfilGestion profil, OffsetDateTime le) {
		this.numeroContrat = numeroContrat;
		this.customerId = customerId;
		this.produit = produit;
		this.anneeDepartRetraite = anneeDepartRetraite;
		this.mode = ModeGestion.PILOTEE;
		this.profil = profil;
		this.creeLe = le;
		this.changeLe = le;
	}

	void changer(ModeGestion nouveauMode, ProfilGestion nouveauProfil, Integer anneeRetraite, OffsetDateTime le) {
		this.mode = nouveauMode;
		this.profil = nouveauProfil;
		if (anneeRetraite != null) {
			this.anneeDepartRetraite = anneeRetraite;
		}
		this.changeLe = le;
	}

	public String getNumeroContrat() {
		return numeroContrat;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public Produit getProduit() {
		return produit;
	}

	public ModeGestion getMode() {
		return mode;
	}

	public ProfilGestion getProfil() {
		return profil;
	}

	public Integer getAnneeDepartRetraite() {
		return anneeDepartRetraite;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

	public OffsetDateTime getChangeLe() {
		return changeLe;
	}

}
