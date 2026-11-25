package com.example.compteunique.alertes;

import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-382 : une alerte envoyée à l'équipe (db/migration/V32__journal_alertes.sql), jamais modifiée.
@Entity
@Table(schema = "alertes", name = "journal")
public class Alerte {

	static final int ORIGINE_LONGUEUR_MAX = 50;
	static final int MESSAGE_LONGUEUR_MAX = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private OffsetDateTime dateMetier;

	private String origine;

	private String message;

	private boolean emailEnvoye;

	protected Alerte() {
	}

	Alerte(OffsetDateTime dateMetier, String origine, String message, boolean emailEnvoye) {
		this.dateMetier = dateMetier;
		this.origine = raccourcir(origine, ORIGINE_LONGUEUR_MAX);
		this.message = raccourcir(message, MESSAGE_LONGUEUR_MAX);
		this.emailEnvoye = emailEnvoye;
	}

	private static String raccourcir(String texte, int longueurMax) {
		return texte.length() <= longueurMax ? texte : texte.substring(0, longueurMax);
	}

	public OffsetDateTime getDateMetier() {
		return dateMetier;
	}

	public String getOrigine() {
		return origine;
	}

	public String getMessage() {
		return message;
	}

	public boolean isEmailEnvoye() {
		return emailEnvoye;
	}

}
