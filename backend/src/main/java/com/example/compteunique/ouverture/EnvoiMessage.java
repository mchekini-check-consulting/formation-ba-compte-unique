package com.example.compteunique.ouverture;

import java.time.Duration;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.messagerie.Canal;

// Envoi de l'identifiant (EMAIL) ou du lien d'activation (SMS) d'un dossier (CU-13), table
// créée par db/migration/V8__ouverture_compte.sql. Pas de contenu : canal, date, résultat, tentatives.
@Entity
@Table(name = "envoi_message")
public class EnvoiMessage {

	/** Premier envoi + 3 relances automatiques. */
	static final int TENTATIVES_MAX = 4;
	static final Duration DELAI_RELANCE = Duration.ofMinutes(1);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false, updatable = false)
	private Long dossierId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 5, updatable = false)
	private Canal canal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ResultatEnvoi resultat;

	@Column(nullable = false)
	private int tentatives;

	@Column(name = "dernier_essai_le", nullable = false)
	private OffsetDateTime dernierEssaiLe;

	@Column(name = "prochaine_tentative_le")
	private OffsetDateTime prochaineTentativeLe;

	protected EnvoiMessage() {
	}

	public EnvoiMessage(Long dossierId, Canal canal) {
		this.dossierId = dossierId;
		this.canal = canal;
	}

	void tentative(boolean remis, OffsetDateTime maintenant) {
		tentatives++;
		dernierEssaiLe = maintenant;
		if (remis) {
			resultat = ResultatEnvoi.ENVOYE;
			prochaineTentativeLe = null;
		} else if (tentatives >= TENTATIVES_MAX) {
			resultat = ResultatEnvoi.ECHEC;
			prochaineTentativeLe = null;
		} else {
			resultat = ResultatEnvoi.EN_ECHEC;
			prochaineTentativeLe = maintenant.plus(DELAI_RELANCE);
		}
	}

	/** Renvoi demandé par la cliente : les relances automatiques repartent de zéro. */
	void reinitialiser() {
		tentatives = 0;
	}

	public Long getId() {
		return id;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public Canal getCanal() {
		return canal;
	}

	public ResultatEnvoi getResultat() {
		return resultat;
	}

	public int getTentatives() {
		return tentatives;
	}

	public OffsetDateTime getDernierEssaiLe() {
		return dernierEssaiLe;
	}

	public OffsetDateTime getProchaineTentativeLe() {
		return prochaineTentativeLe;
	}

}
