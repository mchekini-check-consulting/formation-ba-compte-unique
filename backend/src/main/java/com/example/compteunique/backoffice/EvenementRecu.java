package com.example.compteunique.backoffice;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-382 : un événement reçu de Kafka par le journal back-office (une ligne par événement, jamais en double).
@Entity
@Table(schema = "back_office", name = "evenement_recu")
public class EvenementRecu {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID evenementId;

	private String type;

	private String topic;

	private String cle;

	private LocalDate dateMetier;

	private String contenu;

	private OffsetDateTime recuLe;

	protected EvenementRecu() {
	}

	EvenementRecu(UUID evenementId, String type, String topic, String cle, LocalDate dateMetier, String contenu,
			OffsetDateTime recuLe) {
		this.evenementId = evenementId;
		this.type = type;
		this.topic = topic;
		this.cle = cle;
		this.dateMetier = dateMetier;
		this.contenu = contenu;
		this.recuLe = recuLe;
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

	public String getContenu() {
		return contenu;
	}

	public OffsetDateTime getRecuLe() {
		return recuLe;
	}

}
