package com.example.compteunique.evenements;

import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-382 : message en échec après 3 essais, déposé dans le topic d'erreurs ; le back-office est alerté.
@Entity
@Table(schema = "evenements", name = "en_erreur")
public class MessageEnErreur {

	static final int MOTIF_LONGUEUR_MAX = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String topic;

	private String cle;

	private String groupe;

	private String motif;

	private int essais;

	private OffsetDateTime enErreurLe;

	protected MessageEnErreur() {
	}

	MessageEnErreur(String topic, String cle, String groupe, String motif, int essais, OffsetDateTime enErreurLe) {
		this.topic = topic;
		this.cle = cle == null || cle.length() <= 100 ? cle : cle.substring(0, 100);
		this.groupe = groupe;
		String texte = motif == null ? "erreur inconnue" : motif;
		this.motif = texte.length() > MOTIF_LONGUEUR_MAX ? texte.substring(0, MOTIF_LONGUEUR_MAX) : texte;
		this.essais = essais;
		this.enErreurLe = enErreurLe;
	}

	public String getTopic() {
		return topic;
	}

	public String getCle() {
		return cle;
	}

	public String getGroupe() {
		return groupe;
	}

	public String getMotif() {
		return motif;
	}

	public int getEssais() {
		return essais;
	}

	public OffsetDateTime getEnErreurLe() {
		return enErreurLe;
	}

}
