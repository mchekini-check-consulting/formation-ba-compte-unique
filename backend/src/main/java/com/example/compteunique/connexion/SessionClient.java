package com.example.compteunique.connexion;

import java.time.Duration;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Session ouverte par une connexion réussie : seule l'empreinte du jeton est enregistrée.
// CU-17 : fermée par la déconnexion ou après 10 minutes sans action de la cliente.
@Entity
@Table(name = "session_client")
public class SessionClient {

	/** CU-17 : délai sans activité au bout duquel la session est fermée. */
	public static final Duration DELAI_INACTIVITE = Duration.ofMinutes(10);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "identifiant_connexion_id", nullable = false, updatable = false)
	private Long identifiantConnexionId;

	@Column(name = "jeton_hache", nullable = false, updatable = false, unique = true, length = 64)
	private String jetonHache;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private OffsetDateTime creeLe;

	@Column(name = "derniere_activite_le", nullable = false)
	private OffsetDateTime derniereActiviteLe;

	@Column(name = "revoquee_le")
	private OffsetDateTime revoqueeLe;

	protected SessionClient() {
	}

	public SessionClient(Long identifiantConnexionId, String jetonHache, OffsetDateTime creeLe) {
		this.identifiantConnexionId = identifiantConnexionId;
		this.jetonHache = jetonHache;
		this.creeLe = creeLe;
		this.derniereActiviteLe = creeLe;
	}

	/** Moment où la session sera fermée faute d'activité. */
	public OffsetDateTime expireLe() {
		return derniereActiviteLe.plus(DELAI_INACTIVITE);
	}

	/** Active : ni révoquée, ni restée 10 minutes sans activité (à 9 min 59 elle l'est encore). */
	public boolean estActive(OffsetDateTime maintenant) {
		return revoqueeLe == null && maintenant.isBefore(expireLe());
	}

	public boolean estRevoquee() {
		return revoqueeLe != null;
	}

	/** Une action de la cliente : le délai de 10 minutes repart de zéro. */
	public void activite(OffsetDateTime maintenant) {
		derniereActiviteLe = maintenant;
	}

	/** Déconnexion ou expiration : la session ne sert plus jamais. */
	public void revoquer(OffsetDateTime maintenant) {
		if (revoqueeLe == null) {
			revoqueeLe = maintenant;
		}
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

	public OffsetDateTime getDerniereActiviteLe() {
		return derniereActiviteLe;
	}

	public OffsetDateTime getRevoqueeLe() {
		return revoqueeLe;
	}

}
