package com.example.compteunique.evenements;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-382 : un événement de la boîte d'envoi, enregistré avec l'opération et publié ensuite par le relais.
@Entity
@Table(schema = "evenements", name = "a_distribuer")
public class EvenementADistribuer {

	static final int ERREUR_LONGUEUR_MAX = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID evenementId;

	private String type;

	private String topic;

	private String cle;

	private LocalDate dateMetier;

	private int version;

	private String contenu;

	private OffsetDateTime creeLe;

	private OffsetDateTime publieLe;

	private int essais;

	private String derniereErreur;

	protected EvenementADistribuer() {
	}

	EvenementADistribuer(UUID evenementId, String type, String topic, String cle, LocalDate dateMetier, int version,
			String contenu, OffsetDateTime creeLe) {
		this.evenementId = evenementId;
		this.type = type;
		this.topic = topic;
		this.cle = cle;
		this.dateMetier = dateMetier;
		this.version = version;
		this.contenu = contenu;
		this.creeLe = creeLe;
	}

	void publie(OffsetDateTime le) {
		this.publieLe = le;
		this.derniereErreur = null;
	}

	void echec(String motif) {
		this.essais++;
		String texte = motif == null ? "erreur inconnue" : motif;
		this.derniereErreur = texte.length() > ERREUR_LONGUEUR_MAX ? texte.substring(0, ERREUR_LONGUEUR_MAX) : texte;
	}

	public UUID getEvenementId() {
		return evenementId;
	}

	public String getType() {
		return type;
	}

	public String getTopic() {
		return topic;
	}

	public String getCle() {
		return cle;
	}

	public LocalDate getDateMetier() {
		return dateMetier;
	}

	public int getVersion() {
		return version;
	}

	public String getContenu() {
		return contenu;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

	public OffsetDateTime getPublieLe() {
		return publieLe;
	}

	public int getEssais() {
		return essais;
	}

	public String getDerniereErreur() {
		return derniereErreur;
	}

}
