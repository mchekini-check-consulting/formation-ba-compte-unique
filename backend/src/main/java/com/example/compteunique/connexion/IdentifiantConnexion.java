package com.example.compteunique.connexion;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// Identifiant de connexion d'un client (table créée par db/migration/V3__connexion.sql)
@Entity
@Table(name = "identifiant_connexion")
public class IdentifiantConnexion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 32)
	private String identifiant;

	@Column(name = "mot_de_passe_hache")
	private String motDePasseHache;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatutIdentifiant statut;

	@Column(name = "mot_de_passe_defini_le")
	private OffsetDateTime motDePasseDefiniLe;

	@Column(name = "compteur_erreurs", nullable = false)
	private int compteurErreurs;

	@Column(name = "bloque_jusqu_au")
	private OffsetDateTime bloqueJusquAu;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private OffsetDateTime creeLe;

	protected IdentifiantConnexion() {
	}

	public IdentifiantConnexion(String identifiant) {
		this.identifiant = identifiant;
		this.statut = StatutIdentifiant.EN_ATTENTE_ACTIVATION;
	}

	@PrePersist
	void avantCreation() {
		creeLe = OffsetDateTime.now();
	}

	public void definirMotDePasse(String hache, OffsetDateTime date) {
		motDePasseHache = hache;
		motDePasseDefiniLe = date;
		statut = StatutIdentifiant.ACTIF;
	}

	public void connexionReussie() {
		compteurErreurs = 0;
	}

	public Long getId() {
		return id;
	}

	public String getIdentifiant() {
		return identifiant;
	}

	public String getMotDePasseHache() {
		return motDePasseHache;
	}

	public StatutIdentifiant getStatut() {
		return statut;
	}

	public OffsetDateTime getMotDePasseDefiniLe() {
		return motDePasseDefiniLe;
	}

	public int getCompteurErreurs() {
		return compteurErreurs;
	}

	public OffsetDateTime getBloqueJusquAu() {
		return bloqueJusquAu;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

}
