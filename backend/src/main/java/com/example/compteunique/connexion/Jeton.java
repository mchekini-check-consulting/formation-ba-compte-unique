package com.example.compteunique.connexion;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Jeton d'activation ou de réinitialisation : seul son empreinte est enregistrée.
@Entity
@Table(name = "jeton")
public class Jeton {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "identifiant_connexion_id", nullable = false, updatable = false)
	private Long identifiantConnexionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TypeJeton type;

	@Column(name = "jeton_hache", nullable = false, updatable = false, unique = true, length = 64)
	private String jetonHache;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private OffsetDateTime creeLe;

	@Column(name = "expire_le", nullable = false, updatable = false)
	private OffsetDateTime expireLe;

	@Column(name = "utilise_le")
	private OffsetDateTime utiliseLe;

	@Column(name = "invalide_le")
	private OffsetDateTime invalideLe;

	protected Jeton() {
	}

	public Jeton(Long identifiantConnexionId, TypeJeton type, String jetonHache, OffsetDateTime creeLe,
			OffsetDateTime expireLe) {
		this.identifiantConnexionId = identifiantConnexionId;
		this.type = type;
		this.jetonHache = jetonHache;
		this.creeLe = creeLe;
		this.expireLe = expireLe;
	}

	public boolean estUtilisable(OffsetDateTime date) {
		return utiliseLe == null && invalideLe == null && date.isBefore(expireLe);
	}

	public void marquerUtilise(OffsetDateTime date) {
		utiliseLe = date;
	}

	public Long getId() {
		return id;
	}

	public Long getIdentifiantConnexionId() {
		return identifiantConnexionId;
	}

	public TypeJeton getType() {
		return type;
	}

	public String getJetonHache() {
		return jetonHache;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

	public OffsetDateTime getExpireLe() {
		return expireLe;
	}

	public OffsetDateTime getUtiliseLe() {
		return utiliseLe;
	}

	public OffsetDateTime getInvalideLe() {
		return invalideLe;
	}

}
