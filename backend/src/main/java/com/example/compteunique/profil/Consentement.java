package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-10 : un choix de consentement (type, choix, version du texte, date et heure), en ajout seul.
@Entity
@Table(schema = "profil_client", name = "consentement")
public class Consentement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID customerId;

	private String type;

	private boolean choix;

	private String versionTexte;

	private OffsetDateTime choisiLe;

	protected Consentement() {
	}

	Consentement(UUID customerId, String type, boolean choix, String versionTexte, OffsetDateTime choisiLe) {
		this.customerId = customerId;
		this.type = type;
		this.choix = choix;
		this.versionTexte = versionTexte;
		this.choisiLe = choisiLe;
	}

	public String getType() {
		return type;
	}

	public boolean isChoix() {
		return choix;
	}

}
