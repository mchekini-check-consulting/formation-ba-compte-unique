package com.example.compteunique.connexion;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Session ouverte par une connexion réussie : seule l'empreinte du jeton est enregistrée.
@Entity
@Table(name = "session_client")
public class SessionClient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "identifiant_connexion_id", nullable = false, updatable = false)
	private Long identifiantConnexionId;

	@Column(name = "jeton_hache", nullable = false, updatable = false, unique = true, length = 64)
	private String jetonHache;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private OffsetDateTime creeLe;

	protected SessionClient() {
	}

	public SessionClient(Long identifiantConnexionId, String jetonHache, OffsetDateTime creeLe) {
		this.identifiantConnexionId = identifiantConnexionId;
		this.jetonHache = jetonHache;
		this.creeLe = creeLe;
	}

	public Long getId() {
		return id;
	}

	public Long getIdentifiantConnexionId() {
		return identifiantConnexionId;
	}

	public String getJetonHache() {
		return jetonHache;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

}
