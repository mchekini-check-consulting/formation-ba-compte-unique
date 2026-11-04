package com.example.compteunique.messagerie;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Dernier message capturé pour un canal (CU-87), table créée par db/migration/V6__messages_test.sql.
@Entity
@Table(name = "message_test")
public class MessageTest {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(length = 5)
	private Canal canal;

	@Column(nullable = false, length = 254)
	private String destinataire;

	@Column(length = 200)
	private String objet;

	@Column(nullable = false, length = 2000)
	private String texte;

	@Column(name = "envoye_le", nullable = false)
	private OffsetDateTime envoyeLe;

	protected MessageTest() {
	}

	public MessageTest(Canal canal, String destinataire, String objet, String texte, OffsetDateTime envoyeLe) {
		this.canal = canal;
		this.destinataire = destinataire;
		this.objet = objet;
		this.texte = texte;
		this.envoyeLe = envoyeLe;
	}

	public Canal getCanal() {
		return canal;
	}

	public String getDestinataire() {
		return destinataire;
	}

	public String getObjet() {
		return objet;
	}

	public String getTexte() {
		return texte;
	}

	public OffsetDateTime getEnvoyeLe() {
		return envoyeLe;
	}

}
